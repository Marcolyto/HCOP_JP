package ar.com.hexium.hcop.tools.infrastructure.web;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.tools.application.port.in.CalculatorCatalogUseCase;
import ar.com.hexium.hcop.tools.domain.CalculatorSummary;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * Proyección operativa de las calculadoras institucionales para Herramientas.
 * No expone historial, autores ni capacidades administrativas de Configuración.
 */
@RestController
public class CalculatorCatalogController {
  private final CalculatorCatalogUseCase calculators;
  private final AuthContext auth;
  private final ObjectMapper mapper;

  public CalculatorCatalogController(
      CalculatorCatalogUseCase calculators,
      AuthContext auth,
      ObjectMapper mapper) {
    this.calculators = calculators;
    this.auth = auth;
    this.mapper = mapper;
  }

  @GetMapping("/api/clinical/tools/calculators")
  public Map<String, Object> list(HttpServletRequest request) {
    auth.requirePermission(request, "section.tools.use");
    CalculatorCatalogUseCase.CalculatorCatalogView view = calculators.list();
    List<Map<String, Object>> projected = view.calculators().stream().map(this::view).toList();
    Map<String, Object> settings = view.settings().map(this::view).orElseGet(Map::of);
    return Map.of(
        "ok", true,
        "calculators", projected,
        "settings", settings,
        "total", projected.size());
  }

  private Map<String, Object> view(CalculatorSummary item) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", item.id());
    result.put("key", item.key());
    result.put("name", item.name());
    result.put("description", item.description());
    result.put("revision", item.revision());
    result.put("definition", mapper.valueToTree(item.definition()));
    return result;
  }
}
