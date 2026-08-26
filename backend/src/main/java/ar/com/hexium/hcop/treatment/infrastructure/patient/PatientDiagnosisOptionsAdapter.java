package ar.com.hexium.hcop.treatment.infrastructure.patient;

import ar.com.hexium.hcop.patient.application.port.in.PatientDocumentUseCase;
import ar.com.hexium.hcop.patient.domain.StoredDocument;
import ar.com.hexium.hcop.treatment.application.port.out.PatientDiagnosisOptionsPort;
import ar.com.hexium.hcop.treatment.domain.DiagnosisOption;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Extrae los diagnósticos guardados de la historia con el mismo rótulo enriquecido (código
 * CIE-10 + estadio) que usaba {@code TreatmentService.diagnosisDisplay} — más rico que la
 * proyección de {@code diagnosis.application.port.in.DiagnosisUseCase}, por eso no se reusa ese
 * puerto de otro módulo (ver DECISIONES-F3.md).
 */
@Component
public class PatientDiagnosisOptionsAdapter implements PatientDiagnosisOptionsPort {
  private final PatientDocumentUseCase documents;

  public PatientDiagnosisOptionsAdapter(PatientDocumentUseCase documents) {
    this.documents = documents;
  }

  @Override
  public List<DiagnosisOption> diagnosisOptions(long patientId) {
    StoredDocument stored = documents.require(patientId);
    return diagnoses((JsonNode) stored.document());
  }

  private List<DiagnosisOption> diagnoses(JsonNode document) {
    List<DiagnosisOption> result = new ArrayList<>();
    JsonNode records = document.path("oncology").path("diagnosisRecords");
    if (!records.isArray()) records = document.path("oncology").path("diagnoses");
    if (records.isArray()) {
      int index = 0;
      for (JsonNode record : records) {
        if (record.path("archived").asBoolean(false)) continue;
        String id = text(record, "id", "diagnosisEntryId");
        if (id.isBlank()) id = "diagnosis-" + (++index);
        String label = diagnosisDisplay(record);
        if (!label.isBlank()) result.add(new DiagnosisOption(id, label));
      }
    }
    if (result.isEmpty()) {
      String label = document.path("oncology").path("diagnosis").asText("").trim();
      if (!label.isBlank()) result.add(new DiagnosisOption("oncology-current", label));
    }
    return result;
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

  private static String text(JsonNode node, String... keys) {
    for (String key : keys) {
      String value = node.path(key).asText("").trim();
      if (!value.isBlank()) return value;
    }
    return "";
  }

  private static String normalize(String value) {
    return java.text.Normalizer.normalize(value == null ? "" : value, java.text.Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT).trim();
  }
}
