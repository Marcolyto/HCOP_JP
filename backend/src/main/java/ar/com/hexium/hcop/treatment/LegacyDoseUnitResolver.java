package ar.com.hexium.hcop.treatment;

import ar.com.hexium.hcop.config.HcopProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Resolves the missing dose unit of the legacy Lira protocol components.
 *
 * <p>The primary source is {@code indicacionAplicacion.json}. A catalog value is accepted only
 * when every presentation for the same drug resolves to the same unit. The small override table
 * covers legacy drugs that have no preparation row in that file.</p>
 */
@Component
public class LegacyDoseUnitResolver {
  private static final Pattern PRESENTATION_UNIT = Pattern.compile(
      "(?i)(?:^|[,;])\\s*\\d+(?:[.,]\\d+)?\\s*(mg|mcg|µg|ug|g|ui|u\\.i\\.|mui|meq|mmol|ml)\\b");

  private static final Map<String, String> CURATED_OVERRIDES = Map.ofEntries(
      // Non-oral legacy components absent from indicacionAplicacion.json.
      Map.entry("226", "mg"),    // Epirubicina
      Map.entry("1279", "UI"),   // Eritropoyetina recombinante
      Map.entry("1341", "mg"),   // Furosemida
      Map.entry("1379", "mg"),   // Hidrocortisona
      Map.entry("7947", "mg"),   // Ibandronato
      Map.entry("7890", "mg"),   // Ixabepilona
      Map.entry("16", "mmol"),   // Magnesio
      Map.entry("1483", "mg"),   // Metoclopramida
      Map.entry("1601", "mEq"),  // Potasio
      Map.entry("1856", "mL"),   // Soluciones parenterales
      Map.entry("7914", "mL"),   // Solución fisiológica
      Map.entry("2390", "mg"),   // Vitaminas B1/B6/B12 + ácido tióctico

      // Oral legacy components also need an explicit unit in the prescription snapshot.
      Map.entry("7892", "mg"),
      Map.entry("7942", "mg"),
      Map.entry("7912", "mg"),
      Map.entry("7935", "mg"),
      Map.entry("2001", "mg"),
      Map.entry("147", "mg"),
      Map.entry("1984", "mg"),
      Map.entry("7936", "mg"),
      Map.entry("1024", "mg"),
      Map.entry("1126", "mg"),
      Map.entry("1166", "mg"),
      Map.entry("7897", "mg"),
      Map.entry("2395", "mg"),
      Map.entry("7898", "mg"),
      Map.entry("2106", "mg"),
      Map.entry("2102", "mg"),
      Map.entry("1953", "mg"),
      Map.entry("1768", "mg"),
      Map.entry("1951", "mg"),
      Map.entry("150", "mg"),
      Map.entry("7928", "mg"),
      Map.entry("1966", "mg"),
      Map.entry("7945", "mg"),
      Map.entry("2128", "mg"),
      Map.entry("2199", "mg"),
      Map.entry("843", "mg"),
      Map.entry("48", "mg"),
      Map.entry("7918", "mg"),
      Map.entry("278", "mg"),
      Map.entry("1470", "mg"),
      Map.entry("2202", "mg"),
      Map.entry("7944", "mg"),
      Map.entry("7888", "mg"),
      Map.entry("7893", "mg"),
      Map.entry("1566", "mg"),
      Map.entry("7916", "mg"),
      Map.entry("7941", "mg"),
      Map.entry("559", "mg"),
      Map.entry("1610", "mg"),
      Map.entry("2438", "mg"),
      Map.entry("7891", "mg"),
      Map.entry("2437", "mg"),
      Map.entry("2105", "mg"),
      Map.entry("7980", "mg"),
      Map.entry("2104", "mg"),
      Map.entry("7943", "mg"),
      Map.entry("2318", "mg"),
      Map.entry("1670", "mg"),
      Map.entry("998", "mg"),
      Map.entry("7899", "mg"),
      Map.entry("7919", "mg"),
      Map.entry("2387", "mg"),
      Map.entry("7926", "mg"),
      Map.entry("7920", "mg"));

  private final Map<String, String> byDrugId;
  private final Map<String, String> byDrugName;

  @Autowired
  public LegacyDoseUnitResolver(HcopProperties properties, ObjectMapper mapper) {
    this(properties.catalogRoot()
        .resolve("protocolos-lira")
        .resolve("indicacionAplicacion.json"), mapper);
  }

  LegacyDoseUnitResolver(java.nio.file.Path source, ObjectMapper mapper) {
    UnitIndex index = load(source, mapper);
    this.byDrugId = index.byDrugId();
    this.byDrugName = index.byDrugName();
  }

  public String resolve(JsonNode component) {
    String drugId = text(component, "drugId", "idDroga");
    String resolved = byDrugId.get(drugId);
    if (resolved != null) return resolved;
    resolved = CURATED_OVERRIDES.get(drugId);
    if (resolved != null) return resolved;
    return byDrugName.getOrDefault(
        normalize(text(component, "drugName", "droga", "name", "nombre")), "");
  }

  private UnitIndex load(java.nio.file.Path source, ObjectMapper mapper) {
    Map<String, Set<String>> idCandidates = new HashMap<>();
    Map<String, Set<String>> nameCandidates = new HashMap<>();
    if (Files.isRegularFile(source)) {
      try {
        JsonNode root = mapper.readTree(Files.readString(source));
        if (root.isArray()) {
          for (JsonNode row : root) {
            Set<String> units = presentationUnits(row.path("presentaciones").asText(""));
            if (units.isEmpty()) continue;
            addCandidates(idCandidates, row.path("idDroga").asText("").trim(), units);
            addCandidates(
                nameCandidates, normalize(row.path("monodroga").asText("").trim()), units);
          }
        }
      } catch (IOException ignored) {
        // Creation remains fail-closed when neither the catalog nor a curated override resolves.
      }
    }
    return new UnitIndex(unambiguous(idCandidates), unambiguous(nameCandidates));
  }

  private Set<String> presentationUnits(String presentations) {
    Set<String> result = new LinkedHashSet<>();
    Matcher matcher = PRESENTATION_UNIT.matcher(presentations == null ? "" : presentations);
    while (matcher.find()) result.add(canonicalUnit(matcher.group(1)));
    result.remove("");
    return result;
  }

  private void addCandidates(
      Map<String, Set<String>> destination, String key, Set<String> units) {
    if (key == null || key.isBlank()) return;
    destination.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).addAll(units);
  }

  private Map<String, String> unambiguous(Map<String, Set<String>> candidates) {
    Map<String, String> result = new HashMap<>();
    candidates.forEach((key, units) -> {
      if (units.size() == 1) result.put(key, units.iterator().next());
    });
    return Map.copyOf(result);
  }

  private String canonicalUnit(String value) {
    String normalized = normalize(value).replace(".", "");
    return switch (normalized) {
      case "ug", "µg", "mcg" -> "mcg";
      case "ui" -> "UI";
      case "mui" -> "MUI";
      case "meq" -> "mEq";
      case "ml" -> "mL";
      case "mmol" -> "mmol";
      case "g" -> "g";
      case "mg" -> "mg";
      default -> "";
    };
  }

  private static String text(JsonNode node, String... keys) {
    for (String key : keys) {
      String value = node.path(key).asText("").trim();
      if (!value.isBlank()) return value;
    }
    return "";
  }

  private static String normalize(String value) {
    return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .trim();
  }

  private record UnitIndex(Map<String, String> byDrugId, Map<String, String> byDrugName) {
  }
}
