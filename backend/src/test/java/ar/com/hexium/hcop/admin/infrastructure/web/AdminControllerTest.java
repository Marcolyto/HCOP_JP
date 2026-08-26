package ar.com.hexium.hcop.admin.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.admin.application.port.in.AdminManagementUseCase;
import ar.com.hexium.hcop.admin.domain.AdminUser;
import ar.com.hexium.hcop.admin.domain.SecuritySettings;
import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.auth.SessionPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class AdminControllerTest {

  @Test
  void usersExigePermisoYDuplicaLaListaComoItemsYUsers() {
    AdminManagementUseCase admin = mock(AdminManagementUseCase.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    AdminUser user = new AdminUser(1, "u", "u@hcop.invalid", "U", "", "", true, null, List.of());
    when(admin.users()).thenReturn(List.of(user));
    AdminController controller = new AdminController(admin, new AdminJsonMapper(), auth);

    Map<String, Object> response = controller.users(request);

    verify(auth).requirePermission(request, "admin.manage-users");
    assertThat(response.get("items")).isEqualTo(response.get("users"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
    assertThat(items).singleElement().satisfies(item -> assertThat(item).containsEntry("id", "1"));
  }

  @Test
  void securitySinFilaDevuelveItemVacio() {
    AdminManagementUseCase admin = mock(AdminManagementUseCase.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(admin.security()).thenReturn(Optional.empty());
    AdminController controller = new AdminController(admin, new AdminJsonMapper(), auth);

    Map<String, Object> response = controller.security(request);

    verify(auth).requirePermission(request, "admin.manage-security");
    assertThat(response).containsEntry("item", Map.of());
  }

  @Test
  void securityConFilaProyectaElItem() {
    AdminManagementUseCase admin = mock(AdminManagementUseCase.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(admin.security()).thenReturn(Optional.of(
        new SecuritySettings(true, "", "", "", "", 43200, 1)));
    AdminController controller = new AdminController(admin, new AdminJsonMapper(), auth);

    Map<String, Object> response = controller.security(request);

    @SuppressWarnings("unchecked")
    Map<String, Object> item = (Map<String, Object>) response.get("item");
    assertThat(item).containsEntry("sessionDurationMinutes", 43200);
  }

  @Test
  void createUserExigePermisoYDevuelve201() {
    AdminManagementUseCase admin = mock(AdminManagementUseCase.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    AdminUser created = new AdminUser(9, "u", "u@hcop.invalid", "U", "", "", true, null, List.of());
    when(admin.createUser(any())).thenReturn(created);
    SessionPrincipal principal = mock(SessionPrincipal.class);
    when(principal.userId()).thenReturn(5L);
    when(auth.require(request)).thenReturn(principal);
    AdminController controller = new AdminController(admin, new AdminJsonMapper(), auth);
    ObjectNode body = new ObjectMapper().createObjectNode();

    var response = controller.createUser(body, request);

    verify(auth).requirePermission(request, "admin.manage-users");
    assertThat(response.getStatusCode().value()).isEqualTo(201);
  }

  @Test
  void clinicalUsersSoloAutenticaSinExigirPermisoEspecifico() {
    AdminManagementUseCase admin = mock(AdminManagementUseCase.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(admin.usersWithPermission("")).thenReturn(List.of());
    AdminController controller = new AdminController(admin, new AdminJsonMapper(), auth);

    controller.clinicalUsers("", request);

    verify(auth).require(request);
  }
}
