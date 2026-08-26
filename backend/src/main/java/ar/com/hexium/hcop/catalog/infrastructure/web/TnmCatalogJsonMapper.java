package ar.com.hexium.hcop.catalog.infrastructure.web;

import ar.com.hexium.hcop.catalog.application.port.in.TnmCatalogUseCase.TnmSchemaSummary;
import ar.com.hexium.hcop.catalog.application.port.in.TnmCatalogUseCase.TnmSchemaView;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TnmCatalogJsonMapper {

  public Map<String, Object> view(TnmSchemaSummary item) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", item.id());
    result.put("name", item.name());
    result.put("title", item.title());
    result.put("version", item.version());
    result.put("inputs", item.inputCount());
    return result;
  }

  public Map<String, Object> view(TnmSchemaView item) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", item.id());
    result.put("name", item.name());
    result.put("title", item.title());
    result.put("version", item.version());
    result.put("notes", item.notes());
    result.put("inputs", item.inputs());
    result.put("outputs", item.outputs());
    return result;
  }
}
