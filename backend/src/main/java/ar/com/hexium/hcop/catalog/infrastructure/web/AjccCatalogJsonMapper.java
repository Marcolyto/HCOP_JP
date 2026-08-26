package ar.com.hexium.hcop.catalog.infrastructure.web;

import ar.com.hexium.hcop.catalog.application.port.in.AjccStagingUseCase.AjccSiteSummary;
import ar.com.hexium.hcop.catalog.application.port.in.AjccStagingUseCase.AjccSiteView;
import ar.com.hexium.hcop.catalog.application.port.in.AjccStagingUseCase.AjccStagingOutcome;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class AjccCatalogJsonMapper {
  private final ObjectMapper mapper;

  public AjccCatalogJsonMapper(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public Map<String, Object> view(AjccSiteSummary item) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", item.id());
    result.put("name", item.name());
    result.put("group", item.group());
    return result;
  }

  public Map<String, Object> view(AjccSiteView item) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("ok", true);
    result.put("id", item.id());
    result.put("name", item.name());
    result.put("edition", item.edition());
    result.put("source", item.source());
    result.put("guideVersion", item.guideVersion());
    result.put("axes", mapper.valueToTree(item.axes()));
    return result;
  }

  public Map<String, Object> view(AjccStagingOutcome outcome) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("ok", true);
    result.put("stage", outcome.stage());
    if (outcome.matched()) {
      result.put("sourceRow", outcome.sourceRow());
    } else {
      result.put("missing", outcome.missing());
    }
    return result;
  }

  public Map<String, String> stagingValues(Map<String, Object> body) {
    @SuppressWarnings("unchecked")
    Map<String, Object> values = body.get("values") instanceof Map<?, ?> map
        ? (Map<String, Object>) map : Map.of();
    Map<String, String> result = new LinkedHashMap<>();
    values.forEach((key, value) -> result.put(key, value == null ? "" : String.valueOf(value).trim()));
    return result;
  }
}
