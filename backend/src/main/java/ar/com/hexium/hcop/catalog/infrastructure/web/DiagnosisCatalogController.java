package ar.com.hexium.hcop.catalog.infrastructure.web;

import ar.com.hexium.hcop.catalog.application.port.in.DiagnosisCatalogUseCase;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/diagnosis-catalogs")
public class DiagnosisCatalogController {
  private final DiagnosisCatalogUseCase catalog;
  private final CatalogSearchResultJsonMapper json;

  public DiagnosisCatalogController(DiagnosisCatalogUseCase catalog, CatalogSearchResultJsonMapper json) {
    this.catalog = catalog;
    this.json = json;
  }

  @GetMapping("/search")
  public Map<String, Object> search(
      @RequestParam String system,
      @RequestParam(name = "q") String query,
      @RequestParam(defaultValue = "40") int limit) {
    var result = catalog.search(system, query, limit);
    var items = result.items().stream().map(json::view).toList();
    return Map.of(
        "ok", true, "offline", true, "system", result.system(), "query", result.query(),
        "count", items.size(), "items", items);
  }
}
