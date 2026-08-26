package ar.com.hexium.hcop.workflow.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.workflow.application.port.in.TreatmentWorkflowUseCase;
import ar.com.hexium.hcop.workflow.application.port.in.TreatmentWorkflowUseCase.ManagementActionResult;
import ar.com.hexium.hcop.workflow.application.port.in.TreatmentWorkflowUseCase.RequestActionResult;
import ar.com.hexium.hcop.workflow.domain.ManagementState;
import ar.com.hexium.hcop.workflow.domain.WorkflowRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TreatmentWorkflowControllerPermissionTest {
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final TreatmentWorkflowJsonMapper json = new TreatmentWorkflowJsonMapper();

  private SessionPrincipal actor() {
    return new SessionPrincipal(
        5L, "oncologo", "", "Actor de prueba", "", "", true, null, List.of(), Set.of());
  }

  @Test
  void exigePermisoDeSuspensionAntesDeSuspender() {
    TreatmentWorkflowUseCase workflows = mock(TreatmentWorkflowUseCase.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(auth.require(request)).thenReturn(actor());
    ManagementState state = new ManagementState(1, "tx-1", "temporary_hold", 1, "motivo", null, true, 1, Instant.now());
    when(workflows.suspend(any())).thenReturn(new ManagementActionResult(state, "evolution", 2L));
    TreatmentWorkflowController controller = new TreatmentWorkflowController(workflows, json, auth);
    var body = mapper.createObjectNode().put("kind", "temporary").put("reason", "Toxicidad");

    Map<String, Object> response = controller.suspend(1, "tx-1", body, request);

    var order = inOrder(auth, workflows);
    order.verify(auth).requirePermission(request, "workflow.suspend");
    order.verify(workflows).suspend(any());
    assertThat(response).containsEntry("ok", true).containsEntry("documentRevision", 2L);
  }

  @Test
  void exigePermisoDeReanudacionAntesDeReanudar() {
    TreatmentWorkflowUseCase workflows = mock(TreatmentWorkflowUseCase.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(auth.require(request)).thenReturn(actor());
    ManagementState state = new ManagementState(1, "tx-1", "active", 1, "motivo", null, false, 2, Instant.now());
    when(workflows.resume(any())).thenReturn(new ManagementActionResult(state, "evolution", 3L));
    TreatmentWorkflowController controller = new TreatmentWorkflowController(workflows, json, auth);
    var body = mapper.createObjectNode().put("reason", "Mejoría clínica");

    controller.resume(1, "tx-1", body, request);

    var order = inOrder(auth, workflows);
    order.verify(auth).requirePermission(request, "workflow.resume");
    order.verify(workflows).resume(any());
  }

  @Test
  void eligeElPermisoSegunElTipoDeSolicitudAlCrear() {
    TreatmentWorkflowUseCase workflows = mock(TreatmentWorkflowUseCase.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(auth.require(request)).thenReturn(actor());
    WorkflowRequest created = new WorkflowRequest(
        1, "prescription_request", "pending", 1, "tx-1", 1, 5, 9, "", Map.of(), "", "", null,
        null, null, null, Instant.now(), Instant.now(), "111", "Paciente", "Esquema",
        "Diagnostico", "Actor", "Asignado");
    when(workflows.createRequest(any())).thenReturn(new RequestActionResult(created, "evolution", 1L));
    TreatmentWorkflowController controller = new TreatmentWorkflowController(workflows, json, auth);
    var body = mapper.createObjectNode().put("type", "prescription_request")
        .put("patientId", "1").put("treatmentId", "tx-1").put("assignedToUserId", "9");

    controller.create(body, request);

    var order = inOrder(auth, workflows);
    order.verify(auth).requirePermission(request, "workflow.request-prescription");
    order.verify(workflows).createRequest(any());
  }
}
