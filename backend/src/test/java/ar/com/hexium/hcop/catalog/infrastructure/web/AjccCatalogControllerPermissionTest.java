package ar.com.hexium.hcop.catalog.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.catalog.application.port.in.AjccStagingUseCase;
import ar.com.hexium.hcop.catalog.application.port.in.AjccStagingUseCase.AjccSiteView;
import ar.com.hexium.hcop.catalog.application.port.in.AjccStagingUseCase.AjccStagingOutcome;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AjccCatalogControllerPermissionTest {
  private final AjccCatalogJsonMapper json = new AjccCatalogJsonMapper(JsonMapper.builder().build());

  @Test
  void exigeLecturaDeHerramientasAntesDeListarAjcc() {
    AjccStagingUseCase catalog = mock(AjccStagingUseCase.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(catalog.list()).thenReturn(List.of());
    AjccCatalogController controller = new AjccCatalogController(catalog, json, auth);

    Map<String, Object> response = controller.list(request);

    var order = inOrder(auth, catalog);
    order.verify(auth).requirePermission(request, "section.tools.view");
    order.verify(catalog).list();
    assertThat(response).containsEntry("ok", true).containsEntry("count", 0);
  }

  @Test
  void exigeLecturaDeHerramientasAntesDeAbrirDetalleAjcc() {
    AjccStagingUseCase catalog = mock(AjccStagingUseCase.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    AjccSiteView view = new AjccSiteView("mama", "Mama", "AJCC 8", "Catálogo local validado", "", Map.of());
    when(catalog.detail("mama")).thenReturn(view);
    AjccCatalogController controller = new AjccCatalogController(catalog, json, auth);

    Map<String, Object> response = controller.detail("mama", request);

    var order = inOrder(auth, catalog);
    order.verify(auth).requirePermission(request, "section.tools.view");
    order.verify(catalog).detail("mama");
    assertThat(response).containsEntry("ok", true).containsEntry("id", "mama");
  }

  @Test
  void exigeUsoDeHerramientasAntesDeCalcularEstadioAjcc() {
    AjccStagingUseCase catalog = mock(AjccStagingUseCase.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    Map<String, Object> values = Map.of("t", "T2", "n", "N0", "m", "M0");
    when(catalog.stage("mama", Map.of("t", "T2", "n", "N0", "m", "M0")))
        .thenReturn(new AjccStagingOutcome(true, "IIA", 12, List.of()));
    AjccCatalogController controller = new AjccCatalogController(catalog, json, auth);

    Map<String, Object> response = controller.stage(
        Map.of("id", "mama", "values", values), request);

    var order = inOrder(auth, catalog);
    order.verify(auth).requirePermission(request, "section.tools.use");
    order.verify(catalog).stage("mama", Map.of("t", "T2", "n", "N0", "m", "M0"));
    assertThat(response).containsEntry("ok", true).containsEntry("stage", "IIA").containsEntry("sourceRow", 12);
  }
}
