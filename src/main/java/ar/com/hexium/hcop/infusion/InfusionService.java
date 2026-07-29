package ar.com.hexium.hcop.infusion;

import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.catalog.TreatmentCatalogService;
import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.infusion.InfusionRepository.Candidate;
import ar.com.hexium.hcop.infusion.InfusionRepository.Infusion;
import ar.com.hexium.hcop.infusion.InfusionRepository.Logistics;
import ar.com.hexium.hcop.infusion.InfusionRepository.Medication;
import ar.com.hexium.hcop.infusion.InfusionRepository.NewInfusion;
import ar.com.hexium.hcop.infusion.InfusionRepository.Patch;
import ar.com.hexium.hcop.patient.PatientDocumentService;
import ar.com.hexium.hcop.patient.PatientDocumentService.EvolutionAppend;
import ar.com.hexium.hcop.patient.PatientService;
import ar.com.hexium.hcop.treatment.TreatmentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
  private final TreatmentRepository treatments;
  private final TreatmentCatalogService treatmentCatalog;
  private final PatientService patients;
  private final PatientDocumentService documents;
  private final ObjectMapper mapper;
  private final Clock clock;

  public InfusionService(
      InfusionRepository infusions,
      TreatmentRepository treatments,
      TreatmentCatalogService treatmentCatalog,
      PatientService patients,
      PatientDocumentService documents,
      ObjectMapper mapper,
      Clock clock) {
    this.infusions = infusions;
    this.treatments = treatments;
    this.treatmentCatalog = treatmentCatalog;
    this.patients = patients;
    this.documents = documents;
    this.mapper = mapper;
    this.clock = clock;
  }

  public List<Map<String, Object>> list(Long patientId, LocalDate date) {
    if (patientId != null) patients.require(patientId);
    return infusions.list(patientId, date).stream().map(this::view).toList();
  }

  public List<Map<String, Object>> candidates(String query) {
    return infusions.candidates(query).stream().map(this::candidateView).toList();
  }

  @Transactional
  public Map<String, Object> create(JsonNode input, SessionPrincipal actor) {
    long patientId = positiveLong(input, "patientId", "idPaciente");
    String treatmentId = text(input, "treatmentId", "tratamiento");
    int cycle = boundedInt(input, 1, 500, 1, "cycleNumber", "ciclo");
    patients.require(patientId);
    var treatment = treatments.find(patientId, treatmentId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tratamiento no encontrado."));
    if (cycle < treatment.initialCycle() ||
        cycle >= treatment.initialCycle() + treatment.cycleCount()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "El ciclo no pertenece al tratamiento.");
    }
    Instant scheduled = instant(input, true, "scheduledAt", "fechaProgramada");
    String chair = text(input, "chair", "sillon");
    if (chair.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Seleccione un sillón.");
    int duration = boundedInt(input, 1, 1440,
        resolvedDuration(treatment.schemeId(), treatment.durationMinutes()),
        "durationMinutes", "duracionMinutos");
    NewInfusion value = new NewInfusion(
        patientId, treatmentId, cycle, scheduled, chair, duration,
        enumValue(input, CLINICAL, "planned", "clinicalStatus"),
        enumValue(input, PHARMACY, "pending", "pharmacyStatus"),
        enumValue(input, ADMINISTRATION, "not_started", "administrationStatus"),
        input.path("appointmentConfirmed").asBoolean(false),
        text(input, "notes", "observaciones"),
        object(input.path("sourceRef")),
        medications(input.path("medications")));
    try {
      return view(infusions.insert(value, actor.userId()));
    } catch (DataIntegrityViolationException conflict) {
      throw scheduleConflict(conflict);
    }
  }

  @Transactional
  public Map<String, Object> update(long id, JsonNode input, SessionPrincipal actor) {
    Infusion existing = require(id);
    long expected = positiveLong(input, "expectedVersion", "revision");
    Patch patch = new Patch(
        instant(input, false, "scheduledAt"),
        input.has("chair") && input.path("chair").isNull() ? "" : optionalText(input, "chair"),
        optionalInt(input, 1, 1440, "durationMinutes"),
        optionalEnum(input, CLINICAL, "clinicalStatus"),
        optionalEnum(input, PHARMACY, "pharmacyStatus"),
        optionalEnum(input, ADMINISTRATION, "administrationStatus"),
        input.has("appointmentConfirmed") ? input.path("appointmentConfirmed").asBoolean() : null,
        input.has("notes") ? input.path("notes").asText("") : null,
        input.has("sourceRef") ? object(input.path("sourceRef")) : null);
    try {
      Infusion updated = infusions.update(id, expected, patch, actor.userId())
          .orElseThrow(() -> new ApiException(
              HttpStatus.CONFLICT, "El turno fue modificado por otro usuario.", "VERSION_CONFLICT"));
      return view(updated);
    } catch (DataIntegrityViolationException conflict) {
      throw scheduleConflict(conflict);
    }
  }

  @Transactional
  public Map<String, Object> updateLogistics(
      long patientId, String treatmentId, int cycleNumber, JsonNode input, SessionPrincipal actor) {
    patients.require(patientId);
    treatments.find(patientId, treatmentId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tratamiento no encontrado."));
    long expected = input.path("expectedVersion").asLong(0);
    Logistics current = infusions.logistics(patientId, treatmentId, cycleNumber)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ciclo no encontrado."));
    if (expected == 0) expected = current.revision();
    String medication = text(input, "medicationState");
    if ("patient".equals(medication)) medication = "with_patient";
    if (!medication.isBlank() && !Set.of("pending", "received", "with_patient").contains(medication)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "El estado de medicación es inválido.");
    }
    String prescription = text(input, "prescriptionState");
    if (!prescription.isBlank() &&
        !Set.of("confirmed", "required", "requested", "rejected").contains(prescription)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "El estado de prescripción es inválido.");
    }
    LocalDate planned = localDate(input, "plannedDate");
    Logistics saved = infusions.updateLogistics(
        patientId, treatmentId, cycleNumber, expected, planned, medication, prescription,
        input.has("notes") ? input.path("notes").asText("") : null, actor.userId())
        .orElseThrow(() -> new ApiException(
            HttpStatus.CONFLICT, "El ciclo fue modificado por otro usuario.", "VERSION_CONFLICT"));
    return logisticsView(saved);
  }

  @Transactional
  public Finalization finalizeInfusion(long id, JsonNode input, SessionPrincipal actor) {
    if (!input.path("confirmed").asBoolean(false)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Confirme la administración.");
    }
    String observation = text(input, "observation", "observacion");
    if (observation.length() < 3) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Escriba una observación de al menos 3 caracteres.");
    }
    Infusion current = require(id);
    if ("completed".equals(current.clinicalStatus()) &&
        "completed".equals(current.administrationStatus())) {
      return new Finalization(view(current), null, null, true);
    }
    long expected = positiveLong(input, "expectedVersion", "revision");
    Patch patch = new Patch(
        null, null, null, "completed", current.pharmacyStatus(), "completed",
        null, observation, current.sourceRef());
    Infusion completed = infusions.update(id, expected, patch, actor.userId())
        .orElseThrow(() -> new ApiException(
            HttpStatus.CONFLICT, "La aplicación fue modificada por otro usuario.", "VERSION_CONFLICT"));
    ObjectNode evolution = mapper.createObjectNode();
    evolution.put("id", "infusion-completion-" + completed.id());
    evolution.put("date", LocalDate.now(clock).toString());
    evolution.put("datePrecision", "day");
    evolution.put("author", actor.displayName());
    evolution.put("reason", "Administración de tratamiento");
    evolution.put("specialty", "Hospital de día");
    evolution.put("text", "Aplicación finalizada.\nEsquema: " + completed.scheme() +
        "\nCiclo: " + completed.cycleNumber() + "\nObservación: " + observation);
    evolution.put("highlighted", true);
    evolution.put("immutable", true);
    evolution.put("createdAt", clock.instant().toString());
    evolution.put("updatedAt", clock.instant().toString());
    evolution.set("attachments", mapper.createArrayNode());
    evolution.set("linkedStudyIds", mapper.createArrayNode());
    ObjectNode source = evolution.putObject("sourceRef");
    source.put("kind", "infusion-administration");
    source.put("infusionId", Long.toString(completed.id()));
    source.put("treatmentId", completed.treatmentId());
    source.put("cycleNumber", completed.cycleNumber());
    EvolutionAppend append = documents.appendImmutableEvolution(
        completed.patientId(), evolution, actor.userId());
    return new Finalization(view(completed), append.evolution(), append.revision(), false);
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
    result.put("applicationId", infusion.applicationId());
    result.put("scheduledAt", infusion.scheduledAt() == null ? null : infusion.scheduledAt().toString());
    result.put("chair", infusion.chair());
    result.put("durationMinutes", infusion.durationMinutes());
    result.put("clinicalStatus", infusion.clinicalStatus());
    result.put("pharmacyStatus", infusion.pharmacyStatus());
    result.put("administrationStatus", infusion.administrationStatus());
    result.put("appointmentConfirmed", infusion.appointmentConfirmed() ||
        infusion.sourceRef().path("scheduler").path("appointmentConfirmed").asBoolean(false));
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
    result.put("id", item.patientId() + ":" + item.treatmentId() + ":" + item.cycleNumber());
    result.put("patientId", Long.toString(item.patientId()));
    result.put("treatmentId", item.treatmentId());
    result.put("cycleNumber", item.cycleNumber());
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
    result.put("drugScheme", item.scheme());
    result.put("treatmentType", item.treatmentType());
    result.put("totalCycles", item.totalCycles());
    result.put("cycleDays", item.cycleDays());
    result.put("durationMinutes", resolvedDuration(item.schemeId(), item.durationMinutes()));
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

  private int resolvedDuration(String schemeId, Integer storedDuration) {
    if (storedDuration != null && storedDuration > 0) return storedDuration;
    return treatmentCatalog.scheme(schemeId)
        .map(TreatmentCatalogService.Scheme::durationMinutes)
        .filter(value -> value != null && value > 0)
        .orElse(60);
  }

  private Map<String, Object> logisticsView(Logistics logistics) {
    String state = "with_patient".equals(logistics.medicationState()) ? "patient" : logistics.medicationState();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("patientId", Long.toString(logistics.patientId()));
    result.put("treatmentId", logistics.treatmentId());
    result.put("cycleNumber", logistics.cycleNumber());
    result.put("plannedDate", logistics.plannedDate() == null ? null : logistics.plannedDate().toString());
    result.put("medicationState", state);
    result.put("medicationReceived", "received".equals(logistics.medicationState()));
    result.put("medicationWithPatient", "with_patient".equals(logistics.medicationState()));
    result.put("prescriptionState", logistics.prescriptionState());
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
          text(item, "prescribedDoseText", "dosis"), text(item, "doseUnit", "unidad"),
          text(item, "route", "via"), enumValue(item, PHARMACY, "pending", "preparationStatus"),
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

  private JsonNode object(JsonNode node) {
    return node != null && node.isObject() ? node.deepCopy() : mapper.createObjectNode();
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

  public record Finalization(
      Map<String, Object> infusion,
      ObjectNode evolution,
      Long documentRevision,
      boolean idempotent) {
  }
}
