package ar.com.hexium.hcop.diagnosis.infrastructure.web;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.diagnosis.application.port.in.DiagnosisUseCase;
import ar.com.hexium.hcop.diagnosis.application.port.in.DiagnosisUseCase.DiagnosisLinkResult;
import ar.com.hexium.hcop.diagnosis.application.port.in.DiagnosisUseCase.DiagnosisListView;
import ar.com.hexium.hcop.diagnosis.domain.DiagnosisRecord;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class DiagnosisController {
  private final DiagnosisUseCase diagnoses;
  private final AuthContext auth;

  public DiagnosisController(DiagnosisUseCase diagnoses, AuthContext auth) {
    this.diagnoses = diagnoses;
    this.auth = auth;
  }

  @GetMapping("/api/clinical/patients/{patientId}/diagnosis")
  Map<String, Object> list(@Parameter(description = "Id interno del paciente")
  @PathVariable long patientId, HttpServletRequest request) {
    auth.requirePermission(request, "section.history.view");
    DiagnosisListView view = diagnoses.list(patientId);
    return Map.of(
        "ok", true,
        "patientId", Long.toString(patientId),
        "diagnoses", view.diagnoses().stream().map(this::view).toList(),
        "revision", view.revision());
  }

  @PutMapping("/api/clinical/patients/{patientId}/diagnosis")
  Map<String, Object> link(
      @Parameter(description = "Id interno del paciente")
      @PathVariable long patientId,
      @RequestBody JsonNode body,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.history.edit");
    long expected = body.path("expectedRevision").asLong(0);
    String requestedId = body.path("diagnosisEntryId").asText("").trim();
    DiagnosisLinkResult result = diagnoses.link(patientId, requestedId, expected);
    return Map.of(
        "ok", true,
        "patientId", Long.toString(patientId),
        "diagnosis", view(result.diagnosis()),
        "revision", result.revision(),
        "linked", true);
  }

  private Map<String, Object> view(DiagnosisRecord record) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", record.id());
    item.put("nombre", record.display());
    item.put("diagnostico", record.display());
    item.put("date", record.date());
    item.put("stage", record.stage());
    item.put("activo", "1");
    if (record.source() != null) item.put("source", record.source());
    return item;
  }
}
