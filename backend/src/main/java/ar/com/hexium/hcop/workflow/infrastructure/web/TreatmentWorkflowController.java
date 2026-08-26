package ar.com.hexium.hcop.workflow.infrastructure.web;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.workflow.application.port.in.TreatmentWorkflowUseCase;
import ar.com.hexium.hcop.workflow.application.port.in.TreatmentWorkflowUseCase.CreateRequestCommand;
import ar.com.hexium.hcop.workflow.application.port.in.TreatmentWorkflowUseCase.ManagementActionResult;
import ar.com.hexium.hcop.workflow.application.port.in.TreatmentWorkflowUseCase.RequestActionResult;
import ar.com.hexium.hcop.workflow.application.port.in.TreatmentWorkflowUseCase.ResolveCommand;
import ar.com.hexium.hcop.workflow.application.port.in.TreatmentWorkflowUseCase.ResumeCommand;
import ar.com.hexium.hcop.workflow.application.port.in.TreatmentWorkflowUseCase.SuspendCommand;
import ar.com.hexium.hcop.workflow.domain.WorkflowRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class TreatmentWorkflowController {
  private final TreatmentWorkflowUseCase workflows;
  private final TreatmentWorkflowJsonMapper json;
  private final AuthContext auth;

  public TreatmentWorkflowController(
      TreatmentWorkflowUseCase workflows, TreatmentWorkflowJsonMapper json, AuthContext auth) {
    this.workflows = workflows;
    this.json = json;
    this.auth = auth;
  }

  @PostMapping("/api/clinical/treatments/{patientId}/{treatmentId}/suspend")
  Map<String, Object> suspend(
      @PathVariable long patientId, @PathVariable String treatmentId,
      @RequestBody JsonNode body, HttpServletRequest request) {
    auth.requirePermission(request, "workflow.suspend");
    SessionPrincipal actor = auth.require(request);
    ManagementActionResult result = workflows.suspend(new SuspendCommand(
        patientId, treatmentId, text(body, "kind"), text(body, "reason"),
        body.has("cycleNumber") ? body.path("cycleNumber").asInt(-1) : -1,
        text(body, "resumeDate"), actor.userId(), actor.displayName()));
    return json.actionResult(json.stateView(result.state()), result.evolution(), result.documentRevision());
  }

  @PostMapping("/api/clinical/treatments/{patientId}/{treatmentId}/resume")
  Map<String, Object> resume(
      @PathVariable long patientId, @PathVariable String treatmentId,
      @RequestBody JsonNode body, HttpServletRequest request) {
    auth.requirePermission(request, "workflow.resume");
    SessionPrincipal actor = auth.require(request);
    ManagementActionResult result = workflows.resume(new ResumeCommand(
        patientId, treatmentId, text(body, "reason"), actor.userId(), actor.displayName()));
    return json.actionResult(json.stateView(result.state()), result.evolution(), result.documentRevision());
  }

  @PostMapping("/api/clinical/treatment-workflow-requests")
  ResponseEntity<Map<String, Object>> create(
      @RequestBody JsonNode body, HttpServletRequest request) {
    String type = body.path("type").asText("");
    auth.requirePermission(request, "prescription_request".equals(type)
        ? "workflow.request-prescription" : "workflow.request-continuity");
    SessionPrincipal actor = auth.require(request);
    RequestActionResult result = workflows.createRequest(new CreateRequestCommand(
        type, body.path("patientId").asText(""), text(body, "treatmentId"),
        body.path("cycleNumber").asInt(1), body.path("assignedToUserId").asText(""),
        text(body, "message"), actor.userId(), actor.displayName()));
    return ResponseEntity.status(HttpStatus.CREATED).body(json.actionResult(
        json.requestView(result.request()), result.evolution(), result.documentRevision()));
  }

  @GetMapping("/api/clinical/treatment-workflow-requests/inbox")
  Map<String, Object> inbox(HttpServletRequest request) {
    SessionPrincipal actor = auth.require(request);
    var items = workflows.inbox(actor.userId()).stream().map(json::requestView).toList();
    return Map.of("ok", true, "items", items, "requests", items, "total", items.size());
  }

  @PatchMapping("/api/clinical/treatment-workflow-requests/{id}/seen")
  Map<String, Object> seen(@PathVariable long id, HttpServletRequest request) {
    SessionPrincipal actor = auth.require(request);
    WorkflowRequest item = workflows.seen(id, actor.userId());
    return Map.of("ok", true, "item", json.requestView(item));
  }

  @PostMapping("/api/clinical/treatment-workflow-requests/{id}/resolve")
  Map<String, Object> resolve(
      @PathVariable long id, @RequestBody JsonNode body, HttpServletRequest request) {
    SessionPrincipal actor = auth.require(request);
    RequestActionResult result = workflows.resolveRequest(new ResolveCommand(
        id, text(body, "resolution"), text(body, "reason"), text(body, "resumeDate"),
        actor.userId(), actor.displayName(), actor::hasPermission));
    return json.actionResult(json.requestView(result.request()), result.evolution(), result.documentRevision());
  }

  private String text(JsonNode body, String key) {
    return body.path(key).asText("").trim();
  }
}
