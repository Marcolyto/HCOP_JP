package ar.com.hexium.hcop.infusion.infrastructure.web;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.infusion.application.port.in.InfusionUseCase;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class InfusionController {
  private final InfusionUseCase infusions;
  private final AuthContext auth;

  public InfusionController(InfusionUseCase infusions, AuthContext auth) {
    this.infusions = infusions;
    this.auth = auth;
  }

  @GetMapping("/api/clinical/infusions")
  Map<String, Object> list(
      @Parameter(description = "Id del paciente a filtrar (opcional)")
      @RequestParam(required = false) Long patientId,
      @Parameter(description = "Fecha a filtrar (YYYY-MM-DD)")
      @RequestParam(required = false) LocalDate date,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.day-hospital.view");
    List<Map<String, Object>> result = infusions.list(patientId, date);
    return Map.of("ok", true, "infusions", result, "total", result.size());
  }

  @PostMapping("/api/clinical/infusions")
  ResponseEntity<Map<String, Object>> create(@RequestBody JsonNode body, HttpServletRequest request) {
    auth.requirePermission(request, "application.schedule.manage");
    SessionPrincipal actor = auth.require(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of("ok", true, "infusion", infusions.create(body, actor.userId(), actor.displayName())));
  }

  @PatchMapping("/api/clinical/infusions/{id}")
  Map<String, Object> update(
      @Parameter(description = "Id de la aplicación (infusión) a actualizar")
      @PathVariable long id, @RequestBody JsonNode body, HttpServletRequest request) {
    auth.requirePermission(request, "application.schedule.manage");
    SessionPrincipal actor = auth.require(request);
    return Map.of("ok", true, "infusion", infusions.update(id, body, actor.userId(), actor.displayName()));
  }

  @GetMapping("/api/clinical/infusion-candidates")
  Map<String, Object> candidates(
      @Parameter(description = "Texto libre de búsqueda de candidatos")
      @RequestParam(defaultValue = "") String q,
      @Parameter(description = "true para incluir aplicaciones ya agendadas")
      @RequestParam(defaultValue = "false") boolean includeScheduled,
      @Parameter(
          description =
              "Si es true, devuelve sólo aplicaciones que ya cumplen los requisitos de Farmacia "
                  + "para recibir un turno; false también incluye las bloqueadas para seguimiento.")
      @RequestParam(defaultValue = "true") boolean onlySchedulingEligible,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.day-hospital.view");
    List<Map<String, Object>> result = infusions.candidates(q, includeScheduled, onlySchedulingEligible);
    return Map.of("ok", true, "candidates", result, "total", result.size());
  }

  @PatchMapping("/api/clinical/treatment-cycles/{patientId}/{treatmentId}/{cycleNumber}/logistics")
  Map<String, Object> logistics(
      @Parameter(description = "Id interno del paciente")
      @PathVariable long patientId, @Parameter(description = "Id del tratamiento")
      @PathVariable String treatmentId, @Parameter(description = "Número de ciclo del tratamiento")
      @PathVariable int cycleNumber,
      @Parameter(description = "Día de aplicación dentro del ciclo")
      @RequestParam(defaultValue = "1") int applicationDay,
      @RequestBody JsonNode body, HttpServletRequest request) {
    auth.requirePermission(request, "application.pharmacy.manage");
    SessionPrincipal actor = auth.require(request);
    return Map.of(
        "ok", true,
        "logistics", infusions.updateLogistics(
            patientId, treatmentId, cycleNumber, applicationDay, body, actor.userId(), actor.displayName()));
  }
}
