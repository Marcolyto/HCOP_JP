package ar.com.hexium.hcop.catalog.infrastructure.web;

import ar.com.hexium.hcop.catalog.application.port.in.LegacyProtocolCatalogUseCase.CatalogStatus;
import ar.com.hexium.hcop.catalog.application.port.in.LegacyProtocolCatalogUseCase.ProtocolSchemeCatalog;
import ar.com.hexium.hcop.catalog.application.port.in.LegacyProtocolCatalogUseCase.ProtocolSchemeDetail;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LegacyCatalogJsonMapper {

  public Map<String, Object> view(ProtocolSchemeCatalog catalog) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("ok", true);
    result.put("offline", true);
    result.put("source", catalog.source());
    result.put("count", catalog.schemes().size());
    result.put("categories", catalog.categories());
    result.put("schemes", catalog.schemes());
    return result;
  }

  public Map<String, Object> view(ProtocolSchemeDetail detail) {
    return Map.of("ok", true, "scheme", detail.scheme(), "drugs", detail.drugs());
  }

  public Map<String, Object> status(CatalogStatus status, int medications) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("ok", true);
    result.put("offline", true);
    result.put("medications", medications);
    result.put("protocols", status.protocols());
    result.put("tnm", status.tnm());
    result.put("tnmVersion", status.tnmVersion());
    return result;
  }

  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> medications(List<Object> drugs) {
    return drugs.stream().map(item -> (Map<String, Object>) item).map(item -> Map.<String, Object>of(
        "id", item.getOrDefault("id", ""),
        "generic", item.getOrDefault("genericName", item.getOrDefault("name", "")),
        "brand", item.getOrDefault("brand", ""),
        "presentation", item.getOrDefault("presentation", ""),
        "form", item.getOrDefault("form", ""),
        "laboratory", item.getOrDefault("laboratory", ""))).toList();
  }
}
