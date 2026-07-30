package ar.com.hexium.hcop.infusion;

import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.infusion.InfusionRepository.Candidate;
import ar.com.hexium.hcop.infusion.InfusionRepository.Infusion;
import ar.com.hexium.hcop.infusion.InfusionRepository.Logistics;
import ar.com.hexium.hcop.infusion.InfusionRepository.Medication;
import ar.com.hexium.hcop.infusion.InfusionRepository.NewInfusion;
import ar.com.hexium.hcop.infusion.InfusionRepository.Patch;
import ar.com.hexium.hcop.infusion.InfusionRepository.ScheduleSettings;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowRepository.Key;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowRepository.ScheduleGate;
import ar.com.hexium.hcop.patient.PatientService;
import ar.com.hexium.hcop.treatment.DayHospitalApplicationPolicy;
import ar.com.hexium.hcop.treatment.TreatmentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class InfusionService {
  private static final Set<String> CLINICAL = Set.of(
      "planned", "checked_in", "ready", "in_progress", "observation", "paused", "completed", "cancelled");
  private static final Set<String> PHARMACY = Set.of(
      "not_required", "pending", "in_preparation", "ready", "released", "cancelled");
  private static final Set<String> ADMINISTRATION = Set.of(
      "not_started", "in_progress", "completed", "withheld", "cancelled");
  private final InfusionRepository infusions;
  private final TreatmentApplicationLogisticsService applicationLogistics;
  private final ApplicationWorkflowRepository applicationWorkflows;
  private final TreatmentRepository treatments;
  private final PatientService patients;
  private final ObjectMapper mapper;
  private final Clock clock;

  public InfusionService(
      InfusionRepository infusions,
      TreatmentApplicationLogisticsService applicationLogistics,
      ApplicationWorkflowRepository applicationWorkflows,
      TreatmentRepository treatments,
      PatientService patients,
      ObjectMapper mapper,
      Clock clock) {
    this.infusions = infusions;
    this.applicationLogistics = applicationLogistics;
    this.applicationWorkflows = applicationWorkflows;
    this.treatments = treatments;
    this.patients = patients;
    this.mapper = mapper;
    this.clock = clock;
  }

  public List<Map<String, Object>> list(Long patientId, LocalDate date) {
    applicationLogistics.synchronizeExistingTreatments();
    if (patientId != null) patients.require(patientId);
    return infusions.list(patientId, date).stream().map(this::view).toList();
  }

  public List<Map<String, Object>> candidates(String query, boolean includeScheduled) {
    return candidates(query, includeScheduled, true);
  }

  public List<Map<String, Object>> candidates(
      String query, boolean includeScheduled, boolean onlySchedulingEligible) {
    applicationLogistics.synchronizeExistingTreatments();
    applicationWorkflows.ensureWorkflowRows();
    return infusions.candidates(query, includeScheduled, onlySchedulingEligible).stream()
        .map(this::candidateView).toList();
  }

  @Transactional
  public Map<String, Object> create(JsonNode input, SessionPrincipal actor) {
    long patientId = positiveLong(input, "patientId", "idPaciente");
    String treatmentId = text(input, "treatmentId", "tratamiento");
    int cycle = boundedInt(input, 1, 500, 1, "cycleNumber", "ciclo");
    int applicationDay = boundedInt(
        input, 1, DayHospitalApplicationPolicy.MAX_APPLICATION_DAY, 1,
        "applicationDay", "diaAplicacion");
    patients.require(patientId);
    var treatment = treatments.find(patientId, treatmentId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tratamiento no encontrado."));
    if (cycle < treatment.initialCycle() ||
        cycle >= treatment.initialCycle() + treatment.cycleCount()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "El ciclo no pertenece al tratamiento.");
    }
    applicationLogistics.synchronizeTreatment(treatmentId);
    applicationWorkflows.ensureWorkflowRows();
    ScheduleGate scheduleGate =
        requireScheduleGate(patientId, treatmentId, cycle, applicationDay);
    Logistics logistics = infusions.logistics(patientId, treatmentId, cycle, applicationDay)
        .orElseThrow(() -> new ApiException(
            HttpStatus.BAD_REQUEST,
            "El día indicado no contiene una aplicación de medicación en Hospital de día."));
    Instant scheduled = instant(input, true, "scheduledAt", "fechaProgramada");
    ScheduleSettings scheduleSettings = infusions.scheduleSettings();
    String chair = normalizeChair(text(input, "chair", "sillon"), scheduleSettings);
    int duration = boundedInt(input, 1, 1440,
        logistics.durationMinutes(),
        "durationMinutes", "duracionMinutos");
    validateScheduling(scheduled, chair, duration, logistics.durationMinutes(), scheduleSettings);
    ObjectNode sourceRef = object(input.path("sourceRef"));
    ObjectNode scheduler = sourceRef.withObject("scheduler");
    scheduler.put("applicationDay", applicationDay);
    scheduler.put("durationSource", logistics.durationSource());
    scheduler.put("drugScheme", logistics.drugSummary());
    List<Medication> medicationRows = medications(input.path("medications"));
    if (medicationRows.isEmpty()) medicationRows = medications(logistics.applicationDrugs());
    String clinicalStatus = enumValue(input, CLINICAL, "planned", "clinicalStatus");
    String pharmacyStatus = enumValue(input, PHARMACY, "pending", "pharmacyStatus");
    String administrationStatus =
        enumValue(input, ADMINISTRATION, "not_started", "administrationStatus");
    if (!"planned".equals(clinicalStatus)
        || !"pending".equals(pharmacyStatus)
        || !"not_started".equals(administrationStatus)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "Un turno nuevo siempre comienza planificado; los estados avanzan desde el circuito por aplicación.",
          "APPLICATION_WORKFLOW_REQUIRED");
    }
    NewInfusion value = new NewInfusion(
        patientId, treatmentId, cycle, applicationDay, scheduled, chair, duration,
        clinicalStatus, pharmacyStatus, administrationStatus,
        input.path("appointmentConfirmed").asBoolean(false),
        text(input, "notes", "observaciones"),
        sourceRef,
        medicationRows);
    try {
      Infusion created = infusions.insert(value, actor.userId());
      recordAppointmentScheduled(scheduleGate, created, "appointment_scheduled", actor);
      return view(created);
    } catch (DataIntegrityViolationException conflict) {
      throw scheduleConflict(conflict);
    }
  }

  @Transactional
  public Map<String, Object> update(long id, JsonNode input, SessionPrincipal actor) {
    Infusion existing = require(id);
    boolean changesSchedule = changesSchedule(input);
    ScheduleGate scheduleGate = null;
    String requestedClinicalStatus = text(input, "clinicalStatus");
    boolean cancellingAppointment = "cancelled".equals(requestedClinicalStatus);
    String cancellationReason = cancellingAppointment
        ? text(input, "reason")
        : "";
    if (cancellingAppointment && cancellationReason.isBlank()) {
      cancellationReason = "Turno retirado de la agenda";
    }
    if (cancellationReason.length() > 500) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "El motivo para quitar el turno no puede superar 500 caracteres.");
    }
    if (!requestedClinicalStatus.isBlank()
        && !requestedClinicalStatus.equals(existing.clinicalStatus())
        && !"cancelled".equals(requestedClinicalStatus)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "Los estados clínicos avanzan únicamente desde el circuito por aplicación.",
          "APPLICATION_WORKFLOW_REQUIRED");
    }
    String requestedPharmacyStatus = text(input, "pharmacyStatus");
    if (!requestedPharmacyStatus.isBlank()
        && !requestedPharmacyStatus.equals(existing.pharmacyStatus())
        && !(cancellingAppointment && "cancelled".equals(requestedPharmacyStatus))) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "El estado de Farmacia se modifica desde su cola operativa.",
          "APPLICATION_WORKFLOW_REQUIRED");
    }
    String requestedAdministrationStatus = text(input, "administrationStatus");
    if (!requestedAdministrationStatus.isBlank()
        && !requestedAdministrationStatus.equals(existing.administrationStatus())
        && !(cancellingAppointment && "cancelled".equals(requestedAdministrationStatus))) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "El estado de administración se modifica desde su circuito seguro.",
          "APPLICATION_WORKFLOW_REQUIRED");
    }
    if (cancellingAppointment) {
      applicationLogistics.synchronizeTreatment(existing.treatmentId());
      applicationWorkflows.ensureWorkflowRows();
      var application = applicationWorkflows.lock(new Key(
              existing.patientId(), existing.treatmentId(),
              existing.cycleNumber(), existing.applicationDay()))
          .orElseThrow(() -> new ApiException(
              HttpStatus.CONFLICT,
              "La aplicación no ingresó al circuito seguro.",
              "APPLICATION_WORKFLOW_REQUIRED"));
      ApplicationWorkflowPolicy.cancelAppointment(application.policyState());
      scheduleGate = applicationWorkflows.scheduleGate(new Key(
              existing.patientId(), existing.treatmentId(),
              existing.cycleNumber(), existing.applicationDay()))
          .orElseThrow(() -> new ApiException(
              HttpStatus.CONFLICT,
              "La aplicación dejó de estar disponible durante la cancelación.",
              "VERSION_CONFLICT"));
    } else if (changesSchedule) {
      applicationLogistics.synchronizeTreatment(existing.treatmentId());
      applicationWorkflows.ensureWorkflowRows();
      scheduleGate = requireScheduleGate(
          existing.patientId(), existing.treatmentId(),
          existing.cycleNumber(), existing.applicationDay());
    }
    long expected = positiveLong(input, "expectedVersion", "revision");
    boolean placementChanged =
        input.has("scheduledAt") || input.has("chair") || input.has("durationMinutes");
    Instant requestedScheduledAt = instant(input, false, "scheduledAt");
    String requestedChair = input.has("chair") && !input.path("chair").isNull()
        ? input.path("chair").asText("") : null;
    Integer requestedDuration = optionalInt(input, 1, 1440, "durationMinutes");
    Boolean requestedConfirmation =
        input.has("appointmentConfirmed") ? input.path("appointmentConfirmed").asBoolean() : null;
    JsonNode requestedSourceRef = input.has("sourceRef") ? object(input.path("sourceRef")) : null;
    if (cancellingAppointment) {
      requestedConfirmation = false;
      requestedSourceRef = unconfirmedSourceRef(
          requestedSourceRef == null ? existing.sourceRef() : requestedSourceRef);
    } else if (placementChanged || Boolean.TRUE.equals(requestedConfirmation)) {
      Logistics logistics = infusions.logistics(
              existing.patientId(), existing.treatmentId(),
              existing.cycleNumber(), existing.applicationDay())
          .orElseThrow(() -> new ApiException(
              HttpStatus.CONFLICT, "La aplicación ya no posee una planificación válida."));
      ScheduleSettings scheduleSettings = infusions.scheduleSettings();
      Instant effectiveScheduledAt =
          input.has("scheduledAt") ? requestedScheduledAt : existing.scheduledAt();
      String effectiveChair = normalizeChair(
          input.has("chair") ? requestedChair : existing.chair(), scheduleSettings);
      int effectiveDuration = requestedDuration == null
          ? (existing.durationMinutes() == null
              ? logistics.durationMinutes() : existing.durationMinutes())
          : requestedDuration;
      validateScheduling(
          effectiveScheduledAt, effectiveChair, effectiveDuration,
          logistics.durationMinutes(), scheduleSettings);
      if (input.has("chair")) requestedChair = effectiveChair;
      if (placementChanged) {
        requestedConfirmation = false;
        requestedSourceRef = unconfirmedSourceRef(
            requestedSourceRef == null ? existing.sourceRef() : requestedSourceRef);
      }
    }
    Patch patch = new Patch(
        requestedScheduledAt,
        input.has("chair") && input.path("chair").isNull() ? "" : requestedChair,
        requestedDuration,
        optionalEnum(input, CLINICAL, "clinicalStatus"),
        optionalEnum(input, PHARMACY, "pharmacyStatus"),
        optionalEnum(input, ADMINISTRATION, "administrationStatus"),
        requestedConfirmation,
        input.has("notes") ? input.path("notes").asText("") : null,
        requestedSourceRef);
    try {
      Infusion updated = infusions.update(id, expected, patch, actor.userId())
          .orElseThrow(() -> new ApiException(
              HttpStatus.CONFLICT, "El turno fue modificado por otro usuario.", "VERSION_CONFLICT"));
      if (cancellingAppointment) {
        recordAppointmentRemoved(scheduleGate, updated, cancellationReason, actor);
      } else if (changesSchedule) {
        recordAppointmentScheduled(
            scheduleGate, updated, "appointment_updated", actor);
      }
      return view(updated);
    } catch (DataIntegrityViolationException conflict) {
      throw scheduleConflict(conflict);
    }
  }

  @Transactional
  public Map<String, Object> updateLogistics(
      long patientId, String treatmentId, int cycleNumber, int applicationDay,
      JsonNode input, SessionPrincipal actor) {
    patients.require(patientId);
    treatments.find(patientId, treatmentId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tratamiento no encontrado."));
    long expected = input.path("expectedVersion").asLong(0);
    applicationLogistics.synchronizeTreatment(treatmentId);
    Logistics current = infusions.logistics(
        patientId, treatmentId, cycleNumber, applicationDay)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Aplicación no encontrada."));
    if (expected == 0) expected = current.revision();
    String medication = text(input, "medicationState");
    if ("patient".equals(medication)) medication = "with_patient";
    String prescription = text(input, "prescriptionState");
    if (!medication.isBlank() || !prescription.isBlank()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "Medicación y prescripción se actualizan desde el circuito auditable por aplicación.",
          "APPLICATION_WORKFLOW_REQUIRED");
    }
    if (!medication.isBlank() && !Set.of("pending", "received", "with_patient").contains(medication)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "El estado de medicación es inválido.");
    }
    if (!prescription.isBlank() &&
        !Set.of("confirmed", "required", "requested", "rejected").contains(prescription)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "El estado de prescripción es inválido.");
    }
    LocalDate planned = localDate(input, "plannedDate");
    Logistics saved = infusions.updateLogistics(
        patientId, treatmentId, cycleNumber, applicationDay, expected,
        planned, medication, prescription,
        input.has("notes") ? input.path("notes").asText("") : null, actor.userId())
        .orElseThrow(() -> new ApiException(
            HttpStatus.CONFLICT, "La aplicación fue modificada por otro usuario.", "VERSION_CONFLICT"));
    return logisticsView(saved);
  }

  public Infusion require(long id) {
    return infusions.find(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Turno no encontrado."));
  }

  public Map<String, Object> view(Infusion infusion) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", Long.toString(infusion.id()));
    result.put("sessionId", Long.toString(infusion.id()));
    result.put("patientId", Long.toString(infusion.patientId()));
    result.put("treatmentId", infusion.treatmentId());
    result.put("cycleNumber", infusion.cycleNumber());
    result.put("applicationDay", infusion.applicationDay());
    result.put("applicationId", infusion.applicationId());
    result.put("scheduledAt", infusion.scheduledAt() == null ? null : infusion.scheduledAt().toString());
    result.put("chair", infusion.chair());
    result.put("durationMinutes", infusion.durationMinutes());
    result.put("clinicalStatus", infusion.clinicalStatus());
    result.put("pharmacyStatus", infusion.pharmacyStatus());
    result.put("administrationStatus", infusion.administrationStatus());
    result.put("appointmentConfirmed", infusion.appointmentConfirmed());
    result.put("notes", infusion.notes());
    result.put("sourceRef", infusion.sourceRef());
    result.put("revision", infusion.revision());
    result.put("version", infusion.revision());
    result.put("createdAt", infusion.createdAt().toString());
    result.put("updatedAt", infusion.updatedAt().toString());
    result.put("patientName", infusion.patientName());
    result.put("patientDni", infusion.patientDni());
    result.put("dni", infusion.patientDni());
    result.put("medicalRecord", infusion.medicalRecord());
    result.put("insurance", infusion.insurance());
    result.put("affiliateNumber", infusion.affiliateNumber());
    result.put("diagnosis", infusion.diagnosis());
    result.put("scheme", infusion.scheme());
    result.put("treatmentScheme", infusion.scheme());
    result.put("drugScheme", infusion.sourceRef().path("scheduler").path("drugScheme").asText(infusion.scheme()));
    result.put("treatmentType", infusion.treatmentType());
    result.put("totalCycles", infusion.totalCycles());
    result.put("cycleDays", infusion.cycleDays());
    boolean received = infusion.sourceRef().path("scheduler").path("medicationReceived").asBoolean(false);
    boolean withPatient = infusion.sourceRef().path("scheduler").path("medicationWithPatient").asBoolean(false);
    result.put("medicationReceived", received);
    result.put("medicationWithPatient", withPatient);
    result.put("prescriptionConfirmed",
        infusion.sourceRef().path("scheduler").path("prescriptionConfirmed").asBoolean(true));
    result.put("medications", infusions.medications(infusion.id()).stream().map(item -> Map.of(
        "id", Long.toString(item.id()),
        "drugId", item.drugId(),
        "drugName", item.drugName(),
        "prescribedDoseText", item.prescribedDoseText(),
        "doseUnit", item.doseUnit(),
        "route", item.route(),
        "preparationStatus", item.preparationStatus(),
        "administrationStatus", item.administrationStatus(),
        "notes", item.notes(),
        "revision", item.revision())).toList());
    return result;
  }

  private Map<String, Object> candidateView(Candidate item) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", item.patientId() + ":" + item.treatmentId() + ":" +
        item.cycleNumber() + ":" + item.applicationDay());
    result.put("patientId", Long.toString(item.patientId()));
    result.put("treatmentId", item.treatmentId());
    result.put("cycleNumber", item.cycleNumber());
    result.put("applicationDay", item.applicationDay());
    result.put("suggestedDate", item.plannedDate() == null ? "" : item.plannedDate().toString());
    String medication = "with_patient".equals(item.medicationState()) ? "patient" : item.medicationState();
    result.put("medicationState", medication);
    result.put("medicationReceived", "received".equals(item.medicationState()));
    result.put("medicationWithPatient", "with_patient".equals(item.medicationState()));
    result.put("prescriptionState", item.prescriptionState());
    result.put("prescriptionWorkflowState", item.prescriptionState());
    result.put("prescriptionConfirmed", "confirmed".equals(item.prescriptionState()));
    result.put("logisticsRevision", item.logisticsRevision());
    result.put("patientName", item.patientName());
    result.put("patientDni", item.patientDni());
    result.put("dni", item.patientDni());
    result.put("medicalRecord", item.medicalRecord());
    result.put("insurance", item.insurance());
    result.put("affiliateNumber", item.affiliateNumber());
    result.put("diagnosis", item.diagnosis());
    result.put("scheme", item.scheme());
    result.put("drugScheme", item.drugSummary().isBlank() ? item.scheme() : item.drugSummary());
    result.put("applicationDrugs", item.applicationDrugs());
    result.put("medications", item.applicationDrugs());
    result.put("treatmentType", item.treatmentType());
    result.put("totalCycles", item.totalCycles());
    result.put("cycleDays", item.cycleDays());
    result.put("durationMinutes", item.durationMinutes());
    result.put("durationSource", item.durationSource());
    result.put("pharmacyValidationStatus", item.pharmacyValidationStatus());
    result.put("medicationSource", item.medicationSource());
    result.put("patientMustBringMedication", "patient_to_bring".equals(item.medicationSource()));
    result.put("stockReservationStatus", item.stockReservationStatus());
    result.put("applicationWorkflowStatus", item.applicationWorkflowStatus());
    result.put("applicationWorkflowRevision", item.applicationWorkflowRevision());
    boolean schedulingEligible = "approved".equals(item.pharmacyValidationStatus())
        && (("center_stock".equals(item.medicationSource())
              && "reserved".equals(item.stockReservationStatus()))
            || Set.of("patient_to_bring", "patient_has_medication", "received_center")
                .contains(item.medicationSource()));
    result.put("schedulingEligible", schedulingEligible);
    result.put("workflowStatus", item.continuityStatus());
    result.put("continuityState", item.continuityStatus());
    result.put("effectiveFromCycle", item.effectiveFromCycle());
    result.put("suspensionReason", item.suspensionReason());
    result.put("resumeDate", item.resumeDate() == null ? null : item.resumeDate().toString());
    result.put("prescriptionRequired", item.prescriptionRequired());
    result.put("managementRevision", item.managementRevision());
    result.put("pendingRequestIds", Map.of());
    return result;
  }

  private Map<String, Object> logisticsView(Logistics logistics) {
    String state = "with_patient".equals(logistics.medicationState()) ? "patient" : logistics.medicationState();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("patientId", Long.toString(logistics.patientId()));
    result.put("treatmentId", logistics.treatmentId());
    result.put("cycleNumber", logistics.cycleNumber());
    result.put("applicationDay", logistics.applicationDay());
    result.put("plannedDate", logistics.plannedDate() == null ? null : logistics.plannedDate().toString());
    result.put("medicationState", state);
    result.put("medicationReceived", "received".equals(logistics.medicationState()));
    result.put("medicationWithPatient", "with_patient".equals(logistics.medicationState()));
    result.put("prescriptionState", logistics.prescriptionState());
    result.put("durationMinutes", logistics.durationMinutes());
    result.put("durationSource", logistics.durationSource());
    result.put("drugScheme", logistics.drugSummary());
    result.put("applicationDrugs", logistics.applicationDrugs());
    result.put("notes", logistics.notes());
    result.put("revision", logistics.revision());
    result.put("updatedAt", logistics.updatedAt().toString());
    return result;
  }

  private List<Medication> medications(JsonNode node) {
    if (!node.isArray()) return List.of();
    List<Medication> result = new ArrayList<>();
    for (JsonNode item : node) {
      String name = text(item, "drugName", "droga", "name");
      if (name.isBlank()) continue;
      result.add(new Medication(
          text(item, "sourceItemRef"), text(item, "drugId", "idDroga"), name,
          text(item, "prescribedDoseText", "dosis", "dosisDiaria"),
          text(item, "doseUnit", "unidad"),
          text(item, "route", "via", "viaAdministracion"),
          enumValue(item, PHARMACY, "pending", "preparationStatus"),
          enumValue(item, ADMINISTRATION, "not_started", "administrationStatus"),
          text(item, "notes", "observaciones")));
    }
    return result;
  }

  private ApiException scheduleConflict(DataIntegrityViolationException conflict) {
    return new ApiException(
        HttpStatus.CONFLICT,
        "El sillón ya está ocupado en ese horario.",
        "CHAIR_SCHEDULE_CONFLICT");
  }

  ScheduleGate requireScheduleGate(
      long patientId, String treatmentId, int cycleNumber, int applicationDay) {
    var gate = applicationWorkflows.scheduleGate(
            new Key(patientId, treatmentId, cycleNumber, applicationDay))
        .orElseThrow(() -> new ApiException(
            HttpStatus.CONFLICT,
            "La aplicación todavía no ingresó al circuito de Farmacia.",
            "PHARMACY_GATE_REQUIRED"));
    ApplicationWorkflowPolicy.schedule(
        gate.prescriptionStatus(), gate.continuityStatus(), gate.prescriptionRequired(),
        gate.pharmacyValidationStatus(), gate.medicationSource(),
        gate.stockReservationStatus(), gate.clinicalAuthorizationStatus(),
        gate.preparationStatus(), gate.administrationStatus());
    return gate;
  }

  private void recordAppointmentScheduled(
      ScheduleGate before, Infusion appointment, String action, SessionPrincipal actor) {
    Key key = new Key(
        appointment.patientId(), appointment.treatmentId(),
        appointment.cycleNumber(), appointment.applicationDay());
    Instant now = clock.instant();
    if (!applicationWorkflows.markAppointmentScheduled(
        key, before.revision(), actor.userId(), now)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "La aplicación fue modificada mientras se asignaba el turno.",
          "VERSION_CONFLICT");
    }
    ScheduleGate after = applicationWorkflows.scheduleGate(key)
        .orElseThrow(() -> new ApiException(
            HttpStatus.CONFLICT,
            "La aplicación dejó de estar disponible durante el agendamiento.",
            "VERSION_CONFLICT"));
    String auditedAction = "failed".equals(before.clinicalAuthorizationStatus())
        ? "appointment_rescheduled_after_clinical_fail"
        : action;
    String idempotencyKey =
        "appointment-" + appointment.id() + "-revision-" + appointment.revision();
    applicationWorkflows.insertEvent(
        key, auditedAction, idempotencyKey, actor.userId(),
        before.revision(), after.revision(),
        appointmentCommand(appointment), workflowSnapshot(before),
        workflowSnapshot(after), now);
  }

  private void recordAppointmentRemoved(
      ScheduleGate before, Infusion appointment, String reason, SessionPrincipal actor) {
    Key key = new Key(
        appointment.patientId(), appointment.treatmentId(),
        appointment.cycleNumber(), appointment.applicationDay());
    Instant now = clock.instant();
    if (!applicationWorkflows.markAppointmentRemoved(
        key, before.revision(), actor.userId(), now)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "La aplicación avanzó mientras se quitaba el turno. Recargue antes de continuar.",
          "VERSION_CONFLICT");
    }
    ScheduleGate after = applicationWorkflows.scheduleGate(key)
        .orElseThrow(() -> new ApiException(
            HttpStatus.CONFLICT,
            "La aplicación dejó de estar disponible durante la cancelación.",
            "VERSION_CONFLICT"));
    ObjectNode command = appointmentCommand(appointment);
    command.put("reason", reason);
    applicationWorkflows.insertEvent(
        key, "appointment_cancelled",
        "appointment-cancelled-" + appointment.id() + "-revision-" + appointment.revision(),
        actor.userId(), before.revision(), after.revision(),
        command, workflowSnapshot(before),
        workflowSnapshot(after), now);
  }

  private ObjectNode appointmentCommand(Infusion appointment) {
    ObjectNode command = mapper.createObjectNode();
    command.put("sessionId", appointment.id());
    command.put("scheduledAt",
        appointment.scheduledAt() == null ? "" : appointment.scheduledAt().toString());
    command.put("chair", appointment.chair());
    if (appointment.durationMinutes() != null) {
      command.put("durationMinutes", appointment.durationMinutes());
    }
    command.put("appointmentConfirmed", appointment.appointmentConfirmed());
    return command;
  }

  private ObjectNode workflowSnapshot(ScheduleGate gate) {
    ObjectNode snapshot = mapper.createObjectNode();
    snapshot.put("workflowStatus", gate.workflowStatus());
    snapshot.put("prescriptionStatus", gate.prescriptionStatus());
    snapshot.put("continuityStatus", gate.continuityStatus());
    snapshot.put("prescriptionRequired", gate.prescriptionRequired());
    snapshot.put("clinicalAuthorizationStatus", gate.clinicalAuthorizationStatus());
    snapshot.put("clinicalAuthorizationReason", gate.clinicalAuthorizationReason());
    snapshot.set("clinicalAssessment",
        gate.clinicalAssessment() == null
            ? mapper.createObjectNode()
            : gate.clinicalAssessment().deepCopy());
    snapshot.put("preparationStatus", gate.preparationStatus());
    snapshot.put("administrationStatus", gate.administrationStatus());
    snapshot.put("revision", gate.revision());
    return snapshot;
  }

  private boolean changesSchedule(JsonNode input) {
    return input.has("scheduledAt")
        || input.has("chair")
        || input.has("durationMinutes")
        || input.has("appointmentConfirmed");
  }

  private String normalizeChair(String raw, ScheduleSettings settings) {
    String value = raw == null ? "" : raw.trim();
    String number = value.replaceFirst("(?i)^sill[oó]n\\s*", "").trim();
    if (!number.matches("\\d+")) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "Seleccione un sillón válido entre 1 y " + settings.chairCount() + ".");
    }
    int chair = Integer.parseInt(number);
    if (chair < 1 || chair > settings.chairCount()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "El sillón debe estar entre 1 y " + settings.chairCount() + ".");
    }
    return Integer.toString(chair);
  }

  private void validateScheduling(
      Instant scheduledAt, String chair, int durationMinutes, int plannedDurationMinutes,
      ScheduleSettings settings) {
    if (scheduledAt == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Informe fecha y hora del turno.");
    }
    normalizeChair(chair, settings);
    if (plannedDurationMinutes > 0 && durationMinutes != plannedDurationMinutes) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "La duración del turno debe ser la calculada para esta aplicación: "
              + plannedDurationMinutes + " minutos.",
          "SCHEDULE_DURATION_MISMATCH");
    }
    var local = scheduledAt.atZone(clock.getZone());
    if (local.toLocalDate().isBefore(LocalDate.now(clock))) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "No se puede asignar un turno en una fecha pasada.");
    }
    LocalTime start;
    LocalTime end;
    try {
      start = LocalTime.parse(settings.startTime());
      end = LocalTime.parse(settings.endTime());
    } catch (DateTimeParseException invalidConfiguration) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "La jornada de Hospital de día está mal configurada.",
          "INVALID_DAY_HOSPITAL_SETTINGS");
    }
    int minute = local.getHour() * 60 + local.getMinute();
    int startMinute = start.getHour() * 60 + start.getMinute();
    int endMinute = end.getHour() * 60 + end.getMinute();
    int occupiedMinutes =
        ((durationMinutes + settings.slotMinutes() - 1) / settings.slotMinutes())
            * settings.slotMinutes();
    if (endMinute <= startMinute
        || minute < startMinute
        || minute + occupiedMinutes > endMinute) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "El turno debe entrar completo dentro de la jornada "
              + settings.startTime() + "–" + settings.endTime() + ".",
          "OUTSIDE_DAY_HOSPITAL_HOURS");
    }
    if (local.getSecond() != 0 || local.getNano() != 0
        || (minute - startMinute) % settings.slotMinutes() != 0) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "El horario debe coincidir con casilleros de "
              + settings.slotMinutes() + " minutos.",
          "SCHEDULE_SLOT_MISMATCH");
    }
  }

  private ObjectNode unconfirmedSourceRef(JsonNode source) {
    ObjectNode copy = object(source);
    ObjectNode scheduler = copy.withObject("scheduler");
    scheduler.put("appointmentConfirmed", false);
    scheduler.putNull("appointmentConfirmedAt");
    return copy;
  }

  private ObjectNode object(JsonNode node) {
    return node != null && node.isObject()
        ? (ObjectNode) node.deepCopy() : mapper.createObjectNode();
  }

  private String enumValue(JsonNode input, Set<String> allowed, String fallback, String key) {
    String value = text(input, key);
    if (value.isBlank()) return fallback;
    if (!allowed.contains(value)) throw new ApiException(HttpStatus.BAD_REQUEST, "El estado informado es inválido.");
    return value;
  }

  private String optionalEnum(JsonNode input, Set<String> allowed, String key) {
    if (!input.has(key)) return null;
    return enumValue(input, allowed, "", key);
  }

  private Integer optionalInt(JsonNode input, int min, int max, String key) {
    if (!input.has(key) || input.path(key).isNull()) return null;
    return boundedInt(input, min, max, min, key);
  }

  private int boundedInt(JsonNode input, int min, int max, int fallback, String... keys) {
    String text = text(input, keys);
    if (text.isBlank()) return fallback;
    try {
      int value = Integer.parseInt(text);
      if (value < min || value > max) throw new NumberFormatException();
      return value;
    } catch (NumberFormatException invalid) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Un valor numérico es inválido.");
    }
  }

  private long positiveLong(JsonNode input, String... keys) {
    String value = text(input, keys);
    try {
      long parsed = Long.parseLong(value);
      if (parsed < 1) throw new NumberFormatException();
      return parsed;
    } catch (NumberFormatException invalid) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Falta un identificador válido.");
    }
  }

  private Instant instant(JsonNode input, boolean required, String... keys) {
    boolean explicitNull = false;
    for (String key : keys) explicitNull |= input.has(key) && input.path(key).isNull();
    String value = text(input, keys);
    if (value.isBlank()) {
      if (required && !explicitNull) throw new ApiException(HttpStatus.BAD_REQUEST, "Informe fecha y hora.");
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException invalid) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "La fecha y hora son inválidas.");
    }
  }

  private LocalDate localDate(JsonNode input, String key) {
    String value = text(input, key);
    if (value.isBlank()) return null;
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException invalid) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "La fecha planificada es inválida.");
    }
  }

  private String optionalText(JsonNode node, String key) {
    return node.has(key) ? node.path(key).asText("") : null;
  }

  private String text(JsonNode node, String... keys) {
    for (String key : keys) {
      JsonNode value = node.path(key);
      if (!value.isMissingNode() && !value.isNull()) {
        String text = value.asText("").trim();
        if (!text.isBlank()) return text;
      }
    }
    return "";
  }

}
