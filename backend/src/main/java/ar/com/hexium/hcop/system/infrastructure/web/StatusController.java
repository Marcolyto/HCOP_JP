package ar.com.hexium.hcop.system.infrastructure.web;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.system.application.port.in.SystemStatusUseCase;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {
  private final SystemStatusUseCase status;
  private final AuthContext auth;

  public StatusController(SystemStatusUseCase status, AuthContext auth) {
    this.status = status;
    this.auth = auth;
  }

  @GetMapping("/api/clinical/status")
  Map<String, Object> clinical(HttpServletRequest request) {
    SystemStatusUseCase.ClinicalStatusView view = status.clinicalStatus();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("ok", view.databaseUp());
    result.put("service", "hcop-jp");
    result.put("engine", "java-postgresql");
    result.put("version", view.version());
    result.put("database", "postgresql");
    result.put("unified", true);
    result.put("localOnly", true);
    result.put("timestamp", view.timestamp().toString());
    return result;
  }

  @GetMapping("/api/lira/status")
  Map<String, Object> liraCompatibility(HttpServletRequest request) {
    return Map.of(
        "ok", true,
        "available", true,
        "authenticated", true,
        "local", true,
        "independent", true,
        "message", "HCOP JP opera con su base PostgreSQL local.");
  }

  @GetMapping("/api/runtime/status")
  Map<String, Object> runtime() {
    SystemStatusUseCase.RuntimeStatusView view = status.runtimeStatus();
    return Map.of(
        "ok", true,
        "running", true,
        "service", "hcop-jp",
        "engine", "java-postgresql",
        "version", view.version(),
        "managedBy", "docker-compose",
        "timestamp", view.timestamp().toString());
  }

  @PostMapping("/api/runtime/stop")
  Map<String, Object> stop(HttpServletRequest request) {
    auth.requirePermission(request, "admin.manage-security");
    return Map.of(
        "ok", false,
        "stopped", false,
        "message", "El servidor Docker se detiene con detener.bat o docker compose down.");
  }
}
