package ar.com.hexium.hcop.system;

import ar.com.hexium.hcop.auth.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {
  private final JdbcTemplate jdbc;
  private final AuthContext auth;
  private final Clock clock;
  private final String version;

  public StatusController(
      DataSource dataSource,
      AuthContext auth,
      Clock clock,
      org.springframework.beans.factory.ObjectProvider<BuildProperties> buildProperties) {
    this.jdbc = new JdbcTemplate(dataSource);
    this.auth = auth;
    this.clock = clock;
    BuildProperties build = buildProperties.getIfAvailable();
    this.version = build == null ? "1.0.0-dev" : build.getVersion();
  }

  @GetMapping("/api/clinical/status")
  Map<String, Object> clinical(HttpServletRequest request) {
    Integer database = jdbc.queryForObject("select 1", Integer.class);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("ok", database != null && database == 1);
    result.put("service", "hcop-jp");
    result.put("engine", "java-postgresql");
    result.put("version", version);
    result.put("database", "postgresql");
    result.put("unified", true);
    result.put("localOnly", true);
    result.put("timestamp", Instant.now(clock).toString());
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
    return Map.of(
        "ok", true,
        "running", true,
        "service", "hcop-jp",
        "engine", "java-postgresql",
        "version", version,
        "managedBy", "docker-compose",
        "timestamp", Instant.now(clock).toString());
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
