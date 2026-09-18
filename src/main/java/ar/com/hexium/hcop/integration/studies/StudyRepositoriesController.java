package ar.com.hexium.hcop.integration.studies;

import ar.com.hexium.hcop.auth.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
public class StudyRepositoriesController {
  private final StudyRepositorySettings settings;
  private final AuthContext auth;
  public StudyRepositoriesController(StudyRepositorySettings settings, AuthContext auth) {
    this.settings = settings; this.auth = auth;
  }
  @GetMapping("/api/admin/study-repositories")
  ResponseEntity<Map<String, Object>> list(HttpServletRequest request) {
    auth.requirePermission(request, "section.configuration.view");
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(settings.publicView());
  }
  @PutMapping("/api/admin/study-repositories/{id}")
  ResponseEntity<Map<String, Object>> save(@PathVariable String id, @RequestBody JsonNode body, HttpServletRequest request) {
    auth.requirePermission(request, "section.configuration.manage");
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(settings.update(id, body, auth.require(request).userId()));
  }
}
