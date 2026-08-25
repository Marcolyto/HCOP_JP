package ar.com.hexium.hcop.workflow;

import ar.com.hexium.hcop.auth.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class TreatmentWorkflowController {
  private final TreatmentWorkflowService workflows;
  private final AuthContext auth;

  public TreatmentWorkflowController(TreatmentWorkflowService workflows, AuthContext auth) {
    this.workflows = workflows;
    this.auth = auth;
  }

  @PostMapping("/api/clinical/treatments/{patientId}/{treatmentId}/suspend")
  Map<String, Object> suspend(
      @PathVariable long patientId, @PathVariable String treatmentId,
      @RequestBody JsonNode body, HttpServletRequest request) {
    auth.requirePermission(request, "workflow.suspend");
    return workflows.suspend(patientId, treatmentId, body, auth.require(request));
  }

  @PostMapping("/api/clinical/treatments/{patientId}/{treatmentId}/resume")
  Map<String, Object> resume(
      @PathVariable long patientId, @PathVariable String treatmentId,
      @RequestBody JsonNode body, HttpServletRequest request) {
    auth.requirePermission(request, "workflow.resume");
    return workflows.resume(patientId, treatmentId, body, auth.require(request));
  }

  @PostMapping("/api/clinical/treatment-workflow-requests")
  ResponseEntity<Map<String, Object>> create(
      @RequestBody JsonNode body, HttpServletRequest request) {
    String type = body.path("type").asText("");
    auth.requirePermission(request, "prescription_request".equals(type)
        ? "workflow.request-prescription" : "workflow.request-continuity");
    return ResponseEntity.status(HttpStatus.CREATED).body(
        workflows.createRequest(body, auth.require(request)));
  }

  @GetMapping("/api/clinical/treatment-workflow-requests/inbox")
  Map<String, Object> inbox(HttpServletRequest request) {
    var items = workflows.inbox(auth.require(request));
    return Map.of("ok", true, "items", items, "requests", items, "total", items.size());
  }

  @PatchMapping("/api/clinical/treatment-workflow-requests/{id}/seen")
  Map<String, Object> seen(@PathVariable long id, HttpServletRequest request) {
    return workflows.seen(id, auth.require(request));
  }

  @PostMapping("/api/clinical/treatment-workflow-requests/{id}/resolve")
  Map<String, Object> resolve(
      @PathVariable long id, @RequestBody JsonNode body, HttpServletRequest request) {
    return workflows.resolve(id, body, auth.require(request));
  }
}
