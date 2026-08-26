package ar.com.hexium.hcop.admin.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.hexium.hcop.admin.application.port.in.AdminManagementUseCase.CreateUserCommand;
import ar.com.hexium.hcop.admin.domain.AdminRole;
import ar.com.hexium.hcop.admin.domain.AdminUser;
import ar.com.hexium.hcop.admin.domain.SecuritySettings;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class AdminJsonMapperTest {
  private final AdminJsonMapper mapper = new AdminJsonMapper();
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void createUserCommandExtraeYNormalizaLosRoleIds() {
    ObjectNode body = json.createObjectNode();
    body.put("username", "enfermeria");
    body.put("email", "enfermeria@hcop.invalid");
    body.put("displayName", "Enfermería QA");
    body.put("password", "una-clave-larga");
    body.putArray("roleIds").add("1").add("2");

    CreateUserCommand command = mapper.createUserCommand(body, 5);

    assertThat(command.username()).isEqualTo("enfermeria");
    assertThat(command.roleIds()).containsExactly("1", "2");
    assertThat(command.active()).isTrue();
    assertThat(command.actorId().value()).isEqualTo(5L);
  }

  @Test
  void createUserCommandRespetaActiveExplicitoEnFalse() {
    ObjectNode body = json.createObjectNode();
    body.put("active", false);

    CreateUserCommand command = mapper.createUserCommand(body, 5);

    assertThat(command.active()).isFalse();
  }

  @Test
  void viewDeUsuarioProyectaRolesAnidados() {
    AdminUser user = new AdminUser(
        7, "enfermeria", "enfermeria@hcop.invalid", "Enfermería QA", "", "", true,
        Instant.parse("2026-08-01T10:00:00Z"),
        List.of(new AdminUser.RoleSummary(1, "nurse", "Enfermería", "", false, true)));

    Map<String, Object> view = mapper.view(user);

    assertThat(view).containsEntry("id", "7").containsEntry("active", true)
        .containsEntry("lastLoginAt", "2026-08-01T10:00:00Z");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> roles = (List<Map<String, Object>>) view.get("roles");
    assertThat(roles).singleElement().satisfies(role -> assertThat(role).containsEntry("key", "nurse"));
  }

  @Test
  void viewDeUsuarioSinUltimoLoginDevuelveVacio() {
    AdminUser user = new AdminUser(7, "u", "u@hcop.invalid", "U", "", "", true, null, List.of());

    assertThat(mapper.view(user)).containsEntry("lastLoginAt", "");
  }

  @Test
  void viewDeRolIncluyePermisosYUserCount() {
    AdminRole role = new AdminRole(3, "nurse", "Enfermería", "", false, true, 2, Set.of("a", "b"));

    Map<String, Object> view = mapper.view(role);

    assertThat(view).containsEntry("id", "3").containsEntry("userCount", 2L)
        .containsEntry("permissions", Set.of("a", "b"));
  }

  @Test
  void viewDeSeguridadProyectaTodosLosCampos() {
    SecuritySettings settings = new SecuritySettings(true, "1", "admin", "admin@hcop.invalid", "Admin", 43200, 3);

    Map<String, Object> view = mapper.view(settings);

    assertThat(view).containsEntry("loginRequired", true)
        .containsEntry("sessionDurationMinutes", 43200)
        .containsEntry("revision", 3L);
  }
}
