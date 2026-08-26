package ar.com.hexium.hcop.admin.application.service;

import ar.com.hexium.hcop.admin.application.port.in.AdminManagementUseCase;
import ar.com.hexium.hcop.admin.application.port.out.AdminStore;
import ar.com.hexium.hcop.admin.application.port.out.AdminStore.ExistingRole;
import ar.com.hexium.hcop.admin.application.port.out.AdminStore.ExistingUser;
import ar.com.hexium.hcop.admin.application.port.out.AdminStore.NewRole;
import ar.com.hexium.hcop.admin.application.port.out.AdminStore.NewUser;
import ar.com.hexium.hcop.admin.application.port.out.RoleKeyConflictException;
import ar.com.hexium.hcop.admin.application.port.out.UsernameOrEmailConflictException;
import ar.com.hexium.hcop.admin.domain.AdminRole;
import ar.com.hexium.hcop.admin.domain.AdminUser;
import ar.com.hexium.hcop.admin.domain.Permission;
import ar.com.hexium.hcop.admin.domain.SecuritySettings;
import ar.com.hexium.hcop.auth.PasswordService;
import ar.com.hexium.hcop.auth.RefreshTokenRepository;
import ar.com.hexium.hcop.auth.SessionStateRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Casos de uso puros de administración. La revocación inmediata de sesiones JWT (F2.7) vive acá
 * porque es una regla de negocio ligada a estos comandos, no un detalle de persistencia — usa
 * {@code auth} directo (módulo permanentemente exento, mismo criterio que {@code AuthContext} en
 * la capa web).
 */
public final class AdminApplicationService implements AdminManagementUseCase {
  private final AdminStore store;
  private final PasswordService passwords;
  private final Clock clock;
  private final SessionStateRepository sessions;
  private final RefreshTokenRepository refreshTokens;

  public AdminApplicationService(
      AdminStore store,
      PasswordService passwords,
      Clock clock,
      SessionStateRepository sessions,
      RefreshTokenRepository refreshTokens) {
    this.store = store;
    this.passwords = passwords;
    this.clock = clock;
    this.sessions = sessions;
    this.refreshTokens = refreshTokens;
  }

  @Override
  public List<AdminUser> users() {
    return store.users();
  }

  @Override
  public List<AdminUser> usersWithPermission(String permission) {
    return permission == null || permission.isBlank()
        ? users().stream().filter(AdminUser::active).toList()
        : store.usersWithPermission(permission);
  }

  @Override
  public AdminUser createUser(CreateUserCommand command) {
    String username = normalizeUsername(command.username());
    String email = normalizeEmail(command.email());
    String displayName = required(command.displayName(), "El nombre es obligatorio.");
    requirePassword(command.password(), true);
    List<Long> roleIds = roleIds(command.roleIds());
    if (store.usernameOrEmailExists(username, email, null)) {
      throw usernameOrEmailConflict();
    }
    if (!store.rolesExist(roleIds)) {
      throw new AdminFailure(AdminFailure.Type.INVALID, "Uno o más roles no son válidos.");
    }
    try {
      long id = store.insertUser(new NewUser(
          username, email, displayName, command.specialty(), command.licenseNumber(),
          command.active(), passwords.encode(command.password()), clock.instant()));
      store.replaceUserRoles(id, roleIds, command.actorId().value());
      return store.user(id).orElseThrow();
    } catch (UsernameOrEmailConflictException conflict) {
      throw usernameOrEmailConflict();
    }
  }

  @Override
  public AdminUser updateUser(UpdateUserCommand command) {
    AdminUser current = store.user(command.id())
        .orElseThrow(() -> notFound("Usuario no encontrado."));
    String username = normalizeUsername(command.username());
    String email = normalizeEmail(command.email());
    String displayName = required(command.displayName(), "El nombre es obligatorio.");
    requirePassword(command.password(), false);
    List<Long> roleIds = roleIds(command.roleIds());
    if (store.usernameOrEmailExists(username, email, command.id())) {
      throw usernameOrEmailConflict();
    }
    if (!store.rolesExist(roleIds)) {
      throw new AdminFailure(AdminFailure.Type.INVALID, "Uno o más roles no son válidos.");
    }
    Set<Long> previousRoleIds = store.roleIdsForUser(command.id());
    String passwordHash = command.password().isBlank() ? null : passwords.encode(command.password());
    Instant now = clock.instant();
    store.updateUser(new ExistingUser(
        command.id(), username, email, displayName, command.specialty(), command.licenseNumber(),
        command.active(), passwordHash, now));
    store.replaceUserRoles(command.id(), roleIds, command.actorId().value());
    if (!command.active() || passwordHash != null) store.revokeSessions(command.id());
    // F2.7: el access token JWT lleva roles/permisos horneados — sin esto, deshabilitar un
    // usuario, cambiarle la contraseña o reasignarle roles desde acá solo surtiría efecto cuando
    // el token vence (hasta HCOP_JWT_ACCESS_MINUTES después). El refresh siguiente relee todo de
    // la base igual, así que revocar acá es lo único que falta para que sea inmediato.
    boolean rolesChanged = !previousRoleIds.equals(Set.copyOf(roleIds));
    if (!command.active() || passwordHash != null || rolesChanged) {
      sessions.revokeAllForUser(command.id(), now);
      refreshTokens.revokeAllForUser(command.id());
    }
    return store.user(command.id()).orElse(current);
  }

