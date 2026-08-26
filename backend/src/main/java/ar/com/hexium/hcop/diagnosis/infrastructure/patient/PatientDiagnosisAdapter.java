package ar.com.hexium.hcop.diagnosis.infrastructure.patient;

import ar.com.hexium.hcop.diagnosis.application.port.out.PatientDiagnosisPort;
import ar.com.hexium.hcop.diagnosis.application.service.DiagnosisFailure;
import ar.com.hexium.hcop.diagnosis.domain.DiagnosisRecord;
import ar.com.hexium.hcop.patient.application.port.in.PatientDocumentUseCase;
import ar.com.hexium.hcop.patient.application.port.in.PatientUseCase;
import ar.com.hexium.hcop.patient.application.service.PatientFailure;
import ar.com.hexium.hcop.patient.domain.StoredDocument;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Único lugar del módulo que conoce el árbol JSON de la historia clínica. */
@Component
public class PatientDiagnosisAdapter implements PatientDiagnosisPort {
  private final PatientUseCase patients;
  private final PatientDocumentUseCase documents;

  public PatientDiagnosisAdapter(PatientUseCase patients, PatientDocumentUseCase documents) {
    this.patients = patients;
    this.documents = documents;
  }

  @Override
  public DiagnosisSnapshot snapshot(long patientId) {
    try {
      patients.require(patientId);
      StoredDocument stored = documents.require(patientId);
      return new DiagnosisSnapshot(stored.revision(), diagnosisRecords((JsonNode) stored.document()));
    } catch (PatientFailure failure) {
      throw new DiagnosisFailure(DiagnosisFailure.Type.NOT_FOUND, failure.getMessage());
    }
  }

  private List<DiagnosisRecord> diagnosisRecords(JsonNode document) {
    JsonNode source = document.path("oncology").path("diagnosisRecords");
    if (!source.isArray()) source = document.path("oncology").path("diagnoses");
    List<DiagnosisRecord> result = new ArrayList<>();
    if (source.isArray()) {
      int index = 0;
      for (JsonNode record : source) {
        if (record.path("archived").asBoolean(false)) continue;
        String id = text(record, "id", "diagnosisEntryId");
        if (id.isBlank()) id = "diagnosis-" + (++index);
        String display = text(record, "diagnosis", "diagnostico", "snomed", "cie10", "topography");
        if (display.isBlank()) continue;
        result.add(new DiagnosisRecord(
            id, display, text(record, "date", "fechaDiagnostico"), text(record, "stage", "estadio"),
            record));
      }
    }
    if (result.isEmpty()) {
      String display = document.path("oncology").path("diagnosis").asText("").trim();
      if (!display.isBlank()) {
        result.add(new DiagnosisRecord(
            "oncology-current", display,
            document.path("oncology").path("diagnosisDate").asText(""),
            document.path("oncology").path("stage").asText(""),
            null));
      }
    }
    return List.copyOf(result);
  }

  private String text(JsonNode node, String... keys) {
    for (String key : keys) {
      String value = node.path(key).asText("").trim();
      if (!value.isBlank()) return value;
    }
    return "";
  }
}
