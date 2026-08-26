package ar.com.hexium.hcop.catalog.infrastructure.web;

import ar.com.hexium.hcop.catalog.domain.CatalogSearchResult;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Compartido por Diagnóstico y AJCC — misma proyección de un resultado de búsqueda. */
@Component
public class CatalogSearchResultJsonMapper {

  public Map<String, Object> view(CatalogSearchResult item) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("system", item.system());
    result.put("code", item.code());
    result.put("display", item.display());
    result.put("group", item.group());
    result.put("version", item.version());
    result.put("source", item.source());
    if (item.sourceConceptId() != null) result.put("sourceConceptId", item.sourceConceptId());
    return result;
  }
}
