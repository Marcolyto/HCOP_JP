package ar.com.hexium.hcop.workflow.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.workflow.application.port.in.TreatmentWorkflowUseCase.CreateRequestCommand;
import ar.com.hexium.hcop.workflow.application.port.in.TreatmentWorkflowUseCase.SuspendCommand;
import ar.com.hexium.hcop.workflow.application.port.out.PatientEvolutionPort;
import ar.com.hexium.hcop.workflow.application.port.out.TreatmentWorkflowStore;
import ar.com.hexium.hcop.workflow.domain.TreatmentWorkflowSummary;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TreatmentWorkflowApplicationServiceCycleBoundsTest {
  private static final long PATIENT_ID = 41;
  private static final String TREATMENT_ID = "tx-range";

  private final TreatmentWorkflowStore store = mock(TreatmentWorkflowStore.class);
  private final PatientEvolutionPort evolutions = mock(PatientEvolutionPort.class);
  private final TreatmentWorkflowApplicationService service = new TreatmentWorkflowApplicationService(
      store, evolutions, Clock.fixed(Instant.parse("2026-07-30T12:00:00Z"), ZoneOffset.UTC));

  @BeforeEach
  void treatmentHasCyclesThreeThroughSix() {
    when(store.treatmentExists(PATIENT_ID, TREATMENT_ID)).thenReturn(true);
    when(store.treatment(PATIENT_ID, TREATMENT_ID)).thenReturn(java.util.Optional.of(
        new TreatmentWorkflowSummary(
            PATIENT_ID, TREATMENT_ID, "Esquema de prueba", "Diagnostico de prueba", "Iniciado",
            "30111222", "Paciente, Prueba", 3, 4)));
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 7, 500})
  void suspensionRejectsCyclesOutsideTheTreatmentRange(int cycle) {
    SuspendCommand command = new SuspendCommand(
        PATIENT_ID, TREATMENT_ID, "temporary", "Toxicidad que requiere pausa", cycle, null,
        22L, "Oncologo de prueba");

    assertThatThrownBy(() -> service.suspend(command))
        .isInstanceOf(WorkflowFailure.class)
        .hasMessageContaining("Rango");

    verify(store, never()).upsertManagement(
        org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyInt(),
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyBoolean(),
        org.mockito.ArgumentMatchers.anyLong());
    verifyNoInteractions(evolutions);
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
    CreateRequestCommand command = new CreateRequestCommand(
        requestType, Long.toString(PATIENT_ID), TREATMENT_ID, cycle, "72",
        "Revisar el proximo ciclo", 22L, "Oncologo de prueba");

    assertThatThrownBy(() -> service.createRequest(command))
        .isInstanceOf(WorkflowFailure.class)
        .hasMessageContaining("Rango");

    verify(store, never()).insertRequest(
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyInt(),
        org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any());
    verifyNoInteractions(evolutions);
  }
}
