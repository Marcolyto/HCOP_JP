package ar.com.hexium.hcop.workflow;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.patient.PatientDocumentService;
import ar.com.hexium.hcop.workflow.TreatmentWorkflowRepository.TreatmentSummary;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

class TreatmentWorkflowServiceCycleBoundsTest {
  private static final long PATIENT_ID = 41;
  private static final String TREATMENT_ID = "tx-range";

  private final TreatmentWorkflowRepository workflows =
      mock(TreatmentWorkflowRepository.class);
  private final PatientDocumentService documents = mock(PatientDocumentService.class);
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final TreatmentWorkflowService service = new TreatmentWorkflowService(
      workflows,
      documents,
      mapper,
      Clock.fixed(Instant.parse("2026-07-30T12:00:00Z"), ZoneOffset.UTC));

  @BeforeEach
  void treatmentHasCyclesThreeThroughSix() {
    when(workflows.treatmentExists(PATIENT_ID, TREATMENT_ID)).thenReturn(true);
    when(workflows.treatment(PATIENT_ID, TREATMENT_ID)).thenReturn(new TreatmentSummary(
        PATIENT_ID,
        TREATMENT_ID,
        "Esquema de prueba",
        "Diagnostico de prueba",
        "Iniciado",
        "30111222",
        "Paciente, Prueba",
        3,
        4));
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 7, 500})
  void suspensionRejectsCyclesOutsideTheTreatmentRange(int cycle) {
    ObjectNode body = mapper.createObjectNode()
        .put("kind", "temporary")
        .put("reason", "Toxicidad que requiere pausa")
        .put("cycleNumber", cycle);

    assertThatThrownBy(() ->
        service.suspend(PATIENT_ID, TREATMENT_ID, body, actor()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Rango");

    verify(workflows, never()).upsertManagement(
        org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyInt(),
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyBoolean(),
        org.mockito.ArgumentMatchers.anyLong());
    verifyNoInteractions(documents);
  }

  @ParameterizedTest
  @ValueSource(strings = {"prescription_request", "continuity_request"})
  void workflowRequestsRejectACycleBeforeTheInitialCycle(String requestType) {
    assertRequestOutsideTreatmentRange(requestType, 2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"prescription_request", "continuity_request"})
  void workflowRequestsRejectACycleAfterTheLastCycle(String requestType) {
    assertRequestOutsideTreatmentRange(requestType, 7);
  }

  private void assertRequestOutsideTreatmentRange(String requestType, int cycle) {
    ObjectNode body = requestBody(requestType, cycle);

    assertThatThrownBy(() -> service.createRequest(body, actor()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Rango");

    verify(workflows, never()).insertRequest(
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyInt(),
        org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any());
    verifyNoInteractions(documents);
  }

  private ObjectNode requestBody(String requestType, int cycle) {
    return mapper.createObjectNode()
        .put("type", requestType)
        .put("patientId", PATIENT_ID)
        .put("treatmentId", TREATMENT_ID)
        .put("cycleNumber", cycle)
        .put("assignedToUserId", 72)
        .put("message", "Revisar el proximo ciclo");
  }

  private SessionPrincipal actor() {
    return new SessionPrincipal(
        22,
        "oncologo",
        "",
        "Oncologo de prueba",
        "",
        "",
        true,
        null,
        List.of(),
        Set.of("workflow.request.create"));
  }
}
