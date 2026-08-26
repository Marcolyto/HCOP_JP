package ar.com.hexium.hcop.qr.infrastructure.web;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.qr.application.port.in.QrUseCase;
import ar.com.hexium.hcop.qr.application.port.in.QrUseCase.ScanCommand;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class QrWorkflowController {
  private final QrUseCase qr;
  private final QrJsonMapper json;
  private final AuthContext auth;

  public QrWorkflowController(QrUseCase qr, QrJsonMapper json, AuthContext auth) {
    this.qr = qr;
    this.json = json;
    this.auth = auth;
  }

  @GetMapping(
      value = "/api/clinical/patients/{patientId}/treatments/{treatmentId}/documents/qr",
      produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> document(
      @PathVariable long patientId,
      @PathVariable String treatmentId,
      @RequestParam int cycle,
      @RequestParam(defaultValue = "1") int applicationDay,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.day-hospital.view");
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
        .body(qr.printableHtml(patientId, treatmentId, cycle, applicationDay));
  }

  @PostMapping("/api/clinical/qr-scans")
  Map<String, Object> scan(@RequestBody JsonNode body, HttpServletRequest request) {
    auth.requirePermission(request, "application.administration.manage");
    SessionPrincipal actor = auth.require(request);
    return json.scanResult(qr.scan(new ScanCommand(
        body.path("code").asText(""), body.path("operationId").asText(""),
        actor.userId(), actor.displayName())));
  }
}
