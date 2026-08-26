package ar.com.hexium.hcop.treatment.application.service;

import ar.com.hexium.hcop.catalog.application.port.in.TreatmentCatalogUseCase;
import ar.com.hexium.hcop.catalog.domain.TreatmentScheme;
import ar.com.hexium.hcop.treatment.application.port.in.TreatmentUseCase;
import ar.com.hexium.hcop.treatment.application.port.out.InfusionSummaryPort;
import ar.com.hexium.hcop.treatment.application.port.out.PatientDiagnosisOptionsPort;
import ar.com.hexium.hcop.treatment.application.port.out.TreatmentPatientPort;
import ar.com.hexium.hcop.treatment.application.port.out.TreatmentStore;
import ar.com.hexium.hcop.treatment.application.port.out.TreatmentStore.NewTreatmentDraft;
import ar.com.hexium.hcop.treatment.application.port.out.TreatmentStore.TreatmentCreationOutcome;
import ar.com.hexium.hcop.treatment.domain.DiagnosisOption;
import ar.com.hexium.hcop.treatment.domain.Treatment;
import ar.com.hexium.hcop.treatment.domain.TreatmentPatientView;
import ar.com.hexium.hcop.treatment.domain.TreatmentProtocolCompatibility;
import ar.com.hexium.hcop.treatment.domain.TreatmentProtocolCompatibility.Assessment;
import ar.com.hexium.hcop.treatment.domain.WorkflowState;
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
import java.util.UUID;

public final class TreatmentApplicationService implements TreatmentUseCase {
  private static final DateTimeFormatter ARGENTINE_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");
  private final TreatmentStore store;
  private final TreatmentCatalogUseCase catalog;
  private final TreatmentPatientPort patients;
  private final PatientDiagnosisOptionsPort diagnosisOptions;
  private final InfusionSummaryPort infusions;
  private final TreatmentProtocolCompatibility compatibility;
  private final Clock clock;

  public TreatmentApplicationService(
      TreatmentStore store, TreatmentCatalogUseCase catalog, TreatmentPatientPort patients,
      PatientDiagnosisOptionsPort diagnosisOptions, InfusionSummaryPort infusions,
      TreatmentProtocolCompatibility compatibility, Clock clock) {
    this.store = store;
    this.catalog = catalog;
    this.patients = patients;
    this.diagnosisOptions = diagnosisOptions;
    this.infusions = infusions;
    this.compatibility = compatibility;
    this.clock = clock;
  }

  @Override
  public List<Map<String, Object>> list(long patientId) {
    patients.requirePatient(patientId);
    Map<String, WorkflowState> workflowStates = store.workflowStates(patientId);
    return store.list(patientId).stream()
        .map(treatment -> store.view(
            treatment, workflowStates.get(treatment.id()), resolvedDuration(treatment)))
        .toList();
  }

