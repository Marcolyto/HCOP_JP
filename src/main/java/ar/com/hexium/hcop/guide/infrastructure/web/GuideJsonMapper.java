package ar.com.hexium.hcop.guide.infrastructure.web;

import ar.com.hexium.hcop.guide.application.port.in.GuideCatalogUseCase.GuideView;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GuideJsonMapper {
  public Map<String, Object> view(GuideView item) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("name", item.name());
    result.put("title", item.title());
    result.put("site", item.site());
    result.put("audience", item.audience());
    result.put("source", item.source());
    result.put("version", item.version());
    result.put("tags", item.tags());
    result.put("description", item.description());
    result.put("active", item.active());
    result.put("configurationId", item.configurationId());
    result.put("configurationRevision", item.configurationRevision());
    result.put("url", "/api/guides/file?name=" + URLEncoder.encode(
        item.name(), StandardCharsets.UTF_8));
    result.put("size", item.size());
    result.put("updatedAt", item.updatedAt().toString());
    return result;
  }
}
