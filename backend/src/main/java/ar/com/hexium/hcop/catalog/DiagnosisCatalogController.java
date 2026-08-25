package ar.com.hexium.hcop.catalog;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/diagnosis-catalogs")
public class DiagnosisCatalogController {
  private final DiagnosisCatalogService catalog;

  public DiagnosisCatalogController(DiagnosisCatalogService catalog) {
    this.catalog = catalog;
  }

  @GetMapping("/search")
  public Map<String, Object> search(
      @RequestParam String system,
      @RequestParam(name = "q") String query,
      @RequestParam(defaultValue = "40") int limit) {
    return catalog.search(system, query, limit);
  }
}
