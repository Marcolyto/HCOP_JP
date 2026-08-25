package ar.com.hexium.hcop.tools.infrastructure.web;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase;
import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase.ConfigurationView;
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
  private final ConfigurationManagementUseCase configurations;
  private final AuthContext auth;
  private final ObjectMapper mapper;

  public CalculatorCatalogController(
      ConfigurationManagementUseCase configurations,
      AuthContext auth,
      ObjectMapper mapper) {
    this.configurations = configurations;
    this.auth = auth;
    this.mapper = mapper;
  }

  @GetMapping("/api/clinical/tools/calculators")
  public Map<String, Object> list(HttpServletRequest request) {
    auth.requirePermission(request, "section.tools.use");
    List<Map<String, Object>> calculators = configurations.list("calculator", false)
        .stream()
        .filter(ConfigurationView::active)
        .map(this::operationalView)
        .toList();
    Map<String, Object> settings = configurations.list("tool-settings", false)
        .stream()
        .filter(ConfigurationView::active)
        .findFirst()
        .map(this::operationalView)
        .orElseGet(Map::of);
    return Map.of(
        "ok", true,
        "calculators", calculators,
        "settings", settings,
        "total", calculators.size());
  }

  private Map<String, Object> operationalView(ConfigurationView item) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", item.id());
    result.put("key", item.key());
    result.put("name", item.name());
    result.put("description", item.description());
    result.put("revision", item.revision());
    result.put("definition", mapper.valueToTree(item.definition().value()));
    return result;
  }
}
