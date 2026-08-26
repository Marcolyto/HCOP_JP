package ar.com.hexium.hcop.catalog.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.catalog.application.port.in.DrugCatalogUseCase;
import ar.com.hexium.hcop.catalog.application.port.in.LegacyProtocolCatalogUseCase;
import ar.com.hexium.hcop.catalog.application.port.in.LegacyProtocolCatalogUseCase.ProtocolSchemeCatalog;
import ar.com.hexium.hcop.catalog.application.port.in.LegacyProtocolCatalogUseCase.ProtocolSchemeDetail;
import ar.com.hexium.hcop.catalog.application.port.in.TnmCatalogUseCase;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LegacyCatalogControllerPermissionTest {
  private final LegacyCatalogJsonMapper json = new LegacyCatalogJsonMapper();

  @Test
  void exigeLecturaDeProtocolosAntesDeListarElCatalogoCompatible() {
    LegacyProtocolCatalogUseCase protocols = mock(LegacyProtocolCatalogUseCase.class);
    TnmCatalogUseCase tnm = mock(TnmCatalogUseCase.class);
    DrugCatalogUseCase drugs = mock(DrugCatalogUseCase.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(protocols.list("coir")).thenReturn(new ProtocolSchemeCatalog("coir", List.of(), List.of()));

    LegacyCatalogController controller = new LegacyCatalogController(protocols, tnm, drugs, json, auth);

    Map<String, Object> response = controller.protocols("coir", request);

    var order = inOrder(auth, protocols);
    order.verify(auth).requirePermission(request, "section.protocols.view");
    order.verify(protocols).list("coir");
    assertThat(response).containsEntry("ok", true).containsEntry("source", "coir");
  }

  @Test
  void exigeLecturaDeProtocolosAntesDeAbrirElDetalleCompatible() {
    LegacyProtocolCatalogUseCase protocols = mock(LegacyProtocolCatalogUseCase.class);
    TnmCatalogUseCase tnm = mock(TnmCatalogUseCase.class);
    DrugCatalogUseCase drugs = mock(DrugCatalogUseCase.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    Map<String, Object> scheme = Map.of("id", "coir:mama-ac");
    when(protocols.detail("coir:mama-ac", "coir")).thenReturn(new ProtocolSchemeDetail(scheme, List.of()));

    LegacyCatalogController controller = new LegacyCatalogController(protocols, tnm, drugs, json, auth);

    Map<String, Object> response = controller.protocolDetail("coir:mama-ac", "coir", request);

    var order = inOrder(auth, protocols);
    order.verify(auth).requirePermission(request, "section.protocols.view");
    order.verify(protocols).detail("coir:mama-ac", "coir");
    assertThat(response).containsEntry("ok", true).containsEntry("scheme", scheme);
  }

  @Test
  void exigeLecturaDePrescripcionParaBuscarMedicamentos() {
    LegacyProtocolCatalogUseCase protocols = mock(LegacyProtocolCatalogUseCase.class);
    TnmCatalogUseCase tnm = mock(TnmCatalogUseCase.class);
    DrugCatalogUseCase drugs = mock(DrugCatalogUseCase.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(drugs.search("ondansetron")).thenReturn(List.of(Map.of(
        "id", "drug-1",
        "genericName", "Ondansetron",
        "presentation", "8 mg")));

    LegacyCatalogController controller = new LegacyCatalogController(protocols, tnm, drugs, json, auth);

    Map<String, Object> response = controller.medicationSearch("ondansetron", request);

    verify(auth).requirePermission(request, "section.prescriptions.view");
    assertThat(response.get("count")).isEqualTo(1);
  }
}
