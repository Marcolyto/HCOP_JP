package ar.com.hexium.hcop.catalog;

import ar.com.hexium.hcop.auth.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LegacyCatalogController {
  private final LegacyProtocolCatalogService protocols;
  private final SeerTnmCatalogService tnm;
  private final DrugCatalogService drugs;
  private final AuthContext auth;

  public LegacyCatalogController(
      LegacyProtocolCatalogService protocols,
      SeerTnmCatalogService tnm,
      DrugCatalogService drugs,
      AuthContext auth) {
    this.protocols = protocols;
    this.tnm = tnm;
    this.drugs = drugs;
    this.auth = auth;
  }

  @GetMapping("/api/protocols")
  Map<String, Object> protocols(
      @RequestParam(defaultValue = "coir") String source,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.protocols.view");
    return protocols.list(source);
  }

  @GetMapping("/api/protocols/detail")
  Map<String, Object> protocolDetail(
      @RequestParam String id,
      @RequestParam(defaultValue = "coir") String source,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.protocols.view");
    return protocols.detail(id, source);
  }

  @GetMapping("/api/medications/search")
  Map<String, Object> medicationSearch(
      @RequestParam(defaultValue = "") String q,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.prescriptions.view");
    var results = drugs.search(q).stream().map(item -> Map.<String, Object>of(
        "id", item.getOrDefault("id", ""),
        "generic", item.getOrDefault("genericName", item.getOrDefault("name", "")),
        "brand", item.getOrDefault("brand", ""),
        "presentation", item.getOrDefault("presentation", ""),
        "form", item.getOrDefault("form", ""),
        "laboratory", item.getOrDefault("laboratory", ""))).toList();
    return Map.of("ok", true, "results", results, "count", results.size());
  }

  @GetMapping("/api/catalogs/status")
  Map<String, Object> status() {
    Map<String, Object> status = new java.util.LinkedHashMap<>(protocols.status(tnm.list().size()));
    status.put("medications", drugs.total());
    return status;
  }

  @PostMapping("/api/catalogs/update")
  Map<String, Object> update(@RequestBody(required = false) Map<String, Object> ignored) {
    Map<String, Object> status = new java.util.LinkedHashMap<>(protocols.status(tnm.list().size()));
    status.put("medications", drugs.total());
    status.put("message", "Los catálogos locales ya están disponibles y versionados.");
    return status;
  }
}
