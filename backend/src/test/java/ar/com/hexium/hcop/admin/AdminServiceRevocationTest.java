package ar.com.hexium.hcop.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.auth.PasswordService;
import ar.com.hexium.hcop.auth.RefreshTokenRepository;
import ar.com.hexium.hcop.auth.SessionStateRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * F2.7: el access token JWT lleva roles/permisos horneados — sin revocar acá, deshabilitar un
 * usuario, cambiarle roles/contraseña o editar los permisos de un rol solo surtiría efecto
 * cuando el token vence.
 */
class AdminServiceRevocationTest {
  private final AdminRepository repository = mock(AdminRepository.class);
  private final PasswordService passwords = mock(PasswordService.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC);
  private final SessionStateRepository sessions = mock(SessionStateRepository.class);
  private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
  private final ObjectMapper mapper = new ObjectMapper();
  private AdminService service;

  @BeforeEach
  void setUp() {
    service = new AdminService(repository, passwords, clock, sessions, refreshTokens);
    when(repository.user(anyLong())).thenReturn(java.util.Optional.of(Map.of("id", "7")));
    when(repository.usernameOrEmailExists(any(), any(), any())).thenReturn(false);
    when(repository.rolesExist(any())).thenReturn(true);
    when(passwords.encode(any())).thenReturn("hashed");
  }

  @Test
  void revocaSesionesJwtAlDeshabilitarUsuario() {
    when(repository.roleIdsForUser(7L)).thenReturn(Set.of(1L));

    service.updateUser(7L, userBody(false, "", List.of(1L)), 1L);

    verify(sessions).revokeAllForUser(eq(7L), any());
    verify(refreshTokens).revokeAllForUser(7L);
    verify(repository).revokeSessions(7L);
  }

  @Test
  void revocaSesionesJwtAlCambiarLaContrasena() {
    when(repository.roleIdsForUser(7L)).thenReturn(Set.of(1L));

    service.updateUser(7L, userBody(true, "una-clave-nueva", List.of(1L)), 1L);

    verify(sessions).revokeAllForUser(eq(7L), any());
    verify(refreshTokens).revokeAllForUser(7L);
  }

  @Test
  void revocaSesionesJwtAlReasignarRoles() {
    when(repository.roleIdsForUser(7L)).thenReturn(Set.of(1L));

    service.updateUser(7L, userBody(true, "", List.of(2L)), 1L);

    verify(sessions).revokeAllForUser(eq(7L), any());
    verify(refreshTokens).revokeAllForUser(7L);
  }

  @Test
  void noRevocaNadaSiNoHayCambioMaterial() {
    when(repository.roleIdsForUser(7L)).thenReturn(Set.of(1L));

    service.updateUser(7L, userBody(true, "", List.of(1L)), 1L);

    verify(sessions, never()).revokeAllForUser(anyLong(), any());
    verify(refreshTokens, never()).revokeAllForUser(anyLong());
    verify(repository, never()).revokeSessions(anyLong());
  }

  @Test
  void revocaSesionesJwtDeTodosLosUsuariosDelRolAlEditarlo() {
    when(repository.role(3L)).thenReturn(java.util.Optional.of(Map.of("id", "3")));
    when(repository.permissionsExist(any())).thenReturn(true);
    when(repository.userIdsForRole(3L)).thenReturn(List.of(7L, 8L));

    ObjectNode body = mapper.createObjectNode();
    body.put("name", "Enfermería");
    body.put("active", true);
    body.putArray("permissions").add("section.history.view");
    service.updateRole(3L, body, 1L);

    verify(sessions).revokeAllForUsers(eq(List.of(7L, 8L)), any());
    verify(refreshTokens).revokeAllForUsers(List.of(7L, 8L));
  }

  private ObjectNode userBody(boolean active, String password, List<Long> roleIds) {
    ObjectNode body = mapper.createObjectNode();
    body.put("username", "enfermeria");
    body.put("email", "enfermeria@hcop.invalid");
    body.put("displayName", "Enfermería QA");
    body.put("active", active);
    body.put("password", password);
    var array = body.putArray("roleIds");
    roleIds.forEach(id -> array.add(id.toString()));
    return body;
  }
}
