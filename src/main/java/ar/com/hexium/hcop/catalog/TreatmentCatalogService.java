package ar.com.hexium.hcop.catalog;

import ar.com.hexium.hcop.config.HcopProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class TreatmentCatalogService {
  private final HcopProperties properties;
  private final ObjectMapper mapper;
  private final JdbcTemplate jdbc;
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private volatile Catalog catalog = new Catalog(List.of(), Map.of(), Instant.EPOCH);

  public TreatmentCatalogService(HcopProperties properties, ObjectMapper mapper, JdbcTemplate jdbc) {
    this.properties = properties;
    this.mapper = mapper;
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> schemes(String query) {
    Catalog current = current();
    String normalized = normalize(query);
    List<Map<String, Object>> result = new ArrayList<>();
    for (Scheme scheme : current.schemes()) {
      if (!normalized.isBlank() && !normalize(scheme.name()).contains(normalized)) continue;
      result.add(scheme.view());
    }
    return result;
  }

  public Optional<Scheme> scheme(String id) {
    return current().byId().containsKey(id)
        ? Optional.of(current().byId().get(id))
        : Optional.empty();
  }

  public List<Scheme> allSchemes() {
    return current().schemes();
  }

  public void invalidate() {
    lock.writeLock().lock();
    try {
      catalog = new Catalog(List.of(), Map.of(), Instant.EPOCH);
    } finally {
      lock.writeLock().unlock();
    }
  }

  public record Scheme(
      String id,
      String name,
      int cycleDays,
      Integer durationMinutes,
      JsonNode definition,
      boolean custom) {
    public Map<String, Object> view() {
      Map<String, Object> value = new LinkedHashMap<>();
      value.put("id", id);
      value.put("nombre", name);
      value.put("name", name);
      value.put("activo", "1");
      value.put("duracionCiclo", cycleDays > 0 ? Integer.toString(cycleDays) : "");
      value.put("cycleDays", cycleDays > 0 ? cycleDays : null);
      value.put("estimatedDurationMinutes", durationMinutes);
      value.put("durationMinutes", durationMinutes);
      value.put("estimatedDurationText", durationText(durationMinutes));
      value.put("origin", custom ? "custom" : "catalog");
      return value;
    }
  }

  private Catalog current() {
    Catalog value = catalog;
    if (Duration.between(value.loadedAt(), Instant.now()).toMinutes() < 2) return value;
    lock.writeLock().lock();
    try {
      if (Duration.between(catalog.loadedAt(), Instant.now()).toMinutes() < 2) return catalog;
      catalog = load();
      return catalog;
    } finally {
      lock.writeLock().unlock();
    }
  }

  private Catalog load() {
    DurationIndex durations = readDurations();
    Map<String, Scheme> merged = new LinkedHashMap<>();
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
          merged.put(id, new Scheme(
              id, name, number(node, "duracionCiclo"), duration, definition, false));
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
          merged.put(id, new Scheme(id, name, cycleDays, duration, definition, true));
        });

    List<Scheme> schemes = merged.values().stream()
        .sorted(Comparator.comparing(Scheme::name, String.CASE_INSENSITIVE_ORDER))
        .toList();
    Map<String, Scheme> byId = new HashMap<>();
    schemes.forEach(item -> byId.put(item.id(), item));
    return new Catalog(schemes, Map.copyOf(byId), Instant.now());
  }

  private JsonNode catalogDefinition(JsonNode summary, String id) {
    JsonNode definition = summary.deepCopy();
    var detailFile = properties.catalogRoot()
        .resolve("protocolos-lira")
        .resolve("esquemas")
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
        if (!name.isBlank()) byName.put(normalize(name), duration);
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

  private String normalize(String value) {
    if (value == null) return "";
    return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", " ")
        .trim();
  }

  private static String durationText(Integer minutes) {
    if (minutes == null || minutes < 1) return "";
    int hours = minutes / 60;
    int remainder = minutes % 60;
    if (hours == 0) return minutes + " min";
    if (remainder == 0) return hours + " h";
    return hours + " h " + remainder + " min";
  }

  private record Catalog(List<Scheme> schemes, Map<String, Scheme> byId, Instant loadedAt) {
  }

  private record DurationIndex(Map<String, Integer> byId, Map<String, Integer> byName) {
    Integer resolve(String id, String name) {
      Integer value = byId.get(id);
      return value == null ? byName.get(normalizeName(name)) : value;
    }

    private static String normalizeName(String value) {
      if (value == null) return "";
      return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
          .replaceAll("\\p{M}", "")
          .toLowerCase(Locale.ROOT)
          .replaceAll("[^a-z0-9]+", " ")
          .trim();
    }
  }
}
