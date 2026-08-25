package ar.com.hexium.hcop.infusion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowPolicy.State;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowRepository.Application;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowRepository.Key;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowRepository.ScheduleGate;
import ar.com.hexium.hcop.infusion.InfusionRepository.Infusion;
import ar.com.hexium.hcop.infusion.InfusionRepository.Logistics;
import ar.com.hexium.hcop.infusion.InfusionRepository.Patch;
import ar.com.hexium.hcop.infusion.InfusionRepository.ScheduleSettings;
import ar.com.hexium.hcop.patient.PatientService;
import ar.com.hexium.hcop.treatment.TreatmentRepository;
import ar.com.hexium.hcop.treatment.TreatmentRepository.Treatment;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class InfusionServiceSchedulingWorkflowTest {
  private static final Instant NOW = Instant.parse("2026-07-29T15:00:00Z");
  private final InfusionRepository infusions = mock(InfusionRepository.class);
  private final TreatmentApplicationLogisticsService logistics =
      mock(TreatmentApplicationLogisticsService.class);
  private final ApplicationWorkflowRepository workflows =
      mock(ApplicationWorkflowRepository.class);
  private final TreatmentRepository treatments = mock(TreatmentRepository.class);
  private final PatientService patients = mock(PatientService.class);
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final InfusionService service = new InfusionService(
      infusions, logistics, workflows, treatments, patients,
      mapper, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void reschedulingAfterFailReopensTriageAndAuditsThePreviousAssessment() {
    Key key = new Key(9, "tx-1", 2, 8);
    JsonNode previousAssessment = mapper.createObjectNode()
        .put("decision", "FAIL")
        .put("reason", "Neutropenia");
    ScheduleGate failed = gate(
        "failed", "postponed", previousAssessment, 8);
    ScheduleGate pending = gate(
        "pending", "triage_pending", mapper.createObjectNode(), 9);
    Infusion existing = infusion(
        Instant.parse("2026-07-29T13:00:00Z"), "Sillón 1", 3);
    Infusion moved = infusion(
        Instant.parse("2026-07-30T13:30:00Z"), "Sillón 2", 4);
    SessionPrincipal actor = actor();

    when(infusions.find(7)).thenReturn(Optional.of(existing));
    Logistics plannedApplication = mock(Logistics.class);
    when(plannedApplication.durationMinutes()).thenReturn(90);
    when(infusions.logistics(9, "tx-1", 2, 8))
        .thenReturn(Optional.of(plannedApplication));
    when(infusions.scheduleSettings())
        .thenReturn(new ScheduleSettings(6, 10, "08:00", "16:00"));
    when(workflows.scheduleGate(key))
        .thenReturn(Optional.of(failed), Optional.of(pending));
    when(workflows.markAppointmentScheduled(key, 8, actor.userId(), NOW))
        .thenReturn(true);
    when(infusions.update(eq(7L), eq(3L), any(Patch.class), eq(actor.userId())))
        .thenReturn(Optional.of(moved));
    when(infusions.medications(7)).thenReturn(List.of());

    JsonNode input = mapper.createObjectNode()
        .put("expectedVersion", 3)
        .put("scheduledAt", "2026-07-30T13:30:00Z")
        .put("chair", "Sillón 2")
        .put("durationMinutes", 90)
        .put("appointmentConfirmed", true);

    service.update(7, input, actor);

    ArgumentCaptor<Patch> savedPatch = ArgumentCaptor.forClass(Patch.class);
    verify(infusions).update(eq(7L), eq(3L), savedPatch.capture(), eq(actor.userId()));
    assertThat(savedPatch.getValue().appointmentConfirmed()).isFalse();
    assertThat(savedPatch.getValue().sourceRef()
        .path("scheduler").path("appointmentConfirmed").asBoolean(true)).isFalse();

    ArgumentCaptor<JsonNode> before = ArgumentCaptor.forClass(JsonNode.class);
    ArgumentCaptor<JsonNode> after = ArgumentCaptor.forClass(JsonNode.class);
    verify(workflows).insertEvent(
        eq(key),
        eq("appointment_rescheduled_after_clinical_fail"),
        eq("appointment-7-revision-4"),
        eq(actor.userId()),
        eq(8L),
        eq(9L),
        any(JsonNode.class),
        before.capture(),
        after.capture(),
        eq(NOW));
    assertThat(before.getValue().path("clinicalAssessment").path("reason").asText())
        .isEqualTo("Neutropenia");
    assertThat(after.getValue().path("clinicalAuthorizationStatus").asText())
        .isEqualTo("pending");
    assertThat(after.getValue().path("clinicalAssessment").isEmpty()).isTrue();
  }

  @Test
  void removingAnAppointmentPreservesPharmacyAndAdministrationAndAuditsTheReason() {
    Key key = new Key(9, "tx-1", 2, 8);
    Infusion existing = infusion(
        Instant.parse("2026-07-30T13:00:00Z"), "Sillón 1", 3);
    Infusion removed = new Infusion(
        7, 9, "tx-1", 2, 8, null,
        null, "", 90, "cancelled", "pending", "not_started", false,
        "", mapper.createObjectNode(), 4, NOW, NOW,
        "30111222", "HC-9", "Ana", "Paciente",
        "Obra social", "1234", "Diagnóstico", "Esquema",
        "Oncológico", 6, 21);
    Application application = mock(Application.class);
    when(application.policyState()).thenReturn(new State(
        "scheduled", "confirmed", "center_stock", "approved", "reserved",
        "pending", "not_started", "not_started"));
    ScheduleGate before = gate(
        "pending", "scheduled", mapper.createObjectNode(), 8);
    ScheduleGate after = gate(
        "pending", "medication_ready", mapper.createObjectNode(), 9);

    when(infusions.find(7)).thenReturn(Optional.of(existing));
    when(workflows.lock(key)).thenReturn(Optional.of(application));
    when(workflows.scheduleGate(key)).thenReturn(Optional.of(before), Optional.of(after));
    when(workflows.markAppointmentRemoved(key, 8, actor().userId(), NOW)).thenReturn(true);
    when(infusions.update(eq(7L), eq(3L), any(Patch.class), eq(actor().userId())))
        .thenReturn(Optional.of(removed));
    when(infusions.medications(7)).thenReturn(List.of());

    JsonNode input = mapper.createObjectNode()
        .put("expectedVersion", 3)
        .putNull("scheduledAt")
        .putNull("chair")
        .put("clinicalStatus", "cancelled")
        .put("reason", "Paciente solicitó reprogramar");

    service.update(7, input, actor());

    ArgumentCaptor<Patch> savedPatch = ArgumentCaptor.forClass(Patch.class);
    verify(infusions).update(eq(7L), eq(3L), savedPatch.capture(), eq(actor().userId()));
    assertThat(savedPatch.getValue().clinicalStatus()).isEqualTo("cancelled");
    assertThat(savedPatch.getValue().pharmacyStatus()).isNull();
    assertThat(savedPatch.getValue().administrationStatus()).isNull();

    ArgumentCaptor<JsonNode> command = ArgumentCaptor.forClass(JsonNode.class);
    verify(workflows).insertEvent(
        eq(key), eq("appointment_cancelled"), any(), eq(actor().userId()),
        eq(8L), eq(9L), command.capture(), any(), any(), eq(NOW));
    assertThat(command.getValue().path("reason").asText())
        .isEqualTo("Paciente solicitó reprogramar");
  }

  @Test
  void rejectsADurationDifferentFromTheApplicationPlan() {
    arrangeSchedulableApplication(new ScheduleSettings(6, 10, "08:00", "16:00"));
    JsonNode input = scheduleInput("2026-07-30T13:00:00Z", "2", 80);

    assertThatThrownBy(() -> service.create(input, actor()))
        .isInstanceOfSatisfying(ApiException.class, error ->
            assertThat(error.code()).isEqualTo("SCHEDULE_DURATION_MISMATCH"));

    verify(infusions, never()).insert(any(), anyLong());
  }

  @Test
  void rejectsAChairOutsideTheConfiguredRange() {
    arrangeSchedulableApplication(new ScheduleSettings(6, 10, "08:00", "16:00"));
    JsonNode input = scheduleInput("2026-07-30T13:00:00Z", "Sillon 7", 90);

    assertThatThrownBy(() -> service.create(input, actor()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("entre 1 y 6");

    verify(infusions, never()).insert(any(), anyLong());
  }

  @Test
  void rejectsAnAppointmentThatWouldEndAfterTheWorkday() {
    arrangeSchedulableApplication(new ScheduleSettings(6, 10, "08:00", "16:00"));
    JsonNode input = scheduleInput("2026-07-30T15:00:00Z", "2", 90);

    assertThatThrownBy(() -> service.create(input, actor()))
        .isInstanceOfSatisfying(ApiException.class, error ->
            assertThat(error.code()).isEqualTo("OUTSIDE_DAY_HOSPITAL_HOURS"));

    verify(infusions, never()).insert(any(), anyLong());
  }

  @Test
  void rejectsAnAppointmentThatDoesNotStartOnASlotBoundary() {
    arrangeSchedulableApplication(new ScheduleSettings(6, 10, "08:00", "16:00"));
    JsonNode input = scheduleInput("2026-07-30T13:05:00Z", "2", 90);

    assertThatThrownBy(() -> service.create(input, actor()))
        .isInstanceOfSatisfying(ApiException.class, error ->
            assertThat(error.code()).isEqualTo("SCHEDULE_SLOT_MISMATCH"));

    verify(infusions, never()).insert(any(), anyLong());
  }

  @Test
  void serviceRejectsMovingAnAppointmentAfterPass() {
    Key key = new Key(9, "tx-1", 2, 8);
    when(workflows.scheduleGate(key)).thenReturn(Optional.of(new ScheduleGate(
        "confirmed", "active", false,
        "approved", "center_stock", "reserved",
        "passed", "", mapper.createObjectNode(),
        "not_started", "not_started", "clinically_authorized", 5)));

    assertThatThrownBy(() -> service.requireScheduleGate(9, "tx-1", 2, 8))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("etapa clínica");
  }

  private ScheduleGate gate(
      String clinicalStatus, String workflowStatus, JsonNode assessment, long revision) {
    return new ScheduleGate(
        "confirmed", "active", false,
        "approved", "center_stock", "reserved",
        clinicalStatus, "Neutropenia", assessment,
        "not_started", "not_started", workflowStatus, revision);
  }

  private Infusion infusion(Instant scheduledAt, String chair, long revision) {
    return new Infusion(
        7, 9, "tx-1", 2, 8, null,
        scheduledAt, chair, 90, "planned", "pending", "not_started", true,
        "", mapper.createObjectNode(), revision, NOW, NOW,
        "30111222", "HC-9", "Ana", "Paciente",
        "Obra social", "1234", "Diagnóstico", "Esquema",
        "Oncológico", 6, 21);
  }

  private void arrangeSchedulableApplication(ScheduleSettings settings) {
    Treatment treatment = new Treatment(
        "tx-1", 9, "diagnosis-1",
        LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1),
        1, 6, 21, "Oncologico", "Paliativo",
        "Diagnostico", "scheme-1", "Esquema", "Oncologo",
        "Iniciado", "Pendiente", false, 90,
        mapper.createObjectNode(), 1, NOW, NOW);
    Logistics planned = new Logistics(
        9, "tx-1", 1, 1, LocalDate.of(2026, 7, 30),
        "pending", "confirmed", 90, "protocol", "Droga de prueba",
        mapper.createArrayNode(), "", 1, NOW);
    when(treatments.find(9, "tx-1")).thenReturn(Optional.of(treatment));
    when(workflows.scheduleGate(new Key(9, "tx-1", 1, 1)))
        .thenReturn(Optional.of(new ScheduleGate(
            "confirmed", "active", false,
            "approved", "center_stock", "reserved",
            "pending", "", mapper.createObjectNode(),
            "not_started", "not_started", "scheduled", 1)));
    when(infusions.logistics(9, "tx-1", 1, 1)).thenReturn(Optional.of(planned));
    when(infusions.scheduleSettings()).thenReturn(settings);
  }

  private JsonNode scheduleInput(String scheduledAt, String chair, int durationMinutes) {
    return mapper.createObjectNode()
        .put("patientId", 9)
        .put("treatmentId", "tx-1")
        .put("cycleNumber", 1)
        .put("applicationDay", 1)
        .put("scheduledAt", scheduledAt)
        .put("chair", chair)
        .put("durationMinutes", durationMinutes);
  }

  private SessionPrincipal actor() {
    return new SessionPrincipal(
        22, "admisiones", "", "Admisiones", "", "",
        true, null, List.of(), Set.of("application.schedule.manage"));
  }
}
