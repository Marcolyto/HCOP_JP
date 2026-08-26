package ar.com.hexium.hcop.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.admin.application.port.in.AdminManagementUseCase.CreateRoleCommand;
import ar.com.hexium.hcop.admin.application.port.in.AdminManagementUseCase.CreateUserCommand;
import ar.com.hexium.hcop.admin.application.port.in.AdminManagementUseCase.UpdateRoleCommand;
import ar.com.hexium.hcop.admin.application.port.in.AdminManagementUseCase.UpdateSecurityCommand;
import ar.com.hexium.hcop.admin.application.port.in.AdminManagementUseCase.UpdateUserCommand;
import ar.com.hexium.hcop.admin.application.port.out.AdminStore;
import ar.com.hexium.hcop.admin.application.port.out.RoleKeyConflictException;
import ar.com.hexium.hcop.admin.application.port.out.UsernameOrEmailConflictException;
import ar.com.hexium.hcop.admin.domain.AdminRole;
import ar.com.hexium.hcop.admin.domain.AdminUser;
import ar.com.hexium.hcop.auth.PasswordService;
import ar.com.hexium.hcop.auth.RefreshTokenRepository;
import ar.com.hexium.hcop.auth.SessionStateRepository;
import ar.com.hexium.hcop.sharedkernel.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminApplicationServiceTest {
  private final AdminStore store = mock(AdminStore.class);
  private final PasswordService passwords = mock(PasswordService.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC);
  private final SessionStateRepository sessions = mock(SessionStateRepository.class);
  private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
  private AdminApplicationService service;

  @BeforeEach
  void setUp() {
    service = new AdminApplicationService(store, passwords, clock, sessions, refreshTokens);
    when(store.user(anyLong())).thenReturn(Optional.of(user(7, true)));
    when(store.usernameOrEmailExists(any(), any(), any())).thenReturn(false);
    when(store.rolesExist(any())).thenReturn(true);
    when(passwords.encode(any())).thenReturn("hashed");
  }

  @Test
  void usersWithPermissionSinCapacidadFiltraActivos() {
    when(store.users()).thenReturn(List.of(user(1, true), user(2, false)));

    List<AdminUser> result = service.usersWithPermission("");

    assertThat(result).extracting(AdminUser::id).containsExactly(1L);
  }

  @Test
  void usersWithPermissionConCapacidadDelegaAlStore() {
    when(store.usersWithPermission("section.tools.use")).thenReturn(List.of(user(3, true)));

    List<AdminUser> result = service.usersWithPermission("section.tools.use");

    assertThat(result).extracting(AdminUser::id).containsExactly(3L);
  }

  @Test
  void createUserValidaYPersiste() {
    when(store.insertUser(any())).thenReturn(9L);
    when(store.user(9L)).thenReturn(Optional.of(user(9, true)));

    AdminUser created = service.createUser(userCommand("enfermeria", "una-clave-larga", List.of("1")));

    assertThat(created.id()).isEqualTo(9L);
    verify(store).replaceUserRoles(9L, List.of(1L), 1L);
  }

  @Test
  void createUserRechazaUsuarioOCorreoDuplicado() {
    when(store.usernameOrEmailExists(any(), any(), any())).thenReturn(true);

    assertThatThrownBy(() -> service.createUser(userCommand("enfermeria", "una-clave-larga", List.of("1"))))
        .isInstanceOf(AdminFailure.class)
        .extracting(failure -> ((AdminFailure) failure).type())
        .isEqualTo(AdminFailure.Type.CONFLICT);
  }

  @Test
  void createUserPropagaConflictoDeCarrera() {
    when(store.insertUser(any())).thenThrow(new UsernameOrEmailConflictException());

    assertThatThrownBy(() -> service.createUser(userCommand("enfermeria", "una-clave-larga", List.of("1"))))
        .isInstanceOf(AdminFailure.class)
        .extracting(failure -> ((AdminFailure) failure).type())
        .isEqualTo(AdminFailure.Type.CONFLICT);
  }

  @Test
  void createUserRechazaRolInexistente() {
    when(store.rolesExist(any())).thenReturn(false);

    assertThatThrownBy(() -> service.createUser(userCommand("enfermeria", "una-clave-larga", List.of("1"))))
        .isInstanceOf(AdminFailure.class)
        .extracting(failure -> ((AdminFailure) failure).type())
        .isEqualTo(AdminFailure.Type.INVALID);
  }

  @Test
  void updateUserUsuarioInexistenteEsNotFound() {
    when(store.user(7L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.updateUser(userUpdateCommand(true, "", List.of("1"))))
        .isInstanceOf(AdminFailure.class)
        .extracting(failure -> ((AdminFailure) failure).type())
        .isEqualTo(AdminFailure.Type.NOT_FOUND);
  }

  @Test
  void revocaSesionesJwtAlDeshabilitarUsuario() {
    when(store.roleIdsForUser(7L)).thenReturn(Set.of(1L));

    service.updateUser(userUpdateCommand(false, "", List.of("1")));

    verify(sessions).revokeAllForUser(eq(7L), any());
    verify(refreshTokens).revokeAllForUser(7L);
    verify(store).revokeSessions(7L);
  }

  @Test
  void revocaSesionesJwtAlCambiarLaContrasena() {
    when(store.roleIdsForUser(7L)).thenReturn(Set.of(1L));

    service.updateUser(userUpdateCommand(true, "una-clave-nueva", List.of("1")));

    verify(sessions).revokeAllForUser(eq(7L), any());
    verify(refreshTokens).revokeAllForUser(7L);
  }

  @Test
  void revocaSesionesJwtAlReasignarRoles() {
    when(store.roleIdsForUser(7L)).thenReturn(Set.of(1L));

    service.updateUser(userUpdateCommand(true, "", List.of("2")));

    verify(sessions).revokeAllForUser(eq(7L), any());
    verify(refreshTokens).revokeAllForUser(7L);
  }

  @Test
  void noRevocaNadaSiNoHayCambioMaterial() {
    when(store.roleIdsForUser(7L)).thenReturn(Set.of(1L));

    service.updateUser(userUpdateCommand(true, "", List.of("1")));

    verify(sessions, never()).revokeAllForUser(anyLong(), any());
    verify(refreshTokens, never()).revokeAllForUser(anyLong());
    verify(store, never()).revokeSessions(anyLong());
  }

  @Test
  void createRolePropagaConflictoDeClave() {
    when(store.permissionsExist(any())).thenReturn(true);
    when(store.insertRole(any())).thenThrow(new RoleKeyConflictException());

    assertThatThrownBy(() -> service.createRole(roleCommand("enfermeria")))
        .isInstanceOf(AdminFailure.class)
        .extracting(failure -> ((AdminFailure) failure).type())
        .isEqualTo(AdminFailure.Type.CONFLICT);
  }

  @Test
  void revocaSesionesJwtDeTodosLosUsuariosDelRolAlEditarlo() {
    when(store.role(3L)).thenReturn(Optional.of(role(3)));
    when(store.permissionsExist(any())).thenReturn(true);
    when(store.userIdsForRole(3L)).thenReturn(List.of(7L, 8L));

    service.updateRole(new UpdateRoleCommand(
        3L, "Enfermería", "", true, List.of("section.history.view"), UserId.of(1)));

    verify(sessions).revokeAllForUsers(eq(List.of(7L, 8L)), any());
    verify(refreshTokens).revokeAllForUsers(List.of(7L, 8L));
  }

  @Test
  void updateSecurityRechazaDesactivarLoginObligatorio() {
    assertThatThrownBy(() -> service.updateSecurity(new UpdateSecurityCommand(false, 43200, UserId.of(1))))
        .isInstanceOf(AdminFailure.class)
        .extracting(failure -> ((AdminFailure) failure).type())
        .isEqualTo(AdminFailure.Type.INVALID);
  }

  @Test
  void updateSecurityRechazaDuracionInvalida() {
    assertThatThrownBy(() -> service.updateSecurity(new UpdateSecurityCommand(true, 5, UserId.of(1))))
        .isInstanceOf(AdminFailure.class)
        .extracting(failure -> ((AdminFailure) failure).type())
        .isEqualTo(AdminFailure.Type.INVALID);
  }

  private CreateUserCommand userCommand(String username, String password, List<String> roleIds) {
    return new CreateUserCommand(
        username, username + "@hcop.invalid", "Enfermería QA", "", "", true, password, roleIds, UserId.of(1));
  }

  private UpdateUserCommand userUpdateCommand(boolean active, String password, List<String> roleIds) {
    return new UpdateUserCommand(
        7L, "enfermeria", "enfermeria@hcop.invalid", "Enfermería QA", "", "", active, password,
        roleIds, UserId.of(1));
  }

  private CreateRoleCommand roleCommand(String key) {
    return new CreateRoleCommand(key, "Enfermería", "", true, List.of(), UserId.of(1));
  }

  private AdminUser user(long id, boolean active) {
    return new AdminUser(id, "user" + id, "user" + id + "@hcop.invalid", "Usuario " + id, "", "", active, null, List.of());
  }

  private AdminRole role(long id) {
    return new AdminRole(id, "role" + id, "Rol " + id, "", false, true, 0, Set.of());
  }
}
