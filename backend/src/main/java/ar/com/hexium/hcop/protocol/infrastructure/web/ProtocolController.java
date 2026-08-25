package ar.com.hexium.hcop.protocol.infrastructure.web;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.protocol.application.port.in.ProtocolManagementUseCase;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class ProtocolController {
  private final ProtocolManagementUseCase protocols;
  private final ProtocolJsonMapper json;
  private final AuthContext auth;

  public ProtocolController(
      ProtocolManagementUseCase protocols,
      ProtocolJsonMapper json,
      AuthContext auth) {
    this.protocols = protocols;
    this.json = json;
    this.auth = auth;
  }

  @GetMapping("/api/clinical/protocols")
  Map<String, Object> list(
      @RequestParam(defaultValue = "0") int includeArchived,
      @RequestParam(defaultValue = "0") int includeCatalog,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.protocols.view");
    var result = protocols.list(includeArchived == 1, includeCatalog == 1);
    List<Map<String, Object>> views = result.protocols().stream().map(json::view).toList();
    return Map.of(
        "ok", true,
        "protocols", views,
        "total", views.size(),
        "currentCount", result.currentCount(),
        "catalogCount", result.catalogCount());
  }

  @GetMapping("/api/clinical/protocols/{id}")
  Map<String, Object> get(@PathVariable String id, HttpServletRequest request) {
    auth.requirePermission(request, "section.protocols.view");
    return Map.of("ok", true, "protocol", json.view(protocols.get(id)));
  }

  @PostMapping("/api/clinical/protocols")
  ResponseEntity<Map<String, Object>> create(
      @RequestBody JsonNode body,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.protocols.edit");
    var protocol = protocols.create(json.command(body, auth.require(request).userId()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of("ok", true, "protocol", json.view(protocol)));
  }

  @PutMapping("/api/clinical/protocols/{id}")
  Map<String, Object> update(
      @PathVariable String id,
      @RequestBody JsonNode body,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.protocols.edit");
    var protocol = protocols.update(id, json.command(body, auth.require(request).userId()));
    return Map.of("ok", true, "protocol", json.view(protocol));
  }

  @DeleteMapping("/api/clinical/protocols/{id}")
  Map<String, Object> archive(@PathVariable String id, HttpServletRequest request) {
    auth.requirePermission(request, "section.protocols.edit");
    var protocol = protocols.archive(id, ar.com.hexium.hcop.sharedkernel.domain.UserId.of(
        auth.require(request).userId()));
    return Map.of("ok", true, "protocol", json.view(protocol));
  }

  @GetMapping("/api/clinical/coir-catalog")
  Map<String, Object> coir(HttpServletRequest request) {
    auth.requirePermission(request, "section.protocols.view");
    List<Map<String, Object>> catalog = protocols.catalog().stream().map(json::catalog).toList();
    return Map.of("ok", true, "catalog", catalog, "total", catalog.size());
  }

  @GetMapping("/api/clinical/drugs")
  Map<String, Object> drugs(
      @RequestParam(defaultValue = "") String q,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.protocols.view");
    List<Map<String, Object>> result = protocols.drugs(q).stream().map(json::document).toList();
    return Map.of("ok", true, "drugs", result, "total", result.size());
  }
}
