package ar.com.hexium.hcop.treatment;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.catalog.TreatmentCatalogService;
import ar.com.hexium.hcop.treatment.TreatmentService.Creation;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class TreatmentController {
  private final TreatmentService treatments;
  private final TreatmentCatalogService catalog;
  private final AuthContext auth;

  public TreatmentController(
      TreatmentService treatments,
      TreatmentCatalogService catalog,
      AuthContext auth) {
    this.treatments = treatments;
    this.catalog = catalog;
    this.auth = auth;
  }

  @GetMapping("/api/clinical/patients/{patientId}/treatments")
  Map<String, Object> list(@PathVariable long patientId, HttpServletRequest request) {
    auth.requirePermission(request, "section.prescriptions.view");
    List<Map<String, Object>> oncology = treatments.list(patientId);
    return Map.of(
        "ok", true,
        "patientId", Long.toString(patientId),
        "oncology", oncology,
        "treatments", oncology,
        "nonOncology", List.of(),
        "procedures", List.of(),
        "referrals", List.of(),
        "total", oncology.size());
  }

  @PostMapping("/api/clinical/patients/{patientId}/treatments")
  ResponseEntity<Map<String, Object>> create(
      @PathVariable long patientId,
      @RequestBody JsonNode body,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.prescriptions.edit");
    SessionPrincipal actor = auth.require(request);
    Creation creation = treatments.create(patientId, body, actor);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("ok", true);
    result.put("id", creation.treatment().get("id"));
    result.put("treatment", creation.treatment());
    result.put("evolution", creation.evolution());
    result.put("evolutionCreated", !creation.idempotentReplay());
    result.put("idempotentReplay", creation.idempotentReplay());
    result.put("documentRevision", creation.documentRevision());
    result.put("createdAt", creation.createdAt());
    return ResponseEntity.status(
        creation.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
  }

  @GetMapping("/api/clinical/patients/{patientId}/treatment-options")
  Map<String, Object> options(@PathVariable long patientId, HttpServletRequest request) {
    auth.requirePermission(request, "section.prescriptions.view");
    return treatments.options(patientId);
  }

  @GetMapping("/api/clinical/patients/{patientId}/treatment-requirements/{schemeId}")
  Map<String, Object> requirements(
      @PathVariable long patientId,
      @PathVariable String schemeId,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.prescriptions.view");
    return treatments.requirements(patientId, schemeId);
  }

  @GetMapping("/api/clinical/patients/{patientId}/treatments/{treatmentId}/detail")
  Map<String, Object> detail(
      @PathVariable long patientId,
      @PathVariable String treatmentId,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.prescriptions.view");
    return treatments.detail(patientId, treatmentId);
  }

  @GetMapping("/api/clinical/schemes")
  Map<String, Object> schemes(
      @RequestParam(defaultValue = "") String q,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.protocols.view");
    List<Map<String, Object>> schemes = catalog.schemes(q);
    return Map.of("ok", true, "schemes", schemes, "total", schemes.size());
  }

  @GetMapping("/api/clinical/schemes/{id}/duration")
  Map<String, Object> duration(@PathVariable String id, HttpServletRequest request) {
    auth.requirePermission(request, "section.protocols.view");
    var scheme = catalog.scheme(id)
        .orElseThrow(() -> new ar.com.hexium.hcop.common.ApiException(
            HttpStatus.NOT_FOUND, "Esquema no encontrado."));
    return Map.of(
        "ok", true,
        "schemeId", scheme.id(),
        "schemeName", scheme.name(),
        "durationMinutes", scheme.durationMinutes() == null ? 0 : scheme.durationMinutes(),
        "estimatedDurationMinutes", scheme.durationMinutes() == null ? 0 : scheme.durationMinutes());
  }
}
