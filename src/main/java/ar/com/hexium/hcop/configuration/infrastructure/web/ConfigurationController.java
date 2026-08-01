package ar.com.hexium.hcop.configuration.infrastructure.web;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase;
import ar.com.hexium.hcop.sharedkernel.domain.UserId;
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
public class ConfigurationController {
  private final ConfigurationManagementUseCase configurations;
  private final ConfigurationJsonMapper json;
  private final AuthContext auth;

  public ConfigurationController(
      ConfigurationManagementUseCase configurations,
      ConfigurationJsonMapper json,
      AuthContext auth) {
    this.configurations = configurations;
    this.json = json;
    this.auth = auth;
  }

  @GetMapping("/api/clinical/configuration/{kind}")
  Map<String, Object> list(
      @PathVariable String kind,
      @RequestParam(defaultValue = "0") int includeInactive,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.configuration.view");
    List<Map<String, Object>> items = configurations.list(kind, includeInactive == 1)
        .stream()
        .map(json::view)
        .toList();
    return Map.of("ok", true, "items", items, "total", items.size());
  }

  @PostMapping("/api/clinical/configuration/{kind}")
  ResponseEntity<Map<String, Object>> create(
      @PathVariable String kind,
      @RequestBody JsonNode body,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.configuration.manage");
    var item = configurations.create(
        json.createCommand(kind, body, auth.require(request).userId()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of("ok", true, "item", json.view(item)));
  }

  @PutMapping("/api/clinical/configuration/{kind}/{id}")
  Map<String, Object> update(
      @PathVariable String kind,
      @PathVariable long id,
      @RequestBody JsonNode body,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.configuration.manage");
    var item = configurations.update(
        json.updateCommand(kind, id, body, auth.require(request).userId()));
    return Map.of("ok", true, "item", json.view(item));
  }

  @DeleteMapping("/api/clinical/configuration/{kind}/{id}")
  Map<String, Object> archive(
      @PathVariable String kind,
      @PathVariable long id,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.configuration.manage");
    var item = configurations.archive(kind, id, UserId.of(auth.require(request).userId()));
    return Map.of("ok", true, "item", json.view(item));
  }

  @GetMapping("/api/clinical/configuration/{kind}/{id}/versions")
  Map<String, Object> versions(
      @PathVariable String kind,
      @PathVariable long id,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.configuration.view");
    List<Map<String, Object>> versions = configurations.versions(kind, id)
        .stream()
        .map(json::versionView)
        .toList();
    return Map.of("ok", true, "versions", versions, "total", versions.size());
  }

  @GetMapping("/api/clinical/configuration/{kind}/{id}/versions/{revision}")
  Map<String, Object> version(
      @PathVariable String kind,
      @PathVariable long id,
      @PathVariable long revision,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.configuration.view");
    return Map.of(
        "ok",
        true,
        "version",
        json.versionView(configurations.version(kind, id, revision)));
  }
}
