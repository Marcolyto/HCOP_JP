package ar.com.hexium.hcop.tools.infrastructure.web;

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
import tools.jackson.databind.json.JsonMapper;

class CalculatorCatalogControllerTest {

  @Test
  void exigeUsoDeHerramientasYProyectaSoloDefinicionesActivas() {
    ConfigurationManagementUseCase configurations = mock(ConfigurationManagementUseCase.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(configurations.list("calculator", false)).thenReturn(List.of(
        view("17", "calculator", "bsa-local", "SC institucional", true, 4,
            Map.of("mode", "formula", "expression", "sqrt(weight*height/3600)")),
        view("18", "calculator", "archived", "Archivada", false, 2,
            Map.of("mode", "score"))));
    when(configurations.list("tool-settings", false)).thenReturn(List.of(
        view("", "tool-settings", "default", "Herramientas", true, 0,
            Map.of("enabled", true, "disabledBuiltInKeys", List.of("bmi")))));
    CalculatorCatalogController controller = new CalculatorCatalogController(
        configurations, auth, JsonMapper.builder().build());

    Map<String, Object> response = controller.list(request);

    var order = inOrder(auth, configurations);
    order.verify(auth).requirePermission(request, "section.tools.use");
    order.verify(configurations).list("calculator", false);
    order.verify(configurations).list("tool-settings", false);
    assertThat(response).containsEntry("ok", true).containsEntry("total", 1);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> calculators = (List<Map<String, Object>>) response.get("calculators");
    assertThat(calculators).singleElement().satisfies(item -> {
      assertThat(item).containsEntry("id", "17")
          .containsEntry("key", "bsa-local")
          .containsEntry("revision", 4L);
      assertThat(item).doesNotContainKeys("kind", "active", "createdAt", "updatedAt");
    });
    @SuppressWarnings("unchecked")
    Map<String, Object> settings = (Map<String, Object>) response.get("settings");
    assertThat(settings).containsEntry("key", "default").containsEntry("revision", 0L);
  }

  private ConfigurationView view(
      String id,
      String kind,
      String key,
      String name,
      boolean active,
      long revision,
      Object definition) {
    return new ConfigurationView(
        id,
        kind,
        key,
        name,
        "Descripción",
        active,
        ConfigurationDefinition.of(definition),
        revision,
        Instant.EPOCH,
        Instant.EPOCH);
  }
}
