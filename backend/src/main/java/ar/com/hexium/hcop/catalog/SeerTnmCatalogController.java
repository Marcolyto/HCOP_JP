package ar.com.hexium.hcop.catalog;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tnm")
public class SeerTnmCatalogController {
  private final SeerTnmCatalogService catalog;

  public SeerTnmCatalogController(SeerTnmCatalogService catalog) {
    this.catalog = catalog;
  }

  @GetMapping
  public Map<String, Object> list() {
    var schemas = catalog.list();
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("ok", true);
    response.put("offline", true);
    response.put("version", "2.1 / TNM 7");
    response.put("count", schemas.size());
    response.put("schemas", schemas);
    return response;
  }

  @GetMapping("/detail")
  public Map<String, Object> detail(@RequestParam String id) {
    return catalog.detail(id);
  }
}
