package ar.com.hexium.hcop.catalog.infrastructure.persistence;

import ar.com.hexium.hcop.catalog.application.port.out.TreatmentSchemeStore;
import ar.com.hexium.hcop.catalog.domain.TreatmentScheme;
import ar.com.hexium.hcop.platform.HcopProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Repository
public class PostgresTreatmentSchemeStore implements TreatmentSchemeStore {
  private final HcopProperties properties;
  private final ObjectMapper mapper;
  private final JdbcTemplate jdbc;
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private volatile Catalog catalog = new Catalog(List.of(), Instant.EPOCH);

  public PostgresTreatmentSchemeStore(HcopProperties properties, ObjectMapper mapper, JdbcTemplate jdbc) {
    this.properties = properties;
    this.mapper = mapper;
    this.jdbc = jdbc;
  }

  @Override
  public List<TreatmentScheme> load() {
    return current().schemes();
  }

  @Override
  public void invalidate() {
    lock.writeLock().lock();
    try {
      catalog = new Catalog(List.of(), Instant.EPOCH);
    } finally {
      lock.writeLock().unlock();
    }
  }

  private Catalog current() {
    Catalog value = catalog;
    if (Duration.between(value.loadedAt(), Instant.now()).toMinutes() < 2) return value;
    lock.writeLock().lock();
    try {
      if (Duration.between(catalog.loadedAt(), Instant.now()).toMinutes() < 2) return catalog;
      catalog = new Catalog(readSchemes(), Instant.now());
      return catalog;
    } finally {
      lock.writeLock().unlock();
    }
  }

  private List<TreatmentScheme> readSchemes() {
    DurationIndex durations = readDurations();
    Map<String, TreatmentScheme> merged = new LinkedHashMap<>();
    var schemesFile = properties.catalogRoot().resolve("protocolos-lira").resolve("esquemas.json");
    try {
      JsonNode root = mapper.readTree(Files.readString(schemesFile));
      if (root.isArray()) {
        for (JsonNode node : root) {
          if ("0".equals(node.path("activo").asText("1"))) continue;
          String id = node.path("id").asText("").trim();
          String name = node.path("nombre").asText("").trim();
          if (id.isBlank() || name.isBlank()) continue;
          Integer duration = durations.resolve(id, name);
          JsonNode definition = catalogDefinition(node, id);
          merged.put(id, new TreatmentScheme(id, name, number(node, "duracionCiclo"), duration, definition, false));
        }
      }
    } catch (IOException ignored) {
      // La configuración en PostgreSQL sigue permitiendo operar aun sin el catálogo opcional.
    }

    jdbc.query("""
        SELECT item_key, display_name, definition_json::text
          FROM clinical_configuration_items
         WHERE item_kind = 'protocol' AND active = true
         ORDER BY lower(display_name)
        """, result -> {
          String id = result.getString("item_key");
          String name = result.getString("display_name");
          JsonNode definition = mapper.readTree(result.getString("definition_json"));
          int cycleDays = number(definition, "cycleDays");
          if (cycleDays == 0) cycleDays = number(definition, "duracionCiclo");
          Integer duration = nullableNumber(definition, "durationMinutes");
          if (duration == null) duration = nullableNumber(definition, "estimatedDurationMinutes");
          if (duration == null) duration = durations.resolve(id, name);
          merged.put(id, new TreatmentScheme(id, name, cycleDays, duration, definition, true));
        });

    return merged.values().stream()
        .sorted(java.util.Comparator.comparing(TreatmentScheme::name, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  private JsonNode catalogDefinition(JsonNode summary, String id) {
    JsonNode definition = summary.deepCopy();
    var detailFile = properties.catalogRoot().resolve("protocolos-lira").resolve("esquemas")
        .resolve("detalle_" + id + ".json");
    if (!(definition instanceof ObjectNode object) || !Files.isRegularFile(detailFile)) {
      return definition;
    }
    try {
      JsonNode drugs = mapper.readTree(Files.readString(detailFile));
      if (drugs.isArray()) object.set("drugs", drugs);
    } catch (IOException ignored) {
      // Un detalle opcional inválido no debe impedir que el resto del catálogo se cargue.
    }
    return definition;
  }

  private DurationIndex readDurations() {
    Map<String, Integer> byId = new HashMap<>();
    Map<String, Integer> byName = new HashMap<>();
    var file = properties.catalogRoot().resolve("scheme-duration-seed.json");
    boolean consolidated = Files.isRegularFile(file);
    if (!consolidated) file = properties.catalogRoot().resolve("esquemas-coir-419.json");
    try {
      JsonNode root = mapper.readTree(Files.readString(file));
      for (JsonNode node : root.path("schemes")) {
        String id = node.path(consolidated ? "schemeId" : "id").asText("").trim();
        String name = node.path(consolidated ? "schemeName" : "scheme").asText("").trim();
        int duration = node.path("durationMinutes").asInt(0);
        if (duration < 1) continue;
        if (!id.isBlank()) byId.put(id, duration);
        if (!name.isBlank()) byName.put(normalizeName(name), duration);
      }
    } catch (IOException ignored) {
      // Los protocolos personalizados pueden indicar su propia duración.
    }
    return new DurationIndex(Map.copyOf(byId), Map.copyOf(byName));
  }

  private int number(JsonNode node, String field) {
    Integer value = nullableNumber(node, field);
    return value == null ? 0 : value;
  }

  private Integer nullableNumber(JsonNode node, String field) {
    JsonNode value = node.path(field);
    if (value.isNumber()) return value.asInt();
    try {
      String text = value.asText("").trim();
      return text.isBlank() ? null : Integer.valueOf(text);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private static String normalizeName(String value) {
    if (value == null) return "";
    return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", " ")
        .trim();
  }

  private record Catalog(List<TreatmentScheme> schemes, Instant loadedAt) {
  }

  private record DurationIndex(Map<String, Integer> byId, Map<String, Integer> byName) {
    Integer resolve(String id, String name) {
      Integer value = byId.get(id);
      return value == null ? byName.get(normalizeName(name)) : value;
    }
  }
}
