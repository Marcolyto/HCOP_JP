package ar.com.hexium.hcop.catalog.infrastructure.persistence;

import ar.com.hexium.hcop.catalog.application.port.out.TnmSchemaStore;
import ar.com.hexium.hcop.catalog.domain.TnmSchema;
import ar.com.hexium.hcop.config.HcopProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class FilesystemTnmSchemaStore implements TnmSchemaStore {
  private final Path root;
  private final ObjectMapper mapper;
  private volatile List<TnmSchema> cachedSchemas;

  public FilesystemTnmSchemaStore(HcopProperties properties, ObjectMapper mapper) {
    this.root = properties.catalogRoot().resolve("tnm-seer").resolve("tnm-2.1").normalize();
    this.mapper = mapper;
  }

  @Override
  public List<TnmSchema> schemas() {
    List<TnmSchema> snapshot = cachedSchemas;
    if (snapshot != null) return snapshot;
    synchronized (this) {
      if (cachedSchemas == null) cachedSchemas = load();
      return cachedSchemas;
    }
  }

  @Override
  public Optional<Object> stageTable(String tableId) {
    Path tablePath = root.resolve("tables").resolve(tableId + ".json").normalize();
    if (!tablePath.startsWith(root) || !Files.isRegularFile(tablePath)) return Optional.empty();
    try {
      JsonNode table = mapper.readTree(Files.readString(tablePath));
      Map<String, Object> value = new LinkedHashMap<>();
      value.put("id", table.path("id").asText(tableId));
      value.put("name", table.path("name").asText(""));
      value.put("title", table.path("title").asText(""));
      value.put("notes", table.path("notes").asText(""));
      value.put("definition", mapper.convertValue(table.path("definition"), Object.class));
      value.put("rows", mapper.convertValue(table.path("rows"), Object.class));
      return Optional.of(value);
    } catch (IOException ignored) {
      // Una tabla auxiliar faltante no invalida el resto del esquema.
      return Optional.empty();
    }
  }

  private List<TnmSchema> load() {
    Path directory = root.resolve("schemas");
    List<TnmSchema> loaded = new ArrayList<>();
    if (Files.isDirectory(directory)) {
      try (var files = Files.list(directory)) {
        files.filter(path -> path.getFileName().toString().endsWith(".json")).forEach(path -> {
          try {
            JsonNode document = mapper.readTree(Files.readString(path));
            List<Object> stagingInputs = new ArrayList<>();
            for (JsonNode input : document.path("inputs")) {
              if (input.path("used_for_staging").asBoolean(false)) {
                stagingInputs.add(mapper.convertValue(input, Object.class));
              }
            }
            List<String> involvedTables = new ArrayList<>();
            document.path("involved_tables").forEach(node -> involvedTables.add(node.asText("")));
            loaded.add(new TnmSchema(
                document.path("id").asText(path.getFileName().toString().replace(".json", "")),
                document.path("name").asText(""),
                document.path("title").asText(document.path("name").asText("")),
                document.path("version").asText("2.1"),
                mapper.convertValue(document.path("notes"), Object.class),
                List.copyOf(stagingInputs),
                mapper.convertValue(document.path("outputs"), Object.class),
                List.copyOf(involvedTables)));
          } catch (IOException exception) {
            throw new IllegalStateException("Esquema TNM inválido: " + path, exception);
          }
        });
      } catch (IOException exception) {
        throw new IllegalStateException("No se pudo leer el catálogo TNM.", exception);
      }
    }
    return loaded.stream().sorted(Comparator.comparing(TnmSchema::name, String.CASE_INSENSITIVE_ORDER)).toList();
  }
}
