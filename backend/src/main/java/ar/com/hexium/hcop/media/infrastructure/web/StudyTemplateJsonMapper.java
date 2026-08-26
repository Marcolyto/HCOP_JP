package ar.com.hexium.hcop.media.infrastructure.web;

import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase.ConfigurationView;
import ar.com.hexium.hcop.configuration.infrastructure.web.ConfigurationJsonMapper;
import ar.com.hexium.hcop.media.application.port.in.StudyTemplateUseCase.StudyTemplateCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class StudyTemplateJsonMapper {
  private final ConfigurationJsonMapper configurationJson;
  private final ObjectMapper mapper;

  public StudyTemplateJsonMapper(ConfigurationJsonMapper configurationJson, ObjectMapper mapper) {
    this.configurationJson = configurationJson;
    this.mapper = mapper;
  }

  public Map<String, Object> view(StudyTemplateCatalog catalog) {
    List<Object> templates = new ArrayList<>(catalog.bundledTemplates());
    for (ConfigurationView item : catalog.customTemplates()) {
      templates.add(customTemplate(configurationJson.view(item)));
    }
    Map<String, Integer> categoryCounts = new LinkedHashMap<>();
    for (Object raw : templates) {
      Map<?, ?> item = (Map<?, ?>) raw;
      Object categoryValue = item.containsKey("category") ? item.get("category") : "otros";
      categoryCounts.merge(String.valueOf(categoryValue), 1, Integer::sum);
    }
    List<Map<String, Object>> categories = categoryCounts.entrySet().stream()
        .map(entry -> Map.<String, Object>of(
            "id", entry.getKey(), "title", entry.getKey().replace('-', ' '), "count", entry.getValue()))
        .toList();
    return Map.of(
        "ok", true,
        "version", 2,
        "bundledCount", catalog.bundledTemplates().size(),
        "customCount", catalog.customTemplates().size(),
        "total", templates.size(),
        "categories", categories,
        "templates", templates,
        "customAvailable", true);
  }

  public Map<String, Object> created(ConfigurationView item) {
    var itemView = configurationJson.view(item);
    return Map.of("ok", true, "item", itemView, "template", customTemplate(itemView));
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> customTemplate(Map<String, Object> item) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", "custom-" + item.get("id"));
    row.put("configurationId", item.get("id"));
    row.put("origin", "custom");
    row.put("title", item.get("name"));
    row.put("description", item.get("description"));
    row.put("active", item.get("active"));
    row.put("available", true);
    Object definitionValue = item.get("definition");
    Map<String, Object> definition = definitionValue instanceof JsonNode node
        ? mapper.convertValue(node, Map.class)
        : definitionValue instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    row.putAll(definition);
    row.put("file", definition.getOrDefault("fileUrl", ""));
    row.put("thumbnail", definition.getOrDefault("thumbnailUrl", definition.getOrDefault("fileUrl", "")));
    row.put("definition", definition);
    return row;
  }
}
