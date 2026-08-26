package ar.com.hexium.hcop.protocol.infrastructure.web;

import ar.com.hexium.hcop.protocol.application.port.in.ProtocolManagementUseCase.CatalogView;
import ar.com.hexium.hcop.protocol.application.port.in.ProtocolManagementUseCase.ProtocolView;
import ar.com.hexium.hcop.protocol.application.port.in.ProtocolManagementUseCase.SaveProtocolCommand;
import ar.com.hexium.hcop.protocol.domain.ProtocolDocument;
import ar.com.hexium.hcop.sharedkernel.domain.UserId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class ProtocolJsonMapper {
  private static final Set<String> METADATA = Set.of(
      "id", "name", "description", "active", "revision", "catalogOnly");

  private final ObjectMapper mapper;

  public ProtocolJsonMapper(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public SaveProtocolCommand command(JsonNode body, long actorId) {
    Map<String, Object> definition = new LinkedHashMap<>();
    body.properties().forEach(entry -> {
      if (!METADATA.contains(entry.getKey())) {
        definition.put(entry.getKey(), mapper.convertValue(entry.getValue(), Object.class));
      }
    });
    return new SaveProtocolCommand(
        body.path("name").asText("").strip(),
        body.path("description").asText(""),
        !body.has("active") || body.path("active").asBoolean(true),
        body.has("revision") ? body.path("revision").asLong() : null,
        ProtocolDocument.of(definition),
        UserId.of(actorId));
  }

  public Map<String, Object> view(ProtocolView item) {
    Map<String, Object> result = new LinkedHashMap<>();
    if (!item.catalogOnly()) {
      result.put("id", item.id());
      result.put("kind", "protocol");
      result.put("key", item.key());
      result.put("name", item.name());
      result.put("active", item.active());
      result.put("revision", item.revision());
      result.put("definition", mapper.valueToTree(item.definition().value()));
      result.put("itemKind", "protocol");
      result.put("itemKey", item.key());
      result.put("displayName", item.name());
      result.put("description", item.description());
      result.put("createdAt", item.createdAt().toString());
      result.put("updatedAt", item.updatedAt().toString());
      result.putAll(item.definition().value());
    } else {
      result.put("id", item.id());
      result.put("coirSchemeId", item.coirSchemeId());
      result.put("name", item.name());
      result.put("category", item.category());
      result.put("description", item.description());
      result.put("cycleDays", item.cycleDays());
      result.put("durationMinutes", item.durationMinutes());
      result.put("active", true);
    }
    result.put("catalogOnly", item.catalogOnly());
    result.put("components", item.components().stream().map(ProtocolDocument::value).toList());
    result.put("componentCount", item.componentCount());
    result.put("durationText", durationText(item.durationMinutes()));
    result.putIfAbsent("category", item.category());
    result.putIfAbsent("cycleDays", item.cycleDays());
    result.put("coirLinks", item.coirSchemeId() == null || item.coirSchemeId().isBlank()
        ? List.of()
        : item.catalogOnly()
            ? List.of()
            : List.of(Map.of("coirSchemeId", item.coirSchemeId())));
    return result;
  }

  public Map<String, Object> catalog(CatalogView item) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("coirSchemeId", item.coirSchemeId());
    row.put("schemeName", item.schemeName());
    row.put("durationMinutes", item.durationMinutes());
    row.put("durationText", durationText(item.durationMinutes()));
    row.put("cycleDays", item.cycleDays());
    row.put("entryType", "treatment");
    return row;
  }

  public Map<String, Object> document(ProtocolDocument document) {
    return document.value();
  }

  private String durationText(Integer minutes) {
    if (minutes == null || minutes < 1) return "";
    return minutes < 60 ? minutes + " min"
        : (minutes / 60) + " h" + (minutes % 60 == 0 ? "" : " " + (minutes % 60) + " min");
  }
}
