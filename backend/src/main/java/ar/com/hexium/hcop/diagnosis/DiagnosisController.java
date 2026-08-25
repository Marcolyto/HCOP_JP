package ar.com.hexium.hcop.diagnosis;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.patient.PatientDocumentRepository.StoredDocument;
import ar.com.hexium.hcop.patient.PatientDocumentService;
import ar.com.hexium.hcop.patient.PatientService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class DiagnosisController {
  private final PatientService patients;
  private final PatientDocumentService documents;
  private final AuthContext auth;

  public DiagnosisController(
      PatientService patients,
      PatientDocumentService documents,
      AuthContext auth) {
    this.patients = patients;
    this.documents = documents;
    this.auth = auth;
  }

  @GetMapping("/api/clinical/patients/{patientId}/diagnosis")
  Map<String, Object> list(@PathVariable long patientId, HttpServletRequest request) {
    auth.requirePermission(request, "section.history.view");
    patients.require(patientId);
    StoredDocument stored = documents.require(patientId);
    List<Map<String, Object>> result = diagnosisRecords(stored.document());
    return Map.of(
        "ok", true,
        "patientId", Long.toString(patientId),
        "diagnoses", result,
        "revision", stored.revision());
  }

  @PutMapping("/api/clinical/patients/{patientId}/diagnosis")
  Map<String, Object> link(
      @PathVariable long patientId,
      @RequestBody JsonNode body,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.history.edit");
    patients.require(patientId);
    StoredDocument stored = documents.require(patientId);
    long expected = body.path("expectedRevision").asLong(0);
    if (expected > 0 && expected != stored.revision()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "La historia fue modificada en otra ventana.",
          "VERSION_CONFLICT");
    }
    String requestedId = body.path("diagnosisEntryId").asText("").trim();
    List<Map<String, Object>> records = diagnosisRecords(stored.document());
    Map<String, Object> selected = requestedId.isBlank()
        ? records.stream().findFirst().orElse(null)
        : records.stream().filter(item -> requestedId.equals(item.get("id"))).findFirst().orElse(null);
    if (selected == null) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "El diagnóstico guardado no está disponible para Tratamientos.");
    }
    return Map.of(
        "ok", true,
        "patientId", Long.toString(patientId),
        "diagnosis", selected,
        "revision", stored.revision(),
        "linked", true);
  }

  private List<Map<String, Object>> diagnosisRecords(JsonNode document) {
    JsonNode source = document.path("oncology").path("diagnosisRecords");
    if (!source.isArray()) source = document.path("oncology").path("diagnoses");
    java.util.ArrayList<Map<String, Object>> result = new java.util.ArrayList<>();
    if (source.isArray()) {
      int index = 0;
      for (JsonNode record : source) {
        if (record.path("archived").asBoolean(false)) continue;
        String id = text(record, "id", "diagnosisEntryId");
        if (id.isBlank()) id = "diagnosis-" + (++index);
        String display = text(record, "diagnosis", "diagnostico", "snomed", "cie10", "topography");
        if (display.isBlank()) continue;
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("nombre", display);
        item.put("diagnostico", display);
        item.put("date", text(record, "date", "fechaDiagnostico"));
        item.put("stage", text(record, "stage", "estadio"));
        item.put("activo", "1");
        item.put("source", record);
        result.add(item);
      }
    }
    if (result.isEmpty()) {
      String display = document.path("oncology").path("diagnosis").asText("").trim();
      if (!display.isBlank()) {
        result.add(Map.of(
            "id", "oncology-current",
            "nombre", display,
            "diagnostico", display,
            "date", document.path("oncology").path("diagnosisDate").asText(""),
            "stage", document.path("oncology").path("stage").asText(""),
            "activo", "1"));
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
