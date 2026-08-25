package ar.com.hexium.hcop.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.auth.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AjccCatalogControllerPermissionTest {

  @Test
  void exigeLecturaDeHerramientasAntesDeListarAjcc() {
    AjccCatalogService catalog = mock(AjccCatalogService.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(catalog.list()).thenReturn(List.of());
    AjccCatalogController controller = new AjccCatalogController(catalog, auth);

    Map<String, Object> response = controller.list(request);

    var order = inOrder(auth, catalog);
    order.verify(auth).requirePermission(request, "section.tools.view");
    order.verify(catalog).list();
    assertThat(response).containsEntry("ok", true).containsEntry("count", 0);
  }

  @Test
  void exigeLecturaDeHerramientasAntesDeAbrirDetalleAjcc() {
    AjccCatalogService catalog = mock(AjccCatalogService.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    Map<String, Object> expected = Map.of("ok", true, "id", "mama");
    when(catalog.detail("mama")).thenReturn(expected);
    AjccCatalogController controller = new AjccCatalogController(catalog, auth);

    Map<String, Object> response = controller.detail("mama", request);

    var order = inOrder(auth, catalog);
    order.verify(auth).requirePermission(request, "section.tools.view");
    order.verify(catalog).detail("mama");
    assertThat(response).isSameAs(expected);
  }

  @Test
  void exigeUsoDeHerramientasAntesDeCalcularEstadioAjcc() {
    AjccCatalogService catalog = mock(AjccCatalogService.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    Map<String, Object> values = Map.of("t", "T2", "n", "N0", "m", "M0");
    Map<String, Object> expected = Map.of("ok", true, "stage", "IIA");
    when(catalog.stage("mama", values)).thenReturn(expected);
    AjccCatalogController controller = new AjccCatalogController(catalog, auth);

    Map<String, Object> response = controller.stage(
        Map.of("id", "mama", "values", values), request);

    var order = inOrder(auth, catalog);
    order.verify(auth).requirePermission(request, "section.tools.use");
    order.verify(catalog).stage("mama", values);
    assertThat(response).isSameAs(expected);
  }
}
