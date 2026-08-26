package ar.com.hexium.hcop.treatment.infrastructure.persistence;

import ar.com.hexium.hcop.catalog.domain.TreatmentScheme;
import ar.com.hexium.hcop.patient.PatientDocumentRepository.StoredDocument;
import ar.com.hexium.hcop.patient.PatientDocumentService;
import ar.com.hexium.hcop.patient.PatientDocumentService.EvolutionAppend;
import ar.com.hexium.hcop.treatment.application.port.out.TreatmentApplicationSyncPort;
import ar.com.hexium.hcop.treatment.application.port.out.TreatmentStore;
import ar.com.hexium.hcop.treatment.domain.DrugLine;
import ar.com.hexium.hcop.treatment.domain.Treatment;
import ar.com.hexium.hcop.treatment.domain.WorkflowState;
import ar.com.hexium.hcop.treatment.infrastructure.legacy.LegacyDoseUnitResolver;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Repository
public class PostgresTreatmentStore implements TreatmentStore {
  private static final DateTimeFormatter ARGENTINE_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final TreatmentApplicationSyncPort applicationLogistics;
  private final LegacyDoseUnitResolver legacyDoseUnits;
  private final TreatmentCycleTimeline cycleTimeline;
  private final PatientDocumentService documents;

  public PostgresTreatmentStore(
      JdbcTemplate jdbc, ObjectMapper mapper, Clock clock,
      TreatmentApplicationSyncPort applicationLogistics, LegacyDoseUnitResolver legacyDoseUnits,
      TreatmentCycleTimeline cycleTimeline, PatientDocumentService documents) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.clock = clock;
    this.applicationLogistics = applicationLogistics;
    this.legacyDoseUnits = legacyDoseUnits;
    this.cycleTimeline = cycleTimeline;
    this.documents = documents;
  }

  @Override
  public List<Treatment> list(long patientId) {
    return jdbc.query(selectSql() + " WHERE patient_id = ? ORDER BY created_on DESC, created_at DESC",
        this::map, patientId);
  }

  @Override
  public Map<String, WorkflowState> workflowStates(long patientId) {
    Map<String, WorkflowState> states = new LinkedHashMap<>();
    jdbc.query("""
        SELECT t.id,
               COALESCE(m.continuity_status, 'active') AS continuity_status,
               m.effective_from_cycle, m.suspension_reason, m.resume_date,
               COALESCE(m.prescription_required, false) AS prescription_required,
               COALESCE(m.revision, 0) AS management_revision,
               COALESCE((
                 SELECT jsonb_object_agg(l.cycle_number::text, l.prescription_state)
                   FROM treatment_cycle_logistics l
                  WHERE l.patient_id = t.patient_id AND l.treatment_id = t.id
               ), '{}'::jsonb)::text AS prescription_states,
               COALESCE((
                 SELECT jsonb_object_agg(
                   r.cycle_number::text || ':' || r.request_type, r.id)
                   FROM treatment_workflow_requests r
                  WHERE r.patient_id = t.patient_id AND r.treatment_id = t.id
                    AND r.status = 'pending'
               ), '{}'::jsonb)::text AS pending_requests
          FROM clinical_treatments t
          LEFT JOIN treatment_management_states m
            ON m.patient_id = t.patient_id AND m.treatment_id = t.id
         WHERE t.patient_id = ?
        """, result -> {
      Map<Integer, String> prescriptions = new LinkedHashMap<>();
      mapper.readTree(result.getString("prescription_states")).properties().forEach(entry -> {
        try {
          prescriptions.put(Integer.parseInt(entry.getKey()), entry.getValue().asText(""));
        } catch (NumberFormatException ignored) {
          // Ignore malformed legacy keys; valid cycles remain available.
        }
      });
      Map<Integer, Map<String, Long>> requests = new LinkedHashMap<>();
      mapper.readTree(result.getString("pending_requests")).properties().forEach(entry -> {
        String[] key = entry.getKey().split(":", 2);
        if (key.length != 2) return;
        try {
          requests.computeIfAbsent(Integer.parseInt(key[0]), ignored -> new LinkedHashMap<>())
              .put(key[1], entry.getValue().asLong());
        } catch (NumberFormatException ignored) {
          // Ignore malformed legacy keys; valid requests remain available.
        }
      });
      Date resume = result.getDate("resume_date");
      Object effectiveCycle = result.getObject("effective_from_cycle");
      states.put(result.getString("id"), new WorkflowState(
          result.getString("continuity_status"),
          effectiveCycle == null ? null : result.getInt("effective_from_cycle"),
          text(result, "suspension_reason"), resume == null ? null : resume.toLocalDate(),
          result.getBoolean("prescription_required"), result.getLong("management_revision"),
          Map.copyOf(prescriptions), immutableNestedMap(requests)));
    }, patientId);
    return Map.copyOf(states);
  }

  @Override
  public Optional<Treatment> find(long patientId, String treatmentId) {
    return jdbc.query(selectSql() + " WHERE patient_id = ? AND id = ?", this::map, patientId, treatmentId)
        .stream().findFirst();
  }

  @Override
  public Optional<Treatment> find(String treatmentId) {
    return jdbc.query(selectSql() + " WHERE id = ?", this::map, treatmentId).stream().findFirst();
  }

  private Optional<Treatment> findByClinicalEntryId(long patientId, String clinicalEntryId) {
    if (clinicalEntryId == null || clinicalEntryId.isBlank()) return Optional.empty();
    return jdbc.query(selectSql() + """
         WHERE patient_id = ? AND payload ->> 'clinicalEntryId' = ?
        """, this::map, patientId, clinicalEntryId).stream().findFirst();
  }

  @Override
  public Map<String, Object> view(Treatment treatment, WorkflowState workflow, Integer resolvedDurationMinutes) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", treatment.id());
    result.put("patientId", Long.toString(treatment.patientId()));
    result.put("category", "oncological");
    result.put("type", treatment.treatmentType());
    result.put("tipo", treatment.treatmentType());
    result.put("scheme", treatment.schemeName());
    result.put("esquema", treatment.schemeName());
    result.put("schemeId", treatment.schemeId());
    result.put("idEsquema", treatment.schemeId());
    result.put("diagnosis", treatment.diagnosis());
    result.put("diagnostico", treatment.diagnosis());
    result.put("diagnosisId", treatment.diagnosisId());
    result.put("oncologist", treatment.oncologist());
    result.put("oncologo", treatment.oncologist());
    result.put("status", treatment.status());
    result.put("estadoTratamiento", treatment.status());
    String consentStatus = displayConsentStatus(treatment);
    result.put("consentStatus", consentStatus);
    result.put("estadoConsentimiento", consentStatus);
    result.put("consentAvailable", treatment.consentAvailable());
    result.put("intent", treatment.intent());
    result.put("caracter", treatment.intent());
    result.put("cycles", treatment.cycleCount());
    result.put("cycleCount", treatment.cycleCount());
    result.put("cantidadCiclos", treatment.cycleCount());
    result.put("initialCycle", treatment.initialCycle());
    result.put("cicloInicial", treatment.initialCycle());
    result.put("cycleDays", treatment.cycleDays());
    result.put("duracionCiclo", treatment.cycleDays());
    result.put("createdDate", treatment.createdOn().toString());
    result.put("date", treatment.createdOn().toString());
    result.put("fechaCreacion", treatment.createdOn().format(ARGENTINE_DATE));
    result.put("firstCycleDate", treatment.firstCycleDate() == null ? "" : treatment.firstCycleDate().toString());
    result.put("fechaPrimerCiclo", treatment.firstCycleDate() == null ? "" : treatment.firstCycleDate().toString());
    result.put("estimatedDurationMinutes", resolvedDurationMinutes);
    result.put("estimatedDurationText", durationText(resolvedDurationMinutes));
    result.put("durationMinutes", resolvedDurationMinutes);
    result.put("originLocal", true);
    result.put("origenLocal", true);
    result.put("revision", treatment.revision());
    result.put("createdAt", treatment.createdAt().toString());
    result.put("updatedAt", treatment.updatedAt().toString());
    ((JsonNode) treatment.payload()).properties()
        .forEach(entry -> result.putIfAbsent(entry.getKey(), jsonValue(entry.getValue())));
    if (workflow != null) {
      int effectiveCycle = workflow.effectiveFromCycle() == null
          ? treatment.initialCycle() : workflow.effectiveFromCycle();
      result.put("workflowStatus", workflow.continuityStatus());
      result.put("continuityState", workflow.continuityStatus());
      result.put("courseState", workflow.continuityStatus());
      result.put("effectiveFromCycle", effectiveCycle);
      result.put("reactivationCycle", effectiveCycle);
      result.put("suspensionReason", workflow.suspensionReason());
      result.put("resumeDate", workflow.resumeDate() == null ? null : workflow.resumeDate().toString());
      result.put("prescriptionRequired", workflow.prescriptionRequired());
      result.put("managementRevision", workflow.managementRevision());
      result.put("prescriptionStates", workflow.prescriptionStates());
      result.put("prescriptionWorkflowState",
          workflow.prescriptionStates().getOrDefault(effectiveCycle, "required"));
      result.put("pendingRequestIdsByCycle", workflow.pendingRequestIdsByCycle());
      result.put("pendingRequestIds",
          workflow.pendingRequestIdsByCycle().getOrDefault(effectiveCycle, Map.of()));
    }
    return result;
  }

  @Override
  public TreatmentCreationOutcome insert(NewTreatmentDraft draft) {
    JsonNode rawBody = (JsonNode) draft.rawBody();
    ObjectNode payload = (ObjectNode) rawBody.deepCopy();
    payload.put("clinicalEntryId", draft.clinicalEntryId());
    payload.put("origenLocal", true);
    payload.put("requirementsConfirmed", true);
    payload.put("doseCalculated", true);
    payload.put("doseCalculationStatus", "calculated_from_verified_inputs");
    payload.put("pesoKg", draft.weightKg());
    payload.put("tallaCm", draft.heightCm());
    payload.put("supCorporal", draft.bodySurface());
    payload.put("tfg", draft.gfr());
    payload.put("targetAUC", draft.targetAuc());
    payload.put("protocolDiagnosisGroup", draft.protocolDiagnosisGroup());
    payload.put("protocolGroup", draft.protocolGroup());
    payload.put("protocolMismatchConfirmed", draft.protocolMismatchConfirmed());
    payload.put("protocolMismatchReason", draft.protocolMismatchReason());
    ObjectNode detail = createDetail(
        draft.id(), draft.patientId(), draft.scheme(), draft.initialCycle(), draft.cycleCount(),
        draft.firstCycleDate(), draft.cycleDays(), draft.weightKg(), draft.heightCm(),
        draft.bodySurface(), draft.gfr(), draft.targetAuc());

    Instant now = clock.instant();
    int inserted = jdbc.update("""
        INSERT INTO clinical_treatments (
          id, patient_id, diagnosis_id, created_on, first_cycle_date, initial_cycle,
          cycle_count, cycle_days, treatment_type, intent, diagnosis, scheme_id,
          scheme_name, oncologist, treatment_status, consent_status, consent_available,
          estimated_duration_minutes, payload, created_by, updated_by, created_at, updated_at
        ) VALUES (?, ?, NULLIF(?, ''), ?, ?, ?, ?, ?, NULLIF(?, ''), NULLIF(?, ''),
                  NULLIF(?, ''), NULLIF(?, ''), ?, NULLIF(?, ''), ?, NULLIF(?, ''),
                  ?, ?, CAST(? AS jsonb), ?, ?, ?, ?)
        ON CONFLICT DO NOTHING
        """,
        draft.id(), draft.patientId(), draft.diagnosisId(), Date.valueOf(draft.createdOn()),
        draft.firstCycleDate() == null ? null : Date.valueOf(draft.firstCycleDate()),
        draft.initialCycle(), draft.cycleCount(), draft.cycleDays() > 0 ? draft.cycleDays() : null,
        draft.treatmentType(), draft.intent(), draft.diagnosis(), draft.schemeId(),
        draft.scheme().name(), draft.oncologist(), "Iniciado", draft.consentStatus(),
        draft.consentAvailable(), draft.scheme().durationMinutes(), payload.toString(),
        draft.actorId(), draft.actorId(), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));

    if (inserted == 0) {
      String entryId = rawBody.path("clinicalEntryId").asText("");
      Treatment existing = findByClinicalEntryId(draft.patientId(), entryId)
          .orElseThrow(() -> new IllegalStateException(
              "No se pudo recuperar el tratamiento después de un reintento idempotente."));
      StoredDocument currentDocument = documents.require(draft.patientId());
      ObjectNode existingEvolution = treatmentEvolutionFromDocument(
          currentDocument.document(), existing.id(), payload.path("clinicalEntryId").asText(""));
      if (existingEvolution == null) {
        existingEvolution = treatmentEvolution(
            existing, (JsonNode) existing.payload(), draft.actorDisplayName());
      }
      return new TreatmentCreationOutcome(
          view(existing, null, resolvedDurationForInsert(existing)), existingEvolution,
          currentDocument.revision(), existing.createdAt().toString(), true);
    }

    jdbc.update("""
        INSERT INTO treatment_details (treatment_id, detail_json, revision, updated_by, updated_at)
        VALUES (?, CAST(? AS jsonb), 1, ?, ?)
        """, draft.id(), detail.toString(), draft.actorId(), java.sql.Timestamp.from(now));
    for (int cycle = draft.initialCycle(); cycle < draft.initialCycle() + draft.cycleCount(); cycle++) {
      LocalDate planned = draft.firstCycleDate() == null || draft.cycleDays() < 1
          ? null
          : draft.firstCycleDate().plusDays((long) (cycle - draft.initialCycle()) * draft.cycleDays());
      jdbc.update("""
          INSERT INTO treatment_cycle_logistics (
            patient_id, treatment_id, cycle_number, planned_date, medication_state,
            prescription_state, revision, updated_by, created_at, updated_at
          ) VALUES (?, ?, ?, ?, 'pending', 'confirmed', 1, ?, ?, ?)
          """, draft.patientId(), draft.id(), cycle,
          planned == null ? null : Date.valueOf(planned), draft.actorId(),
          java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
    }
    applicationLogistics.synchronize(draft.id());
    Treatment created = find(draft.patientId(), draft.id()).orElseThrow();
    ObjectNode evolution = treatmentEvolution(created, rawBody, draft.actorDisplayName());
    EvolutionAppend append = documents.appendImmutableEvolution(draft.patientId(), evolution, draft.actorId());
    return new TreatmentCreationOutcome(
        view(created, null, resolvedDurationForInsert(created)), append.evolution(),
        append.revision(), created.createdAt().toString(), false);
  }

  private Integer resolvedDurationForInsert(Treatment treatment) {
    return treatment.durationMinutes() != null && treatment.durationMinutes() > 0
        ? treatment.durationMinutes() : null;
  }

  @Override
  public Object enrichedDetail(String treatmentId, TreatmentScheme schemeOrNull, List<Map<String, Object>> sessions) {
    JsonNode detail = detail(treatmentId).deepCopy();
    if (detail instanceof ObjectNode object) {
      object.put("localView", true);
      object.put("localRecord", true);
      object.put("origin", "local");
      object.put("schemeFound", schemeOrNull != null);
      for (JsonNode value : object.path("cycles")) {
        if (!(value instanceof ObjectNode cycle)) continue;
        if ((!cycle.path("drugs").isArray() || cycle.path("drugs").isEmpty()) && schemeOrNull != null) {
          cycle.set("drugs", extractDrugs(
              (JsonNode) schemeOrNull.definition(), cycle.path("number").asInt(), null));
          object.put("protocolSnapshotRecovered", true);
        }
      }
      cycleTimeline.enrich(object, sessions);
    }
    return detail;
  }

  @Override
  public Optional<List<DrugLine>> cycleDrugs(String treatmentId, int cycle) {
    JsonNode detail = detail(treatmentId);
    for (JsonNode item : detail.path("cycles")) {
      if (item.path("number").asInt() == cycle) {
        List<DrugLine> lines = new java.util.ArrayList<>();
        for (JsonNode drug : item.path("drugs")) {
          lines.add(new DrugLine(
              text(drug, "drugName", "name", "nombre"),
              text(drug, "prescribedDoseText", "dose", "dosis"),
              text(drug, "doseUnit", "unidadDosis", "unidad"),
              text(drug, "route", "via"),
              text(drug, "administrationTime", "time")));
        }
        return Optional.of(lines);
      }
    }
    return Optional.empty();
  }

  private JsonNode detail(String treatmentId) {
    return jdbc.query("""
        SELECT detail_json::text FROM treatment_details WHERE treatment_id = ?
        """, (result, row) -> mapper.readTree(result.getString(1)), treatmentId)
        .stream().findFirst().orElse(mapper.createObjectNode());
  }

  private ObjectNode createDetail(
      String treatmentId, long patientId, TreatmentScheme scheme, int initialCycle, int cycleCount,
      LocalDate firstCycle, int cycleDays, double weightKg, double heightCm, double bodySurface,
      double gfr, double targetAuc) {
    ObjectNode detail = mapper.createObjectNode();
    detail.put("treatmentId", treatmentId);
    detail.put("patientId", Long.toString(patientId));
    detail.put("origin", "local");
    detail.put("localView", true);
    detail.put("schemeFound", true);
    detail.put("activeCycle", initialCycle);
    ObjectNode actions = detail.putObject("actions");
    actions.put("prescription", false);
    actions.put("treatmentSheet", true);
    ArrayNode actionCycles = actions.putArray("treatmentSheetCycles");
    ArrayNode cycles = detail.putArray("cycles");
    DosingContext dosing = new DosingContext(weightKg, heightCm, bodySurface, gfr, targetAuc);
    for (int number = initialCycle; number < initialCycle + cycleCount; number++) {
      ObjectNode cycle = cycles.addObject();
      cycle.put("number", number);
      LocalDate planned = cycleDays > 0 ? firstCycle.plusDays((long) (number - initialCycle) * cycleDays) : firstCycle;
      cycle.put("plannedDate", planned.toString());
      cycle.put("date", planned.toString());
      cycle.set("drugs", extractDrugs((JsonNode) scheme.definition(), number, dosing));
      cycle.set("applications", mapper.createArrayNode());
      actionCycles.add(number);
    }
    ObjectNode availability = detail.putObject("documentAvailability");
    availability.put("prescription", false);
    availability.set("treatmentSheetCycles", actionCycles.deepCopy());
    cycleTimeline.enrich(detail, List.of());
    return detail;
  }

  private ArrayNode extractDrugs(JsonNode definition, int cycleNumber) {
    return extractDrugs(definition, cycleNumber, null);
  }

  private ArrayNode extractDrugs(JsonNode definition, int cycleNumber, DosingContext dosing) {
    ArrayNode result = mapper.createArrayNode();
    JsonNode source = definition.path("drugs");
    if (!source.isArray()) source = definition.path("drogas");
    if (!source.isArray()) source = definition.path("components");
    if (!source.isArray()) return result;
    int componentIndex = 0;
    Set<String> sourceItemRefs = new java.util.HashSet<>();
    for (JsonNode item : source) {
      ObjectNode drug = result.addObject();
      drug.put("drugId", text(item, "drugId", "idDroga", "id"));
      drug.put("drugName", text(item, "drugName", "droga", "name", "nombre"));
      String sourceItemRef = text(item, "sourceItemRef", "componentId", "id");
      if (sourceItemRef.isBlank()) {
        sourceItemRef = "component-" + (++componentIndex);
      } else {
        componentIndex++;
      }
      if (!sourceItemRefs.add(sourceItemRef)) {
        sourceItemRef = sourceItemRef + "-" + componentIndex;
        sourceItemRefs.add(sourceItemRef);
      }
      drug.put("sourceItemRef", sourceItemRef);
      String baseDoseText = text(item, "prescribedDoseText", "dosisDiaria", "dosis", "dose");
      String method = text(item, "calculationMethod", "calculoDosis", "doseCalculation");
      String doseUnit = doseUnit(
          baseDoseText, method, text(item, "doseUnit", "unidadDosis", "unidad"));
      if (doseUnit.isBlank()) doseUnit = legacyDoseUnits.resolve(item);
      if (doseUnit.isBlank()) {
        throw new ar.com.hexium.hcop.treatment.application.service.TreatmentFailure(
            ar.com.hexium.hcop.treatment.application.service.TreatmentFailure.Type.UNPROCESSABLE,
            "El componente " + text(item, "drugName", "droga", "name", "nombre")
                + " no tiene unidad de dosis. Complete la unidad en Configuración > Protocolos.");
      }
      DoseCalculation calculation = calculateDose(baseDoseText, method, dosing);
      drug.put("calculationMethod", method);
      drug.put("baseDoseText", baseDoseText);
      drug.put("calculatedDoseText", calculation.doseText());
      drug.put("prescribedDoseText", calculation.doseText());
      drug.put("doseUnit", doseUnit);
      drug.put("doseCalculationStatus", calculation.status());
      drug.put("calculationTrace", calculation.trace());
      drug.put("applicationDays", text(item, "applicationDays", "dia", "days"));
      drug.put("route", text(item, "route", "viaAdministracion", "via"));
      drug.put("administrationTime", text(item, "administrationTime", "tiempoAdministracion", "time"));
      drug.put("totalDoseText", calculation.doseText());
      drug.put("cycleNumber", cycleNumber);
      drug.set("source", item.deepCopy());
    }
    return result;
  }

  private ObjectNode treatmentEvolution(Treatment treatment, JsonNode input, String actorDisplayName) {
    String createdAt = treatment.createdAt().toString();
    ObjectNode evolution = mapper.createObjectNode();
    evolution.put("id", "treatment-evolution-" + treatment.id());
    evolution.put("date", treatment.createdOn().toString());
    evolution.put("datePrecision", "day");
    evolution.put("author", treatment.oncologist().isBlank() ? actorDisplayName : treatment.oncologist());
    evolution.put("reason", "Alta de tratamiento oncológico");
    evolution.put("specialty", "Oncología");
    evolution.put("highlighted", true);
    evolution.put("immutable", true);
    evolution.set("attachments", mapper.createArrayNode());
    evolution.set("linkedStudyIds", mapper.createArrayNode());
    ObjectNode source = evolution.putObject("sourceRef");
    source.put("kind", "oncological-treatment");
    source.put("treatmentId", treatment.id());
    source.put("clinicalEntryId", text(input, "clinicalEntryId", "treatmentEntryId"));
    List<String> lines = new java.util.ArrayList<>();
    lines.add("Alta de tratamiento oncológico.");
    addLine(lines, "Diagnóstico", treatment.diagnosis());
    addLine(lines, "Carácter", treatment.intent());
    addLine(lines, "Tipo de tratamiento", treatment.treatmentType());
    addLine(lines, "Esquema", treatment.schemeName());
    addLine(lines, "Ciclos previstos", Integer.toString(treatment.cycleCount()));
    addLine(lines, "Ciclo inicial", Integer.toString(treatment.initialCycle()));
    addLine(lines, "Fecha prevista del primer ciclo", treatment.firstCycleDate() == null ? "" : treatment.firstCycleDate().format(ARGENTINE_DATE));
    addLine(lines, "Consentimiento", treatment.consentStatus());
    addLine(lines, "Peso", unit(text(input, "peso"), "kg"));
    addLine(lines, "Talla", unit(text(input, "talla"), "cm"));
    addLine(lines, "Superficie corporal", unit(text(input, "supCorporal", "superficieCorporal"), "m²"));
    addLine(lines, "Observaciones", text(input, "observaciones", "notes"));
    if (input.path("requirementsConfirmed").asBoolean(false)) lines.add("Datos requeridos verificados: Sí");
    if (input.path("protocolMismatchConfirmed").asBoolean(false)) {
      addLine(lines, "Excepción diagnóstico-protocolo", text(input, "protocolMismatchReason"));
    }
    evolution.put("text", String.join("\n", lines));
    ObjectNode audit = evolution.putObject("audit");
    audit.put("action", "cargado");
    audit.put("at", createdAt);
    audit.put("lastName", actorDisplayName);
    evolution.put("createdAt", createdAt);
    evolution.put("updatedAt", createdAt);
    return evolution;
  }

  private ObjectNode treatmentEvolutionFromDocument(
      JsonNode document, String treatmentId, String clinicalEntryId) {
    JsonNode evolutions = document.path("evolutions");
    if (!evolutions.isArray()) return null;
    for (JsonNode item : evolutions) {
      JsonNode source = item.path("sourceRef");
      boolean sameTreatment = treatmentId.equals(source.path("treatmentId").asText(""));
      boolean sameEntry = !clinicalEntryId.isBlank()
          && clinicalEntryId.equals(source.path("clinicalEntryId").asText(""));
      if ((sameTreatment || sameEntry) && item.isObject()) {
        return (ObjectNode) item.deepCopy();
      }
    }
    return null;
  }

  private void addLine(List<String> lines, String label, String value) {
    if (value != null && !value.isBlank()) lines.add(label + ": " + value);
  }

  private String unit(String value, String unit) {
    return value.isBlank() ? "" : value + " " + unit;
  }

  private String displayConsentStatus(Treatment treatment) {
    if (isSignedConsent(treatment.consentStatus()) && !treatment.consentAvailable()) {
      return "Firmado · documento pendiente";
    }
    return treatment.consentStatus();
  }

  private boolean isSignedConsent(String value) {
    String normalized = normalize(value);
    return normalized.equals("firmado") || normalized.equals("signed")
        || normalized.startsWith("firmado documento");
  }

  private DoseCalculation calculateDose(String baseDoseText, String method, DosingContext dosing) {
    double base = parseNumber(baseDoseText);
    String normalizedMethod = normalize(method);
    if (!(base > 0)) {
      return new DoseCalculation(baseDoseText, "missing_base_dose", "Dosis base no estructurada.");
    }
    if (normalizedMethod.contains("superficie")) {
      if (dosing == null || !(dosing.bodySurface() > 0)) {
        return new DoseCalculation(
            baseDoseText, "calculation_pending",
            "Requiere superficie corporal y confirmación médica.");
      }
      double dose = roundDose(base * dosing.bodySurface());
      return new DoseCalculation(
          formatDose(dose), "calculated_from_patient",
          formatDose(base) + " × SC " + formatDose(dosing.bodySurface()) + " m²");
    }
    if (normalizedMethod.equals("peso") || normalizedMethod.contains("por peso")) {
      if (dosing == null || !(dosing.weightKg() > 0)) {
        return new DoseCalculation(
            baseDoseText, "calculation_pending",
            "Requiere peso y confirmación médica.");
      }
      double dose = roundDose(base * dosing.weightKg());
      return new DoseCalculation(
          formatDose(dose), "calculated_from_patient",
          formatDose(base) + " × " + formatDose(dosing.weightKg()) + " kg");
    }
    if (normalizedMethod.contains("calvert")) {
      if (dosing == null || !(dosing.gfr() > 0) || !(dosing.targetAuc() > 0)) {
        return new DoseCalculation(
            baseDoseText, "calculation_pending",
            "Requiere TFG, Target AUC y confirmación médica.");
      }
      double dose = roundDose(dosing.targetAuc() * (dosing.gfr() + 25));
      return new DoseCalculation(
          formatDose(dose), "calculated_from_patient",
          "Calvert: AUC " + formatDose(dosing.targetAuc())
              + " × (TFG " + formatDose(dosing.gfr()) + " + 25)");
    }
    return new DoseCalculation(
        formatDose(base), "fixed_protocol_dose", "Dosis fija indicada por el protocolo.");
  }

  private String doseUnit(String baseDoseText, String method, String configuredUnit) {
    String unit = configuredUnit == null ? "" : configuredUnit.trim();
    if (unit.isBlank()) {
      unit = baseDoseText == null ? "" : baseDoseText
          .replaceFirst("^[\\s+-]*\\d+(?:[\\.,]\\d+)?\\s*", "")
          .trim();
    }
    if (unit.isBlank()) return "";
    String normalizedMethod = normalize(method);
    if (normalizedMethod.contains("superficie")) {
      unit = unit.replaceFirst("(?i)\\s*/?\\s*m(?:²|2)\\s*$", "").trim();
    } else if (normalizedMethod.equals("peso") || normalizedMethod.contains("por peso")) {
      unit = unit.replaceFirst("(?i)\\s*/?\\s*kg\\s*$", "").trim();
    }
    return unit;
  }

  private double parseNumber(String value) {
    try {
      return Double.parseDouble((value == null ? "" : value).trim().replace(',', '.'));
    } catch (NumberFormatException invalid) {
      return 0;
    }
  }

  private double roundDose(double value) {
    return Math.round(value * 1000d) / 1000d;
  }

  private String formatDose(double value) {
    return java.math.BigDecimal.valueOf(roundDose(value)).stripTrailingZeros().toPlainString();
  }

  private String durationText(Integer minutes) {
    if (minutes == null || minutes < 1) return "";
    int hours = minutes / 60;
    int remainder = minutes % 60;
    if (hours == 0) return minutes + " min";
    if (remainder == 0) return hours + " h";
    return hours + " h " + remainder + " min";
  }

  private Object jsonValue(JsonNode value) {
    return mapper.convertValue(value, Object.class);
  }

  private String selectSql() {
    return """
        SELECT id, patient_id, diagnosis_id, created_on, first_cycle_date, initial_cycle,
               cycle_count, cycle_days, treatment_type, intent, diagnosis, scheme_id,
               scheme_name, oncologist, treatment_status, consent_status, consent_available,
               estimated_duration_minutes, payload::text, revision, created_at, updated_at
          FROM clinical_treatments
        """;
  }

  private Treatment map(ResultSet result, int rowNumber) throws SQLException {
    Date first = result.getDate("first_cycle_date");
    return new Treatment(
        result.getString("id"),
        result.getLong("patient_id"),
        text(result, "diagnosis_id"),
        result.getDate("created_on").toLocalDate(),
        first == null ? null : first.toLocalDate(),
        result.getInt("initial_cycle"),
        result.getInt("cycle_count"),
        result.getObject("cycle_days") == null ? 0 : result.getInt("cycle_days"),
        text(result, "treatment_type"),
        text(result, "intent"),
        text(result, "diagnosis"),
        text(result, "scheme_id"),
        text(result, "scheme_name"),
        text(result, "oncologist"),
        text(result, "treatment_status"),
        text(result, "consent_status"),
        result.getBoolean("consent_available"),
        result.getObject("estimated_duration_minutes") == null
            ? null : result.getInt("estimated_duration_minutes"),
        mapper.readTree(result.getString("payload")),
        result.getLong("revision"),
        result.getTimestamp("created_at").toInstant(),
        result.getTimestamp("updated_at").toInstant());
  }

  private String text(ResultSet result, String field) throws SQLException {
    String value = result.getString(field);
    return value == null ? "" : value;
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

  private String normalize(String value) {
    return java.text.Normalizer.normalize(value == null ? "" : value, java.text.Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
  }

  private Map<Integer, Map<String, Long>> immutableNestedMap(
      Map<Integer, Map<String, Long>> source) {
    Map<Integer, Map<String, Long>> copy = new LinkedHashMap<>();
    source.forEach((cycle, requests) -> copy.put(cycle, Map.copyOf(requests)));
    return Map.copyOf(copy);
  }

  private record DosingContext(
      double weightKg, double heightCm, double bodySurface, double gfr, double targetAuc) {
  }

  private record DoseCalculation(String doseText, String status, String trace) {
  }
}
