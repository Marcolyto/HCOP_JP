package ar.com.hexium.hcop.catalog.infrastructure.web;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.catalog.application.port.in.AjccStagingUseCase;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ajcc8")
public class AjccCatalogController {
  private final AjccStagingUseCase catalog;
  private final AjccCatalogJsonMapper json;
  private final AuthContext auth;

  public AjccCatalogController(AjccStagingUseCase catalog, AjccCatalogJsonMapper json, AuthContext auth) {
    this.catalog = catalog;
    this.json = json;
    this.auth = auth;
  }

  @GetMapping
  public Map<String, Object> list(HttpServletRequest request) {
    auth.requirePermission(request, "section.tools.view");
    List<Map<String, Object>> sites = catalog.list().stream().map(json::view).toList();
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("ok", true);
    response.put("offline", true);
    response.put("edition", "AJCC 8");
    response.put("source", "Catálogo local validado");
    response.put("count", sites.size());
    response.put("sites", sites);
    return response;
  }

  @GetMapping("/detail")
  public Map<String, Object> detail(@RequestParam String id, HttpServletRequest request) {
    auth.requirePermission(request, "section.tools.view");
    return json.view(catalog.detail(id));
  }

  @PostMapping("/stage")
  public Map<String, Object> stage(@RequestBody Map<String, Object> body, HttpServletRequest request) {
    auth.requirePermission(request, "section.tools.use");
    return json.view(catalog.stage(String.valueOf(body.getOrDefault("id", "")), json.stagingValues(body)));
  }
}
