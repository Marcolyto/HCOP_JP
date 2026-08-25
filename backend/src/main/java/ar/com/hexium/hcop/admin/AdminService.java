package ar.com.hexium.hcop.admin;

import ar.com.hexium.hcop.auth.PasswordService;
import ar.com.hexium.hcop.common.ApiException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class AdminService {
  private final AdminRepository repository;
  private final PasswordService passwords;
  private final Clock clock;

  public AdminService(AdminRepository repository, PasswordService passwords, Clock clock) {
    this.repository = repository;
    this.passwords = passwords;
    this.clock = clock;
  }

  public List<Map<String, Object>> users() {
    return repository.users();
  }

  public List<Map<String, Object>> usersWithPermission(String permission) {
    return permission == null || permission.isBlank()
        ? users().stream().filter(item -> Boolean.TRUE.equals(item.get("active"))).toList()
        : repository.usersWithPermission(permission);
  }

  @Transactional
  public Map<String, Object> createUser(JsonNode body, long actorId) {
    UserInput input = userInput(body, true);
    if (repository.usernameOrEmailExists(input.username(), input.email(), null)) {
      throw new ApiException(HttpStatus.CONFLICT, "El usuario o correo ya está registrado.");
    }
    if (!repository.rolesExist(input.roleIds())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Uno o más roles no son válidos.");
    }
    try {
      long id = repository.insertUser(
          input.username(), input.email(), input.displayName(), input.specialty(),
          input.licenseNumber(), input.active(), passwords.encode(input.password()), clock.instant());
      repository.replaceUserRoles(id, input.roleIds(), actorId);
      return repository.user(id).orElseThrow();
    } catch (DataIntegrityViolationException duplicate) {
      throw new ApiException(HttpStatus.CONFLICT, "El usuario o correo ya está registrado.");
    }
  }

  @Transactional
  public Map<String, Object> updateUser(long id, JsonNode body, long actorId) {
    Map<String, Object> current = repository.user(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuario no encontrado."));
    UserInput input = userInput(body, false);
    if (repository.usernameOrEmailExists(input.username(), input.email(), id)) {
      throw new ApiException(HttpStatus.CONFLICT, "El usuario o correo ya está registrado.");
    }
    if (!repository.rolesExist(input.roleIds())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Uno o más roles no son válidos.");
    }
    String passwordHash = input.password().isBlank() ? null : passwords.encode(input.password());
    repository.updateUser(id, input.username(), input.email(), input.displayName(), input.specialty(),
        input.licenseNumber(), input.active(), passwordHash, clock.instant());
    repository.replaceUserRoles(id, input.roleIds(), actorId);
    if (!input.active() || passwordHash != null) repository.revokeSessions(id);
    return repository.user(id).orElse(current);
  }

  public List<Map<String, Object>> roles() {
    return repository.roles();
  }

  public List<Map<String, Object>> permissionCatalog() {
    return repository.permissions();
  }

  @Transactional
  public Map<String, Object> createRole(JsonNode body, long actorId) {
    RoleInput input = roleInput(body, true);
    try {
      long id = repository.insertRole(
          input.key(), input.name(), input.description(), input.active(), actorId, clock.instant());
      repository.replaceRolePermissions(id, input.permissions());
      return repository.role(id).orElseThrow();
    } catch (DataIntegrityViolationException duplicate) {
      throw new ApiException(HttpStatus.CONFLICT, "Ya existe un rol con esa clave.");
    }
  }

  @Transactional
  public Map<String, Object> updateRole(long id, JsonNode body, long actorId) {
    repository.role(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Rol no encontrado."));
    RoleInput input = roleInput(body, false);
    repository.updateRole(id, input.name(), input.description(), input.active(), actorId, clock.instant());
    repository.replaceRolePermissions(id, input.permissions());
    return repository.role(id).orElseThrow();
  }

  public Map<String, Object> security() {
    return repository.security();
  }

  @Transactional
  public Map<String, Object> updateSecurity(JsonNode body, long actorId) {
    boolean loginRequired = body.path("loginRequired").asBoolean(true);
    if (!loginRequired) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "Esta instalación exige inicio de sesión para conservar la trazabilidad clínica.");
    }
    int duration = body.path("sessionDurationMinutes").asInt(43200);
    if (duration < 15 || duration > 525600) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "La duración de sesión no es válida.");
    }
    return repository.updateSecurity(true, null, duration, actorId);
  }

  private UserInput userInput(JsonNode body, boolean creating) {
    String username = text(body, "username").toLowerCase(Locale.ROOT);
    String email = text(body, "email").toLowerCase(Locale.ROOT);
    String displayName = text(body, "displayName", "name");
    String password = text(body, "password");
    if (!username.matches("[a-z0-9._-]{3,96}")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "El nombre de usuario no es válido.");
    }
    if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "El correo no es válido.");
    }
    if (displayName.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "El nombre es obligatorio.");
    if ((creating || !password.isBlank()) && (password.length() < 8 || password.length() > 256)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "La clave debe tener entre 8 y 256 caracteres.");
    }
    List<Long> parsedRoleIds = new ArrayList<>();
    body.path("roleIds").forEach(node -> {
      try {
        parsedRoleIds.add(Long.valueOf(node.asText()));
      } catch (NumberFormatException ignored) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "Rol inválido.");
      }
    });
    List<Long> roleIds = new ArrayList<>(new LinkedHashSet<>(parsedRoleIds));
    if (roleIds.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Seleccione al menos un rol.");
    return new UserInput(
        username, email, displayName, text(body, "specialty"),
        text(body, "licenseNumber"), body.path("active").asBoolean(true), password, roleIds);
  }

  private RoleInput roleInput(JsonNode body, boolean creating) {
    String key = text(body, "key").toLowerCase(Locale.ROOT);
    String name = text(body, "name", "displayName");
    if (creating && !key.matches("[a-z0-9._-]{3,96}")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "La clave del rol no es válida.");
    }
    if (name.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "El nombre del rol es obligatorio.");
    List<String> parsedPermissions = new ArrayList<>();
    body.path("permissions").forEach(node -> parsedPermissions.add(node.asText("")));
    List<String> permissions = new ArrayList<>(new LinkedHashSet<>(
        parsedPermissions.stream().filter(value -> !value.isBlank()).toList()));
    if (!repository.permissionsExist(permissions)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Uno o más permisos no son válidos.");
    }
    return new RoleInput(key, name, text(body, "description"), body.path("active").asBoolean(true), permissions);
  }

  private String text(JsonNode body, String... keys) {
    for (String key : keys) {
      String value = body.path(key).asText("").trim();
      if (!value.isBlank()) return value;
    }
    return "";
  }

  private record UserInput(
      String username, String email, String displayName, String specialty, String licenseNumber,
      boolean active, String password, List<Long> roleIds) {
  }

  private record RoleInput(
      String key, String name, String description, boolean active, List<String> permissions) {
  }
}
