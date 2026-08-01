package ar.com.hexium.hcop.configuration.infrastructure.web;

import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase.ConfigurationVersionView;
import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase.ConfigurationView;
import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase.CreateCommand;
import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase.UpdateCommand;
import ar.com.hexium.hcop.configuration.domain.ConfigurationDefinition;
import ar.com.hexium.hcop.sharedkernel.domain.UserId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Traduce el contrato JSON histórico a comandos tipados y viceversa.
 */
@Component
public class ConfigurationJsonMapper {
  private static final Set<String> METADATA = Set.of(
      "id",
      "kind",
      "itemKind",
      "key",
      "itemKey",
      "name",
      "displayName",
      "nombre",
      "description",
      "descripcion",
      "active",
      "revision",
      "expectedRevision",
      "reason");

  private final ObjectMapper mapper;

  public ConfigurationJsonMapper(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public CreateCommand createCommand(String kind, JsonNode input, long actorId) {
    return new CreateCommand(
        kind,
        text(input, "key", "itemKey", "slug"),
        text(input, "name", "displayName", "nombre"),
        text(input, "description", "descripcion"),
        !input.has("active") || input.path("active").asBoolean(true),
        definition(input),
        UserId.of(actorId));
  }

  public UpdateCommand updateCommand(String kind, long id, JsonNode input, long actorId) {
    return new UpdateCommand(
        kind,
        id,
        input.has("revision")
            ? input.path("revision").asLong()
            : input.has("expectedRevision")
                ? input.path("expectedRevision").asLong()
                : null,
        text(input, "key", "itemKey", "slug"),
        text(input, "name", "displayName", "nombre"),
        input.has("description") ? input.path("description").asText("") : null,
        input.has("active") ? input.path("active").asBoolean() : null,
        input.has("definition") ? definition(input) : null,
        UserId.of(actorId));
  }

  public Map<String, Object> view(ConfigurationView item) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", item.id());
    result.put("kind", item.kind());
    result.put("key", item.key());
    result.put("name", item.name());
    result.put("active", item.active());
    result.put("revision", item.revision());
    result.put("definition", mapper.valueToTree(item.definition().value()));
    if (item.revision() == 0) return result;
    result.put("itemKind", item.kind());
    result.put("itemKey", item.key());
    result.put("displayName", item.name());
    result.put("description", item.description());
    result.put("createdAt", item.createdAt().toString());
    result.put("updatedAt", item.updatedAt().toString());
    return result;
  }

  public Map<String, Object> versionView(ConfigurationVersionView version) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("revision", version.revision());
    result.put("name", version.name());
    result.put("displayName", version.name());
    result.put("description", version.description());
    result.put("active", version.active());
    result.put("definition", mapper.valueToTree(version.definition().value()));
    result.put("changedBy", version.changedBy());
    result.put("changedByName", version.changedByName());
    result.put("createdAt", version.createdAt().toString());
    return result;
  }

  private ConfigurationDefinition definition(JsonNode input) {
    JsonNode explicit = input.path("definition");
    JsonNode source;
    if (explicit.isObject() || explicit.isArray()) {
      source = explicit;
    } else {
      var copy = mapper.createObjectNode();
      input.properties().forEach(entry -> {
        if (!METADATA.contains(entry.getKey())) copy.set(entry.getKey(), entry.getValue().deepCopy());
      });
      source = copy;
    }
    return ConfigurationDefinition.of(mapper.convertValue(source, Object.class));
  }

  private String text(JsonNode node, String... keys) {
    for (String key : keys) {
      String value = node.path(key).asText("").trim();
      if (!value.isBlank()) return value;
    }
    return "";
  }
}
