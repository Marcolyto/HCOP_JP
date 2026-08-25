package ar.com.hexium.hcop.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.auth.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LegacyCatalogControllerPermissionTest {

  @Test
  void exigeLecturaDeProtocolosAntesDeListarElCatalogoCompatible() {
    LegacyProtocolCatalogService protocols = mock(LegacyProtocolCatalogService.class);
    SeerTnmCatalogService tnm = mock(SeerTnmCatalogService.class);
    DrugCatalogService drugs = mock(DrugCatalogService.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    Map<String, Object> expected = Map.of("ok", true, "protocols", List.of());
    when(protocols.list("coir")).thenReturn(expected);

    LegacyCatalogController controller = new LegacyCatalogController(protocols, tnm, drugs, auth);

    Map<String, Object> response = controller.protocols("coir", request);

    var order = inOrder(auth, protocols);
    order.verify(auth).requirePermission(request, "section.protocols.view");
    order.verify(protocols).list("coir");
    assertThat(response).isSameAs(expected);
  }

  @Test
  void exigeLecturaDeProtocolosAntesDeAbrirElDetalleCompatible() {
    LegacyProtocolCatalogService protocols = mock(LegacyProtocolCatalogService.class);
    SeerTnmCatalogService tnm = mock(SeerTnmCatalogService.class);
    DrugCatalogService drugs = mock(DrugCatalogService.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    Map<String, Object> expected = Map.of("ok", true, "id", "coir:mama-ac");
    when(protocols.detail("coir:mama-ac", "coir")).thenReturn(expected);

    LegacyCatalogController controller = new LegacyCatalogController(protocols, tnm, drugs, auth);

    Map<String, Object> response = controller.protocolDetail("coir:mama-ac", "coir", request);

    var order = inOrder(auth, protocols);
    order.verify(auth).requirePermission(request, "section.protocols.view");
    order.verify(protocols).detail("coir:mama-ac", "coir");
    assertThat(response).isSameAs(expected);
  }

  @Test
  void exigeLecturaDePrescripcionParaBuscarMedicamentos() {
    LegacyProtocolCatalogService protocols = mock(LegacyProtocolCatalogService.class);
    SeerTnmCatalogService tnm = mock(SeerTnmCatalogService.class);
    DrugCatalogService drugs = mock(DrugCatalogService.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(drugs.search("ondansetron")).thenReturn(List.of(Map.of(
        "id", "drug-1",
        "genericName", "Ondansetron",
        "presentation", "8 mg")));

    LegacyCatalogController controller = new LegacyCatalogController(protocols, tnm, drugs, auth);

    Map<String, Object> response = controller.medicationSearch("ondansetron", request);

    verify(auth).requirePermission(request, "section.prescriptions.view");
    assertThat(response.get("count")).isEqualTo(1);
  }
}