  @Override
  public Map<String, Object> options(long patientId) {
    patients.requirePatient(patientId);
    List<Map<String, Object>> diagnoses = diagnosisOptions.diagnosisOptions(patientId).stream()
        .map(this::diagnosisOptionView)
        .toList();
    Map<String, Object> options = new LinkedHashMap<>();
    options.put("diagnoses", diagnoses);
    options.put("diagnosticos", diagnoses);
    List<Map<String, Object>> schemes = catalog.schemes("").stream().map(item -> {
      Map<String, Object> view = schemeView(item);
      var assessment = compatibility.assess("", item.name());
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

  @Override
  public Map<String, Object> requirements(long patientId, String schemeId) {
    TreatmentPatientView patient = patients.requirePatient(patientId);
    TreatmentScheme scheme = catalog.scheme(schemeId)
        .orElseThrow(() -> new TreatmentFailure(TreatmentFailure.Type.NOT_FOUND, "El esquema no existe."));
    String definitionText = String.valueOf(scheme.definition()).toLowerCase(Locale.ROOT);
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

  @Override
  public CreationResult create(CreateTreatmentCommand command) {
    patients.requirePatient(command.patientId());
    String diagnosisId = orEmpty(command.diagnosisId());
    if (diagnosisId.isBlank()) invalid("Seleccione un diagnóstico guardado.");
    String diagnosis = diagnosisOptions.diagnosisOptions(command.patientId()).stream()
        .filter(option -> diagnosisId.equals(option.id()))
        .map(DiagnosisOption::label)
        .findFirst().orElse("");
    if (diagnosis.isBlank()) throw unprocessable("El diagnóstico no pertenece al paciente.");
    String schemeId = orEmpty(command.schemeId());
    TreatmentScheme scheme = catalog.scheme(schemeId)
        .orElseThrow(() -> new TreatmentFailure(TreatmentFailure.Type.INVALID, "Seleccione un esquema válido."));
    Assessment protocolAssessment = compatibility.assess(diagnosis, scheme.name());
    if (protocolAssessment.mismatch()
        && (!command.protocolMismatchConfirmed() || orEmpty(command.protocolMismatchReason()).length() < 10)) {
      throw unprocessable(
          "El esquema pertenece a " + protocolAssessment.protocolGroupLabel()
              + " y el diagnóstico a " + protocolAssessment.diagnosisGroupLabel()
              + ". Confirme la excepción y documente el motivo clínico.");
    }
    int cycleCount = boundedInt(command.cycleCountRaw(), 1, 500, 1);
    int initialCycle = boundedInt(command.initialCycleRaw(), 1, 500, 1);
    int cycleDays = boundedInt(command.cycleDaysRaw(), 0, 3650, Math.max(0, scheme.cycleDays()));
    if ((long) initialCycle + cycleCount - 1 > 500) {
      invalid("El ciclo inicial más la cantidad de ciclos no puede superar el ciclo 500.");
    }
    if (cycleCount > 1 && cycleDays < 1) {
      invalid("Un tratamiento con más de un ciclo necesita un intervalo mayor a cero días.");
    }
    DosingValues dosing = dosingContext(command, scheme);
    LocalDate createdOn = date(command.createdOnRaw(), LocalDate.now(clock));
    LocalDate firstCycle = date(command.firstCycleDateRaw(), createdOn);
    String id = "trt-" + UUID.randomUUID();
    String oncologist = orEmpty(command.oncologistRaw());
    if (oncologist.isBlank()) oncologist = command.actorDisplayName();
    String consent = orEmpty(command.consentRaw());
    if (isSignedConsent(consent) && !command.consentAvailable()) {
      consent = "Firmado · documento pendiente";
    }
    NewTreatmentDraft draft = new NewTreatmentDraft(
        id, command.patientId(), diagnosisId, diagnosis, schemeId, scheme, initialCycle,
        cycleCount, cycleDays, orEmpty(command.treatmentType()), orEmpty(command.intent()),
        oncologist, consent, command.consentAvailable(), createdOn, firstCycle,
        protocolAssessment.diagnosisGroup(), protocolAssessment.protocolGroup(),
        protocolAssessment.mismatch() && command.protocolMismatchConfirmed(),
        protocolAssessment.mismatch() ? orEmpty(command.protocolMismatchReason()) : "",
        dosing.weightKg(), dosing.heightCm(), dosing.bodySurface(), dosing.gfr(), dosing.targetAuc(),
        orEmpty(command.clinicalEntryId()), command.rawBody(), command.actorId(),
        command.actorDisplayName());
    TreatmentCreationOutcome outcome = store.insert(draft);
    return new CreationResult(
        outcome.treatmentView(), outcome.evolution(), outcome.documentRevision(),
        outcome.createdAt(), outcome.idempotentReplay());
  }

  @Override
  public Map<String, Object> detail(long patientId, String treatmentId) {
    Treatment treatment = store.find(patientId, treatmentId)
        .orElseThrow(() -> new TreatmentFailure(TreatmentFailure.Type.NOT_FOUND, "Tratamiento no encontrado."));
    TreatmentScheme scheme = catalog.scheme(treatment.schemeId()).orElse(null);
    List<Map<String, Object>> sessions = infusions.list(patientId).stream()
        .filter(item -> treatmentId.equals(String.valueOf(item.get("treatmentId"))))
        .toList();
    Object detail = store.enrichedDetail(treatmentId, scheme, sessions);
    return Map.of(
        "ok", true, "patientId", Long.toString(patientId), "treatmentId", treatmentId, "detail", detail);
  }

  private Integer resolvedDuration(Treatment treatment) {
    if (treatment.durationMinutes() != null && treatment.durationMinutes() > 0) {
      return treatment.durationMinutes();
    }
    return catalog.scheme(treatment.schemeId())
        .map(TreatmentScheme::durationMinutes)
        .filter(value -> value != null && value > 0)
        .orElse(null);
  }

  private Map<String, Object> diagnosisOptionView(DiagnosisOption option) {
    Assessment assessment = compatibility.assess(option.label(), "");
    return Map.of(
        "id", option.id(),
        "nombre", option.label(),
        "activo", "1",
        "protocolGroup", assessment.diagnosisGroup(),
        "protocolGroupLabel", assessment.diagnosisGroupLabel());
  }

  private Map<String, Object> schemeView(TreatmentScheme scheme) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", scheme.id());
    value.put("nombre", scheme.name());
    value.put("name", scheme.name());
    value.put("activo", "1");
    value.put("duracionCiclo", scheme.cycleDays() > 0 ? Integer.toString(scheme.cycleDays()) : "");
    value.put("cycleDays", scheme.cycleDays() > 0 ? scheme.cycleDays() : null);
    value.put("estimatedDurationMinutes", scheme.durationMinutes());
    value.put("durationMinutes", scheme.durationMinutes());
    value.put("estimatedDurationText", durationText(scheme.durationMinutes()));
    value.put("origin", scheme.custom() ? "custom" : "catalog");
    return value;
  }

  private String durationText(Integer minutes) {
    if (minutes == null || minutes < 1) return "";
    int hours = minutes / 60;
    int remainder = minutes % 60;
    if (hours == 0) return minutes + " min";
    if (remainder == 0) return hours + " h";
    return hours + " h " + remainder + " min";
  }

  private List<Map<String, Object>> simpleOptions(String... values) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (String value : values) result.add(Map.of("id", value, "nombre", value, "activo", "1"));
    return result;
  }

  private DosingValues dosingContext(CreateTreatmentCommand command, TreatmentScheme scheme) {
    if (!command.requirementsConfirmed()) {
      throw unprocessable("Confirme que verificó los requisitos y datos de cálculo del esquema.");
    }
    String definition = normalize(String.valueOf(scheme.definition()));
    boolean calvert = definition.contains("calvert") || normalize(scheme.name()).contains("carboplatino");
    boolean surface = definition.contains("superficie corporal")
        || definition.contains("mg/m2") || definition.contains("mg/m²");
    boolean weight = surface || calvert
        || definition.contains("\"calculodosis\":\"peso\"");
    boolean calcium = normalize(scheme.name()).matches(".*(zoled|denosumab|bifosfonat).*");

    double weightKg = weight ? requiredNumber(command.weightRaw(), "Peso") : number(command.weightRaw());
    if (weightKg > 500) throw unprocessable("Revise el peso; debe expresarse en kg.");
    double heightCm = surface ? requiredNumber(command.heightRaw(), "Talla") : number(command.heightRaw());
    if (surface && (heightCm < 20 || heightCm > 260)) {
      throw unprocessable("La talla debe expresarse en centímetros y estar entre 20 y 260 cm.");
    }
    if (calvert) requiredNumber(command.creatinineRaw(), "Creatinina");
    double gfr = calvert ? requiredNumber(command.gfrRaw(), "TFG") : number(command.gfrRaw());
    double targetAuc = calvert
        ? requiredNumber(command.targetAucRaw(), "Target AUC") : number(command.targetAucRaw());
    if (calvert && (targetAuc < 1 || targetAuc > 12)) throw unprocessable("Revise el Target AUC informado.");
    if (calcium) {
      requiredNumber(command.calciumRaw(), "Calcio");
      requiredNumber(command.albuminRaw(), "Albúmina");
    }
    double bodySurface = surface
        ? 0.007184 * Math.pow(weightKg, 0.425) * Math.pow(heightCm, 0.725)
        : 0;
    return new DosingValues(weightKg, heightCm, roundDose(bodySurface), gfr, targetAuc);
  }

  private double requiredNumber(String raw, String label) {
    double value = number(raw);
    if (!(value > 0)) throw unprocessable("Complete " + label + " con un valor mayor a cero.");
    return value;
  }

  private double number(String raw) {
    return parseNumber(raw);
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

  private boolean isSignedConsent(String value) {
    String normalized = normalize(value);
    return normalized.equals("firmado") || normalized.equals("signed")
        || normalized.startsWith("firmado documento");
  }

  private int boundedInt(String raw, int min, int max, int fallback) {
    String value = orEmpty(raw);
    if (value.isBlank()) return fallback;
    try {
      int number = Integer.parseInt(value);
      if (number < min || number > max) throw new NumberFormatException();
      return number;
    } catch (NumberFormatException ignored) {
      throw new TreatmentFailure(TreatmentFailure.Type.INVALID, "Un valor numérico del tratamiento es inválido.");
    }
  }

  private LocalDate date(String raw, LocalDate fallback) {
    String value = orEmpty(raw);
    if (value.isBlank()) return fallback;
    for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE, ARGENTINE_DATE)) {
      try {
        return LocalDate.parse(value, formatter);
      } catch (DateTimeParseException ignored) {
      }
    }
    throw new TreatmentFailure(TreatmentFailure.Type.INVALID, "Una fecha del tratamiento es inválida.");
  }

  private String orEmpty(String value) {
    return value == null ? "" : value.trim();
  }

  private String normalize(String value) {
    return java.text.Normalizer.normalize(value == null ? "" : value, java.text.Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
  }

  private void invalid(String message) {
    throw new TreatmentFailure(TreatmentFailure.Type.INVALID, message);
  }

  private TreatmentFailure unprocessable(String message) {
    return new TreatmentFailure(TreatmentFailure.Type.UNPROCESSABLE, message);
  }

  private record DosingValues(
      double weightKg, double heightCm, double bodySurface, double gfr, double targetAuc) {
  }
}
