package ar.com.hexium.hcop.configuration.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase;
import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase.ConfigurationView;
import ar.com.hexium.hcop.configuration.domain.ConfigurationDefinition;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResearchFormCatalogControllerTest {

  @Test
  void usaPermisoClinicoYNuncaSolicitaInactivos() {
    ConfigurationManagementUseCase configurations = mock(ConfigurationManagementUseCase.class);
    ConfigurationJsonMapper json = mock(ConfigurationJsonMapper.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    ConfigurationView active = view("1", true);
    when(configurations.list("research-form", false)).thenReturn(List.of(active));
    when(json.view(active)).thenReturn(Map.of("id", "1", "active", true));
    ResearchFormCatalogController controller = new ResearchFormCatalogController(configurations, json, auth);

    Map<String, Object> response = controller.list(request);

    var order = inOrder(auth, configurations, json);
    order.verify(auth).requirePermission(request, "section.research.view");
    order.verify(configurations).list("research-form", false);
    order.verify(json).view(active);
    assertThat(response).containsEntry("ok", true).containsEntry("total", 1);
  }

  @Test
  void filtraDefensivamenteCualquierFormularioInactivo() {
    ConfigurationManagementUseCase configurations = mock(ConfigurationManagementUseCase.class);
    ConfigurationJsonMapper json = mock(ConfigurationJsonMapper.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    ConfigurationView inactive = view("2", false);
    when(configurations.list("research-form", false)).thenReturn(List.of(inactive));
    ResearchFormCatalogController controller = new ResearchFormCatalogController(configurations, json, auth);

    Map<String, Object> response = controller.list(request);

    assertThat(response).containsEntry("total", 0);
    assertThat((List<?>) response.get("items")).isEmpty();
  }

  private ConfigurationView view(String id, boolean active) {
    Instant now = Instant.parse("2026-08-05T12:00:00Z");
    return new ConfigurationView(
        id,
        "research-form",
        "form-" + id,
        "Formulario " + id,
        "",
        active,
        ConfigurationDefinition.of(Map.of("fields", List.of())),
        1,
        now,
        now);
  }
}
