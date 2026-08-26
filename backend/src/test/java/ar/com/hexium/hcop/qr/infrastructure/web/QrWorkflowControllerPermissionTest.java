package ar.com.hexium.hcop.qr.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.qr.application.port.in.QrUseCase;
import ar.com.hexium.hcop.qr.application.port.in.QrUseCase.ScanResult;
import ar.com.hexium.hcop.qr.domain.QrPatientView;
import ar.com.hexium.hcop.qr.domain.QrTreatmentView;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class QrWorkflowControllerPermissionTest {
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final QrJsonMapper json = new QrJsonMapper();

  private SessionPrincipal actor() {
    return new SessionPrincipal(5L, "enfermeria", "", "Actor", "", "", true, null, List.of(), Set.of());
  }

  @Test
  void exigePermisoDeHospitalDeDiaAntesDeImprimirElQr() {
    QrUseCase qr = mock(QrUseCase.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(qr.printableHtml(1, "tx-1", 2, 3)).thenReturn("<html></html>");
    QrWorkflowController controller = new QrWorkflowController(qr, json, auth);

    var response = controller.document(1, "tx-1", 2, 3, request);

    var order = inOrder(auth, qr);
    order.verify(auth).requirePermission(request, "section.day-hospital.view");
    order.verify(qr).printableHtml(1, "tx-1", 2, 3);
    assertThat(response.getBody()).isEqualTo("<html></html>");
  }

  @Test
  void exigePermisoDeAdministracionAntesDeEscanear() {
    QrUseCase qr = mock(QrUseCase.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(auth.require(request)).thenReturn(actor());
    when(qr.scan(any())).thenReturn(new ScanResult(
        new QrPatientView(1, "Paciente", "111"), new QrTreatmentView("tx-1", "Esquema", "Diagnostico"),
        Map.of("id", "9"), false, "evolution", 2L));
    QrWorkflowController controller = new QrWorkflowController(qr, json, auth);
    var body = mapper.createObjectNode().put("code", "codigo").put("operationId", "op-1");

    Map<String, Object> response = controller.scan(body, request);

    var order = inOrder(auth, qr);
    order.verify(auth).requirePermission(request, "application.administration.manage");
    order.verify(qr).scan(any());
    assertThat(response).containsEntry("ok", true).containsEntry("idempotent", false);
  }
}
