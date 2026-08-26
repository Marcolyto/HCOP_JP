package ar.com.hexium.hcop.catalog.infrastructure.web;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.catalog.application.port.in.DrugCatalogUseCase;
import ar.com.hexium.hcop.catalog.application.port.in.LegacyProtocolCatalogUseCase;
import ar.com.hexium.hcop.catalog.application.port.in.TnmCatalogUseCase;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LegacyCatalogController {
  private final LegacyProtocolCatalogUseCase protocols;
  private final TnmCatalogUseCase tnm;
  private final DrugCatalogUseCase drugs;
  private final LegacyCatalogJsonMapper json;
  private final AuthContext auth;

  public LegacyCatalogController(
      LegacyProtocolCatalogUseCase protocols,
      TnmCatalogUseCase tnm,
      DrugCatalogUseCase drugs,
      LegacyCatalogJsonMapper json,
      AuthContext auth) {
    this.protocols = protocols;
    this.tnm = tnm;
    this.drugs = drugs;
    this.json = json;
    this.auth = auth;
  }

  @GetMapping("/api/protocols")
  Map<String, Object> protocols(
      @Parameter(description = "Origen del catálogo (coir/seer)")
      @RequestParam(defaultValue = "coir") String source,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.protocols.view");
    return json.view(protocols.list(source));
  }

  @GetMapping("/api/protocols/detail")
  Map<String, Object> protocolDetail(
      @Parameter(description = "Id del protocolo (según `source`)")
      @RequestParam String id,
      @Parameter(description = "Origen del catálogo (coir/seer)")
      @RequestParam(defaultValue = "coir") String source,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.protocols.view");
    return json.view(protocols.detail(id, source));
  }

  @GetMapping("/api/medications/search")
  Map<String, Object> medicationSearch(
      @Parameter(description = "Texto libre de búsqueda de medicamentos")
      @RequestParam(defaultValue = "") String q,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.prescriptions.view");
    var results = json.medications(drugs.search(q));
    return Map.of("ok", true, "results", results, "count", results.size());
  }

  @GetMapping("/api/catalogs/status")
  Map<String, Object> status() {
    return json.status(protocols.status(tnm.list().size()), drugs.total());
  }

  @PostMapping("/api/catalogs/update")
  Map<String, Object> update(@RequestBody(required = false) Map<String, Object> ignored) {
    Map<String, Object> status = new java.util.LinkedHashMap<>(
        json.status(protocols.status(tnm.list().size()), drugs.total()));
    status.put("message", "Los catálogos locales ya están disponibles y versionados.");
    return status;
  }
}