  @Override
  public List<AdminRole> roles() {
    return store.roles();
  }

  @Override
  public List<Permission> permissionCatalog() {
    return store.permissions();
  }

  @Override
  public AdminRole createRole(CreateRoleCommand command) {
    String key = required(command.key(), "La clave del rol no es válida.").toLowerCase(Locale.ROOT);
    if (!key.matches("[a-z0-9._-]{3,96}")) {
      throw new AdminFailure(AdminFailure.Type.INVALID, "La clave del rol no es válida.");
    }
    String name = required(command.name(), "El nombre del rol es obligatorio.");
    List<String> permissions = permissions(command.permissions());
    try {
      long id = store.insertRole(new NewRole(
          key, name, command.description(), command.active(), command.actorId().value(),
          clock.instant()));
      store.replaceRolePermissions(id, permissions);
      return store.role(id).orElseThrow();
    } catch (RoleKeyConflictException conflict) {
      throw roleKeyConflict();
    }
  }

  @Override
  public AdminRole updateRole(UpdateRoleCommand command) {
    store.role(command.id()).orElseThrow(() -> notFound("Rol no encontrado."));
    String name = required(command.name(), "El nombre del rol es obligatorio.");
    List<String> permissions = permissions(command.permissions());
    Instant now = clock.instant();
    store.updateRole(new ExistingRole(
        command.id(), name, command.description(), command.active(), command.actorId().value(), now));
    store.replaceRolePermissions(command.id(), permissions);
    // F2.7: cambiar los permisos (o deshabilitar) un rol afecta a todos sus usuarios de una — sin
    // esto quedarían con permisos viejos horneados en el JWT hasta que vence.
    List<Long> affectedUserIds = store.userIdsForRole(command.id());
    sessions.revokeAllForUsers(affectedUserIds, now);
    refreshTokens.revokeAllForUsers(affectedUserIds);
    return store.role(command.id()).orElseThrow();
  }

  @Override
  public Optional<SecuritySettings> security() {
    return store.security();
  }

  @Override
  public Optional<SecuritySettings> updateSecurity(UpdateSecurityCommand command) {
    if (!command.loginRequired()) {
      throw new AdminFailure(
          AdminFailure.Type.INVALID,
          "Esta instalación exige inicio de sesión para conservar la trazabilidad clínica.");
    }
    int duration = command.sessionDurationMinutes();
    if (duration < 15 || duration > 525600) {
      throw new AdminFailure(AdminFailure.Type.INVALID, "La duración de sesión no es válida.");
    }
    return store.updateSecurity(duration, command.actorId().value());
  }

  private String normalizeUsername(String username) {
    String normalized = required(username, "El nombre de usuario no es válido.").toLowerCase(Locale.ROOT);
    if (!normalized.matches("[a-z0-9._-]{3,96}")) {
      throw new AdminFailure(AdminFailure.Type.INVALID, "El nombre de usuario no es válido.");
    }
    return normalized;
  }

  private String normalizeEmail(String email) {
    String normalized = required(email, "El correo no es válido.").toLowerCase(Locale.ROOT);
    if (!normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
      throw new AdminFailure(AdminFailure.Type.INVALID, "El correo no es válido.");
    }
    return normalized;
  }

  private void requirePassword(String password, boolean creating) {
    if ((creating || !password.isBlank()) && (password.length() < 8 || password.length() > 256)) {
      throw new AdminFailure(AdminFailure.Type.INVALID, "La clave debe tener entre 8 y 256 caracteres.");
    }
  }

  private List<Long> roleIds(List<String> rawRoleIds) {
    List<Long> parsed = new ArrayList<>();
    for (String rawId : rawRoleIds) {
      try {
        parsed.add(Long.valueOf(rawId));
      } catch (NumberFormatException invalid) {
        throw new AdminFailure(AdminFailure.Type.INVALID, "Rol inválido.");
      }
    }
    List<Long> roleIds = List.copyOf(new LinkedHashSet<>(parsed));
    if (roleIds.isEmpty()) throw new AdminFailure(AdminFailure.Type.INVALID, "Seleccione al menos un rol.");
    return roleIds;
  }

  private List<String> permissions(List<String> rawPermissions) {
    List<String> permissions = List.copyOf(new LinkedHashSet<>(
        rawPermissions.stream().filter(value -> value != null && !value.isBlank()).toList()));
    if (!store.permissionsExist(permissions)) {
      throw new AdminFailure(AdminFailure.Type.INVALID, "Uno o más permisos no son válidos.");
    }
    return permissions;
  }

  private String required(String value, String message) {
    String normalized = value == null ? "" : value.strip();
    if (normalized.isBlank()) throw new AdminFailure(AdminFailure.Type.INVALID, message);
    return normalized;
  }

  private AdminFailure notFound(String message) {
    return new AdminFailure(AdminFailure.Type.NOT_FOUND, message);
  }

  private AdminFailure usernameOrEmailConflict() {
    return new AdminFailure(AdminFailure.Type.CONFLICT, "El usuario o correo ya está registrado.");
  }

  private AdminFailure roleKeyConflict() {
    return new AdminFailure(AdminFailure.Type.CONFLICT, "Ya existe un rol con esa clave.");
  }
}
