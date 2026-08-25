package ar.com.hexium.hcop.catalog;

import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.config.HcopProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class SeerTnmCatalogService {
  private final Path root;
  private final ObjectMapper mapper;
  private volatile List<Schema> schemas;

  public SeerTnmCatalogService(HcopProperties properties, ObjectMapper mapper) {
    this.root = properties.catalogRoot().resolve("tnm-seer").resolve("tnm-2.1").normalize();
    this.mapper = mapper;
  }

  public List<Map<String, Object>> list() {
    return schemas().stream().map(schema -> Map.<String, Object>of(
        "id", schema.id(),
        "name", schema.name(),
        "title", schema.title(),
        "version", schema.version(),
        "inputs", schema.inputCount())).toList();
  }

  public Map<String, Object> detail(String id) {
    Schema stored = schemas().stream().filter(schema -> schema.id().equals(id)).findFirst()
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Sitio TNM no encontrado."));
    ObjectNode schemaView = mapper.createObjectNode();
    schemaView.put("id", stored.id());
    schemaView.put("name", stored.name());
    schemaView.put("title", stored.title());
    schemaView.put("version", stored.version());
    schemaView.set("notes", stored.document().path("notes").deepCopy());
    ArrayNode inputs = mapper.createArrayNode();
    stored.document().path("inputs").forEach(input -> {
      if (input.path("used_for_staging").asBoolean(false)) inputs.add(input.deepCopy());
    });
    schemaView.set("inputs", inputs);
    schemaView.set("outputs", stored.document().path("outputs").deepCopy());

    List<Map<String, Object>> stageTables = new ArrayList<>();
    stored.document().path("involved_tables").forEach(tableIdNode -> {
      String tableId = tableIdNode.asText("");
      if (!tableId.toLowerCase().contains("stage_group")) return;
      Path tablePath = root.resolve("tables").resolve(tableId + ".json").normalize();
      if (!tablePath.startsWith(root) || !Files.isRegularFile(tablePath)) return;
      try {
        JsonNode table = mapper.readTree(Files.readString(tablePath));
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", table.path("id").asText(tableId));
        value.put("name", table.path("name").asText(""));
        value.put("title", table.path("title").asText(""));
        value.put("notes", table.path("notes").asText(""));
        value.put("definition", table.path("definition"));
        value.put("rows", table.path("rows"));
        stageTables.add(value);
      } catch (IOException ignored) {
        // Una tabla auxiliar faltante no invalida el resto del esquema.
      }
    });
    return Map.of("ok", true, "schema", schemaView, "stageTables", stageTables);
  }

  private List<Schema> schemas() {
    List<Schema> snapshot = schemas;
    if (snapshot != null) return snapshot;
    synchronized (this) {
      if (schemas != null) return schemas;
      Path directory = root.resolve("schemas");
      List<Schema> loaded = new ArrayList<>();
      if (Files.isDirectory(directory)) {
        try (var files = Files.list(directory)) {
          files.filter(path -> path.getFileName().toString().endsWith(".json")).forEach(path -> {
            try {
              JsonNode document = mapper.readTree(Files.readString(path));
              int count = 0;
              for (JsonNode input : document.path("inputs")) {
                if (input.path("used_for_staging").asBoolean(false)) count++;
              }
              loaded.add(new Schema(
                  document.path("id").asText(path.getFileName().toString().replace(".json", "")),
                  document.path("name").asText(""),
                  document.path("title").asText(document.path("name").asText("")),
                  document.path("version").asText("2.1"),
                  count,
                  document));
            } catch (IOException exception) {
              throw new IllegalStateException("Esquema TNM inválido: " + path, exception);
            }
          });
        } catch (IOException exception) {
          throw new IllegalStateException("No se pudo leer el catálogo TNM.", exception);
        }
      }
      loaded.sort(Comparator.comparing(Schema::name, String.CASE_INSENSITIVE_ORDER));
      schemas = List.copyOf(loaded);
      return schemas;
    }
  }

  private record Schema(
      String id, String name, String title, String version, int inputCount, JsonNode document) {
  }
}
