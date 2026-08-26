package ar.com.hexium.hcop.workflow.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.workflow.application.port.in.TreatmentWorkflowUseCase.CreateRequestCommand;
import ar.com.hexium.hcop.workflow.application.port.in.TreatmentWorkflowUseCase.ResolveCommand;
import ar.com.hexium.hcop.workflow.application.port.in.TreatmentWorkflowUseCase.ResumeCommand;
import ar.com.hexium.hcop.workflow.application.port.out.PatientEvolutionPort;
import ar.com.hexium.hcop.workflow.application.port.out.PatientEvolutionPort.AppendedEvolution;
import ar.com.hexium.hcop.workflow.application.port.out.TreatmentWorkflowStore;
import ar.com.hexium.hcop.workflow.application.port.out.TreatmentWorkflowStore.DuplicateRequestException;
import ar.com.hexium.hcop.workflow.domain.ManagementState;
import ar.com.hexium.hcop.workflow.domain.TreatmentWorkflowSummary;
import ar.com.hexium.hcop.workflow.domain.WorkflowRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TreatmentWorkflowApplicationServiceTest {
  private static final long PATIENT_ID = 1;
  private static final String TREATMENT_ID = "tx-1";
  private final TreatmentWorkflowStore store = mock(TreatmentWorkflowStore.class);
  private final PatientEvolutionPort evolutions = mock(PatientEvolutionPort.class);
  private final TreatmentWorkflowApplicationService service = new TreatmentWorkflowApplicationService(
      store, evolutions, Clock.fixed(Instant.parse("2026-07-30T12:00:00Z"), ZoneOffset.UTC));

  private TreatmentWorkflowSummary treatment() {
    return new TreatmentWorkflowSummary(
        PATIENT_ID, TREATMENT_ID, "Esquema", "Diagnostico", "Iniciado", "111", "Paciente", 1, 6);
  }

  @Test
  void resumeRechazaCuandoElTratamientoNoEstaSuspendido() {
    when(store.treatmentExists(PATIENT_ID, TREATMENT_ID)).thenReturn(true);
    when(store.treatment(PATIENT_ID, TREATMENT_ID)).thenReturn(Optional.of(treatment()));
    when(store.management(PATIENT_ID, TREATMENT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.resume(
        new ResumeCommand(PATIENT_ID, TREATMENT_ID, "Motivo válido", 1L, "Actor")))
        .isInstanceOf(WorkflowFailure.class)
        .satisfies(ex -> assertThat(((WorkflowFailure) ex).type()).isEqualTo(WorkflowFailure.Type.CONFLICT))
        .hasMessageContaining("no está suspendido");
  }

  @Test
  void createRequestTraduceElDuplicadoComoConflicto() {
    when(store.treatmentExists(PATIENT_ID, TREATMENT_ID)).thenReturn(true);
    when(store.treatment(PATIENT_ID, TREATMENT_ID)).thenReturn(Optional.of(treatment()));
    when(store.insertRequest(
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt(),
        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
        .thenThrow(new DuplicateRequestException("dup"));

    CreateRequestCommand command = new CreateRequestCommand(
        "continuity_request", Long.toString(PATIENT_ID), TREATMENT_ID, 1, "72", "msg", 1L, "Actor");

    assertThatThrownBy(() -> service.createRequest(command))
        .isInstanceOf(WorkflowFailure.class)
        .satisfies(ex -> assertThat(((WorkflowFailure) ex).type()).isEqualTo(WorkflowFailure.Type.CONFLICT));
  }

  @Test
  void resolveRechazaSinElPermisoRequerido() {
    WorkflowRequest current = new WorkflowRequest(
        9, "prescription_request", "pending", PATIENT_ID, TREATMENT_ID, 1, 1, 5, "",
        Map.of(), "", "", null, null, null, null, Instant.now(), Instant.now(), "111",
        "Paciente", "Esquema", "Diagnostico", "Solicitante", "Asignado");
    when(store.request(9L)).thenReturn(Optional.of(current));

    ResolveCommand command = new ResolveCommand(
        9L, "prescription_confirmed", "", null, 5L, "Actor", permission -> false);

    assertThatThrownBy(() -> service.resolveRequest(command))
        .isInstanceOf(WorkflowFailure.class)
        .satisfies(ex -> assertThat(((WorkflowFailure) ex).type()).isEqualTo(WorkflowFailure.Type.FORBIDDEN));
  }

  @Test
  void resolveRechazaCuandoLaSolicitudYaNoEstaDisponible() {
    WorkflowRequest current = new WorkflowRequest(
        9, "prescription_request", "resolved", PATIENT_ID, TREATMENT_ID, 1, 1, 5, "",
        Map.of(), "", "", null, null, null, null, Instant.now(), Instant.now(), "111",
        "Paciente", "Esquema", "Diagnostico", "Solicitante", "Asignado");
    when(store.request(9L)).thenReturn(Optional.of(current));

    ResolveCommand command = new ResolveCommand(
        9L, "prescription_confirmed", "", null, 5L, "Actor", permission -> true);

    assertThatThrownBy(() -> service.resolveRequest(command))
        .isInstanceOf(WorkflowFailure.class)
        .satisfies(ex -> assertThat(((WorkflowFailure) ex).type()).isEqualTo(WorkflowFailure.Type.CONFLICT))
        .hasMessageContaining("ya no está disponible");
  }

  @Test
  void resolveConfirmaLaPrescripcionYReactivaElTratamiento() {
    WorkflowRequest current = new WorkflowRequest(
        9, "prescription_request", "pending", PATIENT_ID, TREATMENT_ID, 2, 1, 5, "",
        Map.of(), "", "", null, null, null, null, Instant.now(), Instant.now(), "111",
        "Paciente", "Esquema", "Diagnostico", "Solicitante", "Asignado");
    WorkflowRequest resolved = new WorkflowRequest(
        9, "prescription_request", "resolved", PATIENT_ID, TREATMENT_ID, 2, 1, 5, "",
        Map.of(), "prescription_confirmed", "", null, Instant.now(), Instant.now(), 5L,
        Instant.now(), Instant.now(), "111", "Paciente", "Esquema", "Diagnostico", "Solicitante",
        "Asignado");
    when(store.request(9L)).thenReturn(Optional.of(current));
    when(store.resolve(9L, 5L, "prescription_confirmed", "", null, Instant.parse("2026-07-30T12:00:00Z")))
        .thenReturn(Optional.of(resolved));
    when(store.management(PATIENT_ID, TREATMENT_ID)).thenReturn(Optional.of(new ManagementState(
        PATIENT_ID, TREATMENT_ID, "temporary_hold", 2, "motivo", null, true, 1, Instant.now())));
    when(store.treatmentExists(PATIENT_ID, TREATMENT_ID)).thenReturn(true);
    when(store.treatment(PATIENT_ID, TREATMENT_ID)).thenReturn(Optional.of(treatment()));
    when(evolutions.append(
        org.mockito.ArgumentMatchers.eq(PATIENT_ID), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.eq("Actor")))
        .thenReturn(new AppendedEvolution("evolution", 3L));

    var result = service.resolveRequest(new ResolveCommand(
        9L, "prescription_confirmed", "", null, 5L, "Actor", permission -> true));

    assertThat(result.request()).isEqualTo(resolved);
    assertThat(result.evolution()).isEqualTo("evolution");
    assertThat(result.documentRevision()).isEqualTo(3L);
    org.mockito.Mockito.verify(store).updatePrescriptionState(PATIENT_ID, TREATMENT_ID, 2, "confirmed", 5L);
    org.mockito.Mockito.verify(store).upsertManagement(
        PATIENT_ID, TREATMENT_ID, "temporary_hold", 2, "motivo", null, false, 5L);
  }
}
