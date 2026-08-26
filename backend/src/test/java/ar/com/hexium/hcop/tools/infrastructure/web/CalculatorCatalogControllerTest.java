package ar.com.hexium.hcop.tools.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.tools.application.port.in.CalculatorCatalogUseCase;
import ar.com.hexium.hcop.tools.application.port.in.CalculatorCatalogUseCase.CalculatorCatalogView;
import ar.com.hexium.hcop.tools.domain.CalculatorSummary;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CalculatorCatalogControllerTest {

  @Test
  void exigeUsoDeHerramientasYProyectaLaVistaDelUseCase() {
    CalculatorCatalogUseCase calculators = mock(CalculatorCatalogUseCase.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    CalculatorSummary calculator = new CalculatorSummary(
        "17", "bsa-local", "SC institucional", "Descripción", 4,
        Map.of("mode", "formula", "expression", "sqrt(weight*height/3600)"));
    CalculatorSummary settings = new CalculatorSummary(
        "", "default", "Herramientas", "Descripción", 0,
        Map.of("enabled", true, "disabledBuiltInKeys", List.of("bmi")));
    when(calculators.list()).thenReturn(new CalculatorCatalogView(List.of(calculator), Optional.of(settings)));
    CalculatorCatalogController controller = new CalculatorCatalogController(
        calculators, auth, JsonMapper.builder().build());

    Map<String, Object> response = controller.list(request);

    verify(auth).requirePermission(request, "section.tools.use");
    assertThat(response).containsEntry("ok", true).containsEntry("total", 1);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> projected = (List<Map<String, Object>>) response.get("calculators");
    assertThat(projected).singleElement().satisfies(item -> {
      assertThat(item).containsEntry("id", "17")
          .containsEntry("key", "bsa-local")
          .containsEntry("revision", 4L);
      assertThat(item).doesNotContainKeys("kind", "active", "createdAt", "updatedAt");
    });
    @SuppressWarnings("unchecked")
    Map<String, Object> settingsView = (Map<String, Object>) response.get("settings");
    assertThat(settingsView).containsEntry("key", "default").containsEntry("revision", 0L);
  }

  @Test
  void sinAjustesInstitucionalesDevuelveSettingsVacio() {
    CalculatorCatalogUseCase calculators = mock(CalculatorCatalogUseCase.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(calculators.list()).thenReturn(new CalculatorCatalogView(List.of(), Optional.empty()));
    CalculatorCatalogController controller = new CalculatorCatalogController(
        calculators, auth, JsonMapper.builder().build());

    Map<String, Object> response = controller.list(request);

    assertThat(response.get("settings")).isEqualTo(Map.of());
    assertThat(response).containsEntry("total", 0);
  }
}
