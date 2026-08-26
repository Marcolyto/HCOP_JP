package ar.com.hexium.hcop.catalog.infrastructure.web;

import ar.com.hexium.hcop.catalog.application.port.in.TnmCatalogUseCase;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tnm")
public class SeerTnmCatalogController {
  private final TnmCatalogUseCase catalog;
  private final TnmCatalogJsonMapper json;

  public SeerTnmCatalogController(TnmCatalogUseCase catalog, TnmCatalogJsonMapper json) {
    this.catalog = catalog;
    this.json = json;
  }

  @GetMapping
  public Map<String, Object> list() {
    var schemas = catalog.list().stream().map(json::view).toList();
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("ok", true);
    response.put("offline", true);
    response.put("version", "2.1 / TNM 7");
    response.put("count", schemas.size());
    response.put("schemas", schemas);
    return response;
  }

  @GetMapping("/detail")
  public Map<String, Object> detail(@Parameter(description = "Código del esquema TNM/SEER")
  @RequestParam String id) {
    var detail = catalog.detail(id);
    return Map.of("ok", true, "schema", json.view(detail.schema()), "stageTables", detail.stageTables());
  }
}
