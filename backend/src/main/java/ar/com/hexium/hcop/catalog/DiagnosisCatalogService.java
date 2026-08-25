package ar.com.hexium.hcop.catalog;

import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.config.HcopProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class DiagnosisCatalogService {
  private final HcopProperties properties;
  private final ObjectMapper mapper;
  private final AjccCatalogService ajcc;
  private volatile List<Equivalence> cache;

  public DiagnosisCatalogService(HcopProperties properties, ObjectMapper mapper, AjccCatalogService ajcc) {
    this.properties = properties;
    this.mapper = mapper;
    this.ajcc = ajcc;
  }

  public Map<String, Object> search(String system, String query, int limit) {
    String normalizedSystem = system == null ? "" : system.trim().toLowerCase(Locale.ROOT);
    if (!Set.of("ajcc", "snomed", "cie10").contains(normalizedSystem)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Sistema diagnóstico inválido.");
    }
    if (query == null || query.trim().length() < 2) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Escriba al menos 2 caracteres.");
    }
    int boundedLimit = Math.max(1, Math.min(100, limit));
    List<Map<String, Object>> items;
    if ("ajcc".equals(normalizedSystem)) {
      items = ajcc.search(query, boundedLimit);
    } else {
      List<String> terms = normalizedTerms(query);
      Set<String> unique = new LinkedHashSet<>();
      List<Map<String, Object>> matches = new ArrayList<>();
      for (Equivalence value : equivalences()) {
        String code = "snomed".equals(normalizedSystem) ? value.snomedCode() : value.cie10Code();
        String display = value.snomedDisplay();
        if (!matchesAll(code + " " + display + " " + value.ajccDisplay(), terms)) continue;
        if (!unique.add(code + "\u0000" + display)) continue;
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("system", "snomed".equals(normalizedSystem) ? "SNOMED CT" : "CIE-10");
        item.put("code", code);
        item.put("display", display);
        item.put("group", value.ajccDisplay());
        item.put("version", "snomed".equals(normalizedSystem) ? "MAIN" : "Mapeo local validado");
        item.put("source", "Catálogo terminológico local");
        item.put("sourceConceptId", value.snomedCode());
        matches.add(item);
        if (matches.size() >= boundedLimit) break;
      }
      items = List.copyOf(matches);
    }
    return Map.of(
        "ok", true,
        "offline", true,
        "system", normalizedSystem,
        "query", query.trim(),
        "count", items.size(),
        "items", items);
  }

  public List<Equivalence> equivalences() {
    List<Equivalence> snapshot = cache;
    if (snapshot != null) return snapshot;
    synchronized (this) {
      if (cache != null) return cache;
      var file = properties.catalogRoot().resolve("diagnosis-equivalences.json");
      List<Equivalence> loaded = new ArrayList<>();
      try {
        JsonNode root = mapper.readTree(Files.readString(file));
        for (JsonNode row : root) {
          if (!row.isArray() || row.size() < 6) continue;
          loaded.add(new Equivalence(
              row.get(0).asText(), row.get(1).asText(), row.get(2).asText(),
              row.get(3).asText(), row.get(4).asText(), row.get(5).asText()));
        }
      } catch (IOException exception) {
        throw new IllegalStateException("No se pudo leer el catálogo diagnóstico local.", exception);
      }
      cache = List.copyOf(loaded);
      return cache;
    }
  }

  public static List<String> normalizedTerms(String value) {
    if (value == null) return List.of();
    String normalized = normalize(value);
    return List.of(normalized.split(" ")).stream().filter(term -> !term.isBlank()).distinct().toList();
  }

  public static boolean matchesAll(String value, List<String> terms) {
    String normalized = normalize(value);
    return terms.stream().allMatch(normalized::contains);
  }

  private static String normalize(String value) {
    return Normalizer.normalize(String.valueOf(value), Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^\\p{L}\\p{N}]+", " ")
        .replaceAll("\\b(?:tumor|neoplasia)\\s+malign[oa]s?\\b", "carcinoma")
        .replaceAll("\\bcancer\\b", "carcinoma")
        .replaceAll("\\s+", " ")
        .trim();
  }

  public record Equivalence(
      String ajccCode, String ajccDisplay, String snomedCode, String snomedDisplay,
      String cie10Code, String relation) {
  }
}
