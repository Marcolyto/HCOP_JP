package ar.com.hexium.hcop.treatment;

import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.catalog.TreatmentCatalogService;
import ar.com.hexium.hcop.catalog.TreatmentCatalogService.Scheme;
import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.patient.PatientDocumentRepository.StoredDocument;
import ar.com.hexium.hcop.patient.PatientDocumentService;
import ar.com.hexium.hcop.patient.PatientDocumentService.EvolutionAppend;
import ar.com.hexium.hcop.patient.PatientService;
import ar.com.hexium.hcop.infusion.InfusionService;
import ar.com.hexium.hcop.treatment.TreatmentRepository.NewTreatment;
import ar.com.hexium.hcop.treatment.TreatmentRepository.Treatment;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class TreatmentService {
  private static final DateTimeFormatter ARGENTINE_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");
  private final TreatmentRepository treatments;
  private final TreatmentCatalogService catalog;
  private final PatientService patients;
  private final PatientDocumentService documents;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final InfusionService infusions;
  private final TreatmentProtocolCompatibility compatibility;
  private final TreatmentCycleTimeline cycleTimeline;
  private final LegacyDoseUnitResolver legacyDoseUnits;

  public TreatmentService(
      TreatmentRepository treatments,
      TreatmentCatalogService catalog,
      PatientService patients,
      PatientDocumentService documents,
      ObjectMapper mapper,
      Clock clock,
      InfusionService infusions,
      TreatmentProtocolCompatibility compatibility,
      TreatmentCycleTimeline cycleTimeline,
      LegacyDoseUnitResolver legacyDoseUnits) {
    this.treatments = treatments;
    this.catalog = catalog;
    this.patients = patients;
    this.documents = documents;
    this.mapper = mapper;
    this.clock = clock;
    this.infusions = infusions;
    this.compatibility = compatibility;
    this.cycleTimeline = cycleTimeline;
    this.legacyDoseUnits = legacyDoseUnits;
  }

  public List<Map<String, Object>> list(long patientId) {
    patients.require(patientId);
    return treatments.list(patientId).stream().map(this::view).toList();
  }

  public Map<String, Object> options(long patientId) {
    patients.require(patientId);
    StoredDocument stored = documents.require(patientId);
    List<Map<String, Object>> diagnoses = diagnoses(stored.document());
    Map<String, Object> options = new LinkedHashMap<>();
    options.put("diagnoses", diagnoses);
    options.put("diagnosticos", diagnoses);
    List<Map<String, Object>> schemes = catalog.schemes("").stream().map(item -> {
      Map<String, Object> view = new LinkedHashMap<>(item);
      var assessment = compatibility.assess("", String.valueOf(item.getOrDefault("nombre", "")));
      view.put("protocolGroup", assessment.protocolGroup());
      view.put("protocolGroupLabel", assessment.protocolGroupLabel());
      return view;
    }).toList();
    options.put("schemes", schemes);
    options.put("esquemas", schemes);
    options.put("characters", simpleOptions("Curativo", "Paliativo", "Adyuvante", "Neoadyuvante"));
    options.put("caracteres", options.get("characters"));
    options.put("treatmentTypes", simpleOptions(
        "Quimioterapia", "Inmunoterapia", "Hormonoterapia",
        "Quimioterapia + Inmunoterapia", "Bifosfonatos", "Terapia dirigida"));
    options.put("tipos", options.get("treatmentTypes"));
    options.put("consentStates", simpleOptions("Pendiente", "Firmado · documento pendiente", "No requiere"));
    options.put("consentimientos", options.get("consentStates"));
    return Map.of("ok", true, "patientId", Long.toString(patientId), "options", options);
  }

  public Map<String, Object> requirements(long patientId, String schemeId) {
    var patient = patients.require(patientId);
    Scheme scheme = catalog.scheme(schemeId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El esquema no existe."));
    String definitionText = scheme.definition().toString().toLowerCase(Locale.ROOT);
    boolean calvert = definitionText.contains("calvert") || normalize(scheme.name()).contains("carboplatino");
    boolean surface = definitionText.contains("superficie corporal") ||
        definitionText.contains("mg/m2") || definitionText.contains("mg/m²");
    boolean weight = surface || calvert || definitionText.contains("\"peso\"");
    boolean calcium = normalize(scheme.name()).matches(".*(zoled|denosumab|bifosfonat).*");
    Integer age = patient.birthDate() == null
        ? null : Math.max(0, Period.between(patient.birthDate(), LocalDate.now(clock)).getYears());
    Map<String, Object> requirements = new LinkedHashMap<>();
    requirements.put("hayPeso", weight);
    requirements.put("hayTalla", surface);
    requirements.put("hayCalvert", calvert);
    requirements.put("hayCalcioAlbumina", calcium);
    requirements.put("peso", null);
    requirements.put("talla", null);
    requirements.put("idSexo", normalize(patient.sex()).contains("femen") ? "1"
        : normalize(patient.sex()).contains("mascul") ? "2" : null);
    requirements.put("edad", age);
    requirements.put("creatinina", null);
    requirements.put("calcio", null);
    requirements.put("albumina", null);
    requirements.put("origen", "catalogo-local-postgresql");
    requirements.put("doseCalculated", false);
    return Map.of(
        "ok", true,
        "patientId", Long.toString(patientId),
        "schemeId", schemeId,
        "requirements", requirements);
  }

  @Transactional
  public Creation create(long patientId, JsonNode input, SessionPrincipal actor) {
    patients.require(patientId);
    String diagnosisId = text(input, "diagnostico", "diagnosis", "diagnosisId");
    if (diagnosisId.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Seleccione un diagnóstico guardado.");
    }
    StoredDocument stored = documents.require(patientId);
    String diagnosis = diagnosisLabel(stored.document(), diagnosisId);
    if (diagnosis.isBlank()) {
      throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "El diagnóstico no pertenece al paciente.");
    }
    String schemeId = text(input, "esquema", "scheme", "schemeId");
    Scheme scheme = catalog.scheme(schemeId)
        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Seleccione un esquema válido."));
    var protocolAssessment = compatibility.assess(diagnosis, scheme.name());
    boolean protocolMismatchConfirmed = input.path("protocolMismatchConfirmed").asBoolean(false);
    String protocolMismatchReason = text(input, "protocolMismatchReason");
    if (protocolAssessment.mismatch()
        && (!protocolMismatchConfirmed || protocolMismatchReason.length() < 10)) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "El esquema pertenece a " + protocolAssessment.protocolGroupLabel()
              + " y el diagnóstico a " + protocolAssessment.diagnosisGroupLabel()
              + ". Confirme la excepción y documente el motivo clínico.");
    }
    int cycleCount = boundedInt(input, 1, 500, 1, "cantidadCiclos", "cycles", "cycleCount");
    int initialCycle = boundedInt(input, 1, 500, 1, "cicloInicial", "initialCycle");
    int cycleDays = boundedInt(input, 0, 3650, Math.max(0, scheme.cycleDays()),
        "duracionCiclo", "cycleDays");
    if ((long) initialCycle + cycleCount - 1 > 500) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "El ciclo inicial más la cantidad de ciclos no puede superar el ciclo 500.");
    }
    if (cycleCount > 1 && cycleDays < 1) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "Un tratamiento con más de un ciclo necesita un intervalo mayor a cero días.");
    }
    DosingContext dosing = dosingContext(input, scheme);
    LocalDate createdOn = date(input, LocalDate.now(clock), "fechaCreacion", "date", "createdDate");
    LocalDate firstCycle = date(input, createdOn, "fechaPrimerCiclo", "firstCycleDate");
    String id = "trt-" + UUID.randomUUID();
    String oncologist = text(input, "oncologo", "oncologist");
    if (oncologist.isBlank()) oncologist = actor.displayName();
    String consent = text(input, "estadoConsentimiento", "consent", "consentStatus");
    boolean consentAvailable = input.path("consentAvailable").asBoolean(false);
    if (isSignedConsent(consent) && !consentAvailable) {
      consent = "Firmado · documento pendiente";
    }
    ObjectNode payload = (ObjectNode) input.deepCopy();
    payload.put("clinicalEntryId", text(input, "clinicalEntryId", "treatmentEntryId"));
    payload.put("origenLocal", true);
    payload.put("requirementsConfirmed", true);
    payload.put("doseCalculated", true);
    payload.put("doseCalculationStatus", "calculated_from_verified_inputs");
    payload.put("pesoKg", dosing.weightKg());
    payload.put("tallaCm", dosing.heightCm());
    payload.put("supCorporal", dosing.bodySurface());
    payload.put("tfg", dosing.gfr());
    payload.put("targetAUC", dosing.targetAuc());
    payload.put("protocolDiagnosisGroup", protocolAssessment.diagnosisGroup());
    payload.put("protocolGroup", protocolAssessment.protocolGroup());
    payload.put("protocolMismatchConfirmed", protocolAssessment.mismatch() && protocolMismatchConfirmed);
    payload.put("protocolMismatchReason", protocolAssessment.mismatch() ? protocolMismatchReason : "");
    ObjectNode detail = createDetail(
        id, patientId, scheme, initialCycle, cycleCount, firstCycle, cycleDays, dosing);
    NewTreatment value = new NewTreatment(
        id, patientId, diagnosisId, createdOn, firstCycle, initialCycle, cycleCount, cycleDays,
        text(input, "tipoOncologico", "treatmentType", "type"),
        text(input, "caracter", "character", "intent"),
        diagnosis, schemeId, scheme.name(), oncologist, "Iniciado", consent, consentAvailable,
        scheme.durationMinutes(), payload, detail);
    var insertion = treatments.insert(value, actor.userId());
    Treatment treatment = insertion.treatment();
    if (!insertion.created()) {
      StoredDocument currentDocument = documents.require(patientId);
      ObjectNode existingEvolution = treatmentEvolutionFromDocument(
          currentDocument.document(), treatment.id(), payload.path("clinicalEntryId").asText(""));
      if (existingEvolution == null) {
        existingEvolution = treatmentEvolution(treatment, treatment.payload(), actor);
      }
      return new Creation(
          view(treatment), existingEvolution, currentDocument.revision(),
          treatment.createdAt().toString(), true);
    }
    ObjectNode evolution = treatmentEvolution(treatment, input, actor);
    EvolutionAppend append = documents.appendImmutableEvolution(patientId, evolution, actor.userId());
    return new Creation(
        view(treatment), append.evolution(), append.revision(),
        treatment.createdAt().toString(), false);
  }

  public Map<String, Object> detail(long patientId, String treatmentId) {
    Treatment treatment = treatments.find(patientId, treatmentId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tratamiento no encontrado."));
    JsonNode detail = treatments.detail(treatmentId).deepCopy();
    if (detail instanceof ObjectNode object) {
      Scheme scheme = catalog.scheme(treatment.schemeId()).orElse(null);
      object.put("localView", true);
      object.put("localRecord", true);
      object.put("origin", "local");
      object.put("schemeFound", scheme != null);
      List<Map<String, Object>> sessions = infusions.list(patientId, null).stream()
          .filter(item -> treatmentId.equals(String.valueOf(item.get("treatmentId"))))
          .toList();
      for (JsonNode value : object.path("cycles")) {
        if (!(value instanceof ObjectNode cycle)) continue;
        if ((!cycle.path("drugs").isArray() || cycle.path("drugs").isEmpty()) && scheme != null) {
          cycle.set("drugs", extractDrugs(scheme.definition(), cycle.path("number").asInt()));
          object.put("protocolSnapshotRecovered", true);
        }
      }
      cycleTimeline.enrich(object, sessions);
    }
    return Map.of("ok", true, "patientId", Long.toString(patientId), "treatmentId", treatmentId, "detail", detail);
  }

  public Map<String, Object> view(Treatment treatment) {
    Map<String, Object> result = new LinkedHashMap<>();
    Integer duration = resolvedDuration(treatment);
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
    result.put("estimatedDurationMinutes", duration);
    result.put("estimatedDurationText", durationText(duration));
    result.put("durationMinutes", duration);
    result.put("originLocal", true);
    result.put("origenLocal", true);
    result.put("revision", treatment.revision());
    result.put("createdAt", treatment.createdAt().toString());
    result.put("updatedAt", treatment.updatedAt().toString());
    treatment.payload().properties().forEach(entry -> result.putIfAbsent(entry.getKey(), jsonValue(entry.getValue())));
    return result;
  }

  private Integer resolvedDuration(Treatment treatment) {
    if (treatment.durationMinutes() != null && treatment.durationMinutes() > 0) {
      return treatment.durationMinutes();
    }
    return catalog.scheme(treatment.schemeId())
        .map(Scheme::durationMinutes)
        .filter(value -> value != null && value > 0)
        .orElse(null);
  }

  private List<Map<String, Object>> diagnoses(JsonNode document) {
    List<Map<String, Object>> result = new ArrayList<>();
    JsonNode records = document.path("oncology").path("diagnosisRecords");
    if (!records.isArray()) records = document.path("oncology").path("diagnoses");
    if (records.isArray()) {
      int index = 0;
      for (JsonNode record : records) {
        if (record.path("archived").asBoolean(false)) continue;
        String id = text(record, "id", "diagnosisEntryId");
        if (id.isBlank()) id = "diagnosis-" + (++index);
        String label = diagnosisDisplay(record);
        if (!label.isBlank()) result.add(diagnosisOption(id, label));
      }
    }
    if (result.isEmpty()) {
      String label = document.path("oncology").path("diagnosis").asText("").trim();
      if (!label.isBlank()) result.add(diagnosisOption("oncology-current", label));
    }
    return result;
  }

  private Map<String, Object> diagnosisOption(String id, String label) {
    var assessment = compatibility.assess(label, "");
    return Map.of(
        "id", id,
        "nombre", label,
        "activo", "1",
        "protocolGroup", assessment.diagnosisGroup(),
        "protocolGroupLabel", assessment.diagnosisGroupLabel());
  }

  private String diagnosisLabel(JsonNode document, String id) {
    return diagnoses(document).stream()
        .filter(item -> id.equals(item.get("id")))
        .map(item -> String.valueOf(item.get("nombre")))
        .findFirst().orElse("");
  }

  static String diagnosisDisplay(JsonNode record) {
    JsonNode classifications = record.path("diagnosticClassifications");
    JsonNode snomed = classifications.path("snomed");
    JsonNode cie10 = classifications.path("cie10");
    JsonNode ajcc = classifications.path("ajcc");
    JsonNode tnm = record.path("tnm");

    String diagnosis = text(
        record, "diagnosis", "diagnostico", "snomed", "cie10", "tipoDiagnostico", "name");
    if (diagnosis.isBlank()) {
      diagnosis = text(snomed, "display", "freeText", "sourceDisplay");
    }
    if (diagnosis.isBlank()) {
      diagnosis = text(cie10, "display", "freeText", "sourceDisplay");
    }
    if (diagnosis.isBlank()) {
      diagnosis = text(record, "topography", "topografia");
    }
    if (diagnosis.isBlank()) {
      diagnosis = text(tnm, "siteDisplay");
    }
    if (diagnosis.isBlank()) {
      diagnosis = text(ajcc, "display", "freeText");
    }
    if (diagnosis.isBlank()) return "";

    String code = text(record, "cie10Codigo", "code");
    if (code.isBlank()) code = text(cie10, "code");
    String stage = text(record, "stage", "estadio");
    if (stage.isBlank()) stage = text(tnm, "stage", "stageGroup");

    StringBuilder result = new StringBuilder(diagnosis);
    if (!code.isBlank() && !normalize(diagnosis).contains(normalize(code))) {
      result.append(" · CIE-10 ").append(code);
    }
    if (!stage.isBlank()) result.append(" · Estadio ").append(stage);
    return result.toString();
  }

  private ObjectNode createDetail(
      String treatmentId, long patientId, Scheme scheme, int initialCycle, int cycleCount,
      LocalDate firstCycle, int cycleDays) {
    return createDetail(
        treatmentId, patientId, scheme, initialCycle, cycleCount,
        firstCycle, cycleDays, null);
  }

  private ObjectNode createDetail(
      String treatmentId, long patientId, Scheme scheme, int initialCycle, int cycleCount,
      LocalDate firstCycle, int cycleDays, DosingContext dosing) {
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
    for (int number = initialCycle; number < initialCycle + cycleCount; number++) {
      ObjectNode cycle = cycles.addObject();
      cycle.put("number", number);
      LocalDate planned = cycleDays > 0 ? firstCycle.plusDays((long) (number - initialCycle) * cycleDays) : firstCycle;
      cycle.put("plannedDate", planned.toString());
      cycle.put("date", planned.toString());
      cycle.set("drugs", extractDrugs(scheme.definition(), number, dosing));
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

  private ArrayNode extractDrugs(
      JsonNode definition, int cycleNumber, DosingContext dosing) {
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
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
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

  private ObjectNode treatmentEvolution(Treatment treatment, JsonNode input, SessionPrincipal actor) {
    String createdAt = treatment.createdAt().toString();
    ObjectNode evolution = mapper.createObjectNode();
    evolution.put("id", "treatment-evolution-" + treatment.id());
    evolution.put("date", treatment.createdOn().toString());
    evolution.put("datePrecision", "day");
    evolution.put("author", treatment.oncologist().isBlank() ? actor.displayName() : treatment.oncologist());
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
    List<String> lines = new ArrayList<>();
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
    audit.put("lastName", actor.displayName());
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

  private List<Map<String, Object>> simpleOptions(String... values) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (String value : values) result.add(Map.of("id", value, "nombre", value, "activo", "1"));
    return result;
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
    return normalized.equals("firmado")
        || normalized.equals("signed")
        || normalized.startsWith("firmado documento");
  }

  private int boundedInt(JsonNode input, int min, int max, int fallback, String... keys) {
    String value = text(input, keys);
    if (value.isBlank()) return fallback;
    try {
      int number = Integer.parseInt(value);
      if (number < min || number > max) throw new NumberFormatException();
      return number;
    } catch (NumberFormatException ignored) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Un valor numérico del tratamiento es inválido.");
    }
  }

  private LocalDate date(JsonNode input, LocalDate fallback, String... keys) {
    String value = text(input, keys);
    if (value.isBlank()) return fallback;
    for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE, ARGENTINE_DATE)) {
      try {
        return LocalDate.parse(value, formatter);
      } catch (DateTimeParseException ignored) {
      }
    }
    throw new ApiException(HttpStatus.BAD_REQUEST, "Una fecha del tratamiento es inválida.");
  }

  private static String text(JsonNode node, String... keys) {
    for (String key : keys) {
      JsonNode value = node.path(key);
      if (!value.isMissingNode() && !value.isNull()) {
        String text = value.asText("").trim();
        if (!text.isBlank()) return text;
      }
    }
    return "";
  }

  private static String normalize(String value) {
    return java.text.Normalizer.normalize(value == null ? "" : value, java.text.Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
  }

  private DosingContext dosingContext(JsonNode input, Scheme scheme) {
    if (!input.path("requirementsConfirmed").asBoolean(false)) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "Confirme que verificó los requisitos y datos de cálculo del esquema.");
    }
    String definition = normalize(scheme.definition().toString());
    boolean calvert = definition.contains("calvert")
        || normalize(scheme.name()).contains("carboplatino");
    boolean surface = definition.contains("superficie corporal")
        || definition.contains("mg/m2") || definition.contains("mg/m²");
    boolean weight = surface || calvert
        || definition.contains("\"calculoDosis\":\"Peso\"".toLowerCase(Locale.ROOT));
    boolean calcium = normalize(scheme.name()).matches(".*(zoled|denosumab|bifosfonat).*");

    double weightKg = weight ? requiredNumber(input, "Peso", "peso", "weight") : number(input, "peso", "weight");
    if (weightKg > 500) {
      throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Revise el peso; debe expresarse en kg.");
    }
    double heightCm = surface ? requiredNumber(input, "Talla", "talla", "height") : number(input, "talla", "height");
    if (surface && (heightCm < 20 || heightCm > 260)) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "La talla debe expresarse en centímetros y estar entre 20 y 260 cm.");
    }
    double creatinine = calvert
        ? requiredNumber(input, "Creatinina", "creatinina", "creatinine") : number(input, "creatinina", "creatinine");
    double gfr = calvert
        ? requiredNumber(input, "TFG", "tfg", "gfr") : number(input, "tfg", "gfr");
    double targetAuc = calvert
        ? requiredNumber(input, "Target AUC", "targetAUC", "targetAuc") : number(input, "targetAUC", "targetAuc");
    if (calvert && (targetAuc < 1 || targetAuc > 12)) {
      throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Revise el Target AUC informado.");
    }
    double calciumValue = calcium
        ? requiredNumber(input, "Calcio", "calcio", "calcium") : number(input, "calcio", "calcium");
    double albumin = calcium
        ? requiredNumber(input, "Albúmina", "albumina", "albumin") : number(input, "albumina", "albumin");
    double bodySurface = surface
        ? 0.007184 * Math.pow(weightKg, 0.425) * Math.pow(heightCm, 0.725)
        : 0;
    return new DosingContext(
        weightKg, heightCm, roundDose(bodySurface), creatinine, gfr, targetAuc,
        calciumValue, albumin);
  }

  private DoseCalculation calculateDose(
      String baseDoseText, String method, DosingContext dosing) {
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

  private double requiredNumber(JsonNode input, String label, String... keys) {
    double value = number(input, keys);
    if (!(value > 0)) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "Complete " + label + " con un valor mayor a cero.");
    }
    return value;
  }

  private double number(JsonNode input, String... keys) {
    for (String key : keys) {
      JsonNode value = input.path(key);
      if (value.isMissingNode() || value.isNull()) continue;
      double parsed = parseNumber(value.asText(""));
      if (Double.isFinite(parsed)) return parsed;
    }
    return 0;
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
    return minutes < 60 ? minutes + " min"
        : (minutes / 60) + " h" + (minutes % 60 == 0 ? "" : " " + (minutes % 60) + " min");
  }

  private Object jsonValue(JsonNode value) {
    return mapper.convertValue(value, Object.class);
  }

  public record Creation(
      Map<String, Object> treatment,
      ObjectNode evolution,
      long documentRevision,
      String createdAt,
      boolean idempotentReplay) {
  }

  private record DosingContext(
      double weightKg, double heightCm, double bodySurface,
      double creatinine, double gfr, double targetAuc,
      double calcium, double albumin) {
  }

  private record DoseCalculation(String doseText, String status, String trace) {
  }
}
