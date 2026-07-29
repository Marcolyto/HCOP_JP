package ar.com.hexium.hcop.catalog;

import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.config.HcopProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
public class LegacyProtocolCatalogService {
  private final Path catalogRoot;
  private final ObjectMapper mapper;
  private final TreatmentCatalogService treatments;
  private volatile Snapshot snapshot;

  public LegacyProtocolCatalogService(
      HcopProperties properties,
      ObjectMapper mapper,
      TreatmentCatalogService treatments) {
    this.catalogRoot = properties.catalogRoot();
    this.mapper = mapper;
    this.treatments = treatments;
  }

  public Map<String, Object> list(String source) {
    if ("seer".equalsIgnoreCase(source)) return listSeer();
    Snapshot current = snapshot();
    return Map.of(
        "ok", true,
        "offline", true,
        "source", "coir",
        "count", current.schemes().size(),
        "categories", current.categories(),
        "schemes", current.schemes());
  }

  public Map<String, Object> detail(String id, String source) {
    if ("seer".equalsIgnoreCase(source)) return detailSeer(id);
    Snapshot current = snapshot();
    Map<String, Object> scheme = current.byId().get(id);
    if (scheme == null) throw new ApiException(HttpStatus.NOT_FOUND, "Esquema no encontrado.");
    List<Map<String, Object>> drugRows = readArray(
        catalogRoot.resolve("protocolos-lira").resolve("esquemas").resolve("detalle_" + id + ".json"));
    List<Map<String, Object>> drugs = drugRows.stream().map(row -> {
      Map<String, Object> value = new LinkedHashMap<>(row);
      String drugId = text(row.get("idDroga"));
      value.put("applications", current.applications().getOrDefault(drugId, List.of()));
      value.put("presentations", current.presentations().getOrDefault(drugId, List.of()));
      return value;
    }).toList();
    return Map.of("ok", true, "scheme", scheme, "drugs", drugs);
  }

  public Map<String, Object> status(int tnmCount) {
    Snapshot current = snapshot();
    return Map.of(
        "ok", true,
        "offline", true,
        "medications", current.drugNames().size(),
        "protocols", current.schemes().size(),
        "tnm", tnmCount,
        "tnmVersion", "2.1 / TNM 7");
  }

  public Set<String> protocolDrugNames() {
    return snapshot().drugNames();
  }

  public List<Map<String, Object>> clinicalComponents(String schemeId) {
    Object value = detail(schemeId, "coir").get("drugs");
    if (!(value instanceof List<?> rows)) return List.of();
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object row : rows) {
      if (row instanceof Map<?, ?> map) result.add(component(stringMap(map)));
    }
    return List.copyOf(result);
  }

  public List<Map<String, Object>> searchableDrugs() {
    Snapshot current = snapshot();
    Set<String> ids = new LinkedHashSet<>();
    ids.addAll(current.applications().keySet());
    ids.addAll(current.presentations().keySet());
    List<Map<String, Object>> result = new ArrayList<>();
    for (String id : ids) {
      List<Map<String, Object>> applications = current.applications().getOrDefault(id, List.of());
      List<Map<String, Object>> presentations = current.presentations().getOrDefault(id, List.of());
      String name = applications.stream().map(item -> text(item.get("monodroga")))
          .filter(item -> !item.isBlank()).findFirst()
          .orElseGet(() -> presentations.stream().map(item -> text(item.get("monodroga")))
              .filter(item -> !item.isBlank()).findFirst().orElse(""));
      if (id.isBlank() || name.isBlank()) continue;
      Map<String, Object> drug = new LinkedHashMap<>();
      drug.put("id", id);
      drug.put("name", name);
      drug.put("nombre", name);
      drug.put("genericName", name);
      drug.put("brand", "");
      drug.put("presentation", presentations.stream()
          .map(this::presentationLabel).filter(item -> !item.isBlank()).distinct()
          .reduce((left, right) -> left + " / " + right).orElse(""));
      drug.put("form", "");
      drug.put("laboratory", "");
      drug.put("source", "catalogo-coir");
      drug.put("instructions", applications.stream().map(this::preparation).toList());
      drug.put("presentations", presentations.stream().map(this::presentation).toList());
      result.add(drug);
    }
    result.sort(Comparator.comparing(
        item -> text(item.get("name")), String.CASE_INSENSITIVE_ORDER));
    return List.copyOf(result);
  }

  private Snapshot snapshot() {
    Snapshot value = snapshot;
    if (value != null) return value;
    synchronized (this) {
      if (snapshot == null) snapshot = load();
      return snapshot;
    }
  }

  private Snapshot load() {
    List<Map<String, Object>> schemes = new ArrayList<>();
    for (Map<String, Object> source : readArray(
        catalogRoot.resolve("protocolos-lira").resolve("esquemas.json"))) {
      if ("0".equals(text(source.get("activo")))) continue;
      String id = text(source.get("id"));
      String name = text(source.get("nombre"));
      if (id.isBlank() || name.isBlank()) continue;
      var estimate = treatments.scheme(id).orElse(null);
      Map<String, Object> item = new LinkedHashMap<>(source);
      item.put("id", id);
      item.put("name", name);
      item.put("category", category(name));
      item.put("cycleDays", integer(source.get("duracionCiclo")));
      item.put("durationMinutes", estimate == null ? null : estimate.durationMinutes());
      item.put("durationText", durationText(estimate == null ? null : estimate.durationMinutes()));
      item.put("catalogOnly", false);
      schemes.add(item);
    }
    schemes.sort(Comparator.comparing(
            (Map<String, Object> item) -> text(item.get("category")), String.CASE_INSENSITIVE_ORDER)
        .thenComparing(item -> text(item.get("name")), String.CASE_INSENSITIVE_ORDER));
    Map<String, List<Map<String, Object>>> applications = groupByDrug(
        readArray(catalogRoot.resolve("protocolos-lira").resolve("indicacionAplicacion.json")));
    Map<String, List<Map<String, Object>>> presentations = groupByDrug(
        readArray(catalogRoot.resolve("protocolos-lira").resolve("presentacion.json")));
    Set<String> drugNames = new LinkedHashSet<>();
    applications.values().forEach(rows -> rows.forEach(row -> drugNames.add(text(row.get("monodroga")))));
    presentations.values().forEach(rows -> rows.forEach(row -> drugNames.add(text(row.get("monodroga")))));
    drugNames.remove("");
    return new Snapshot(
        List.copyOf(schemes),
        Map.copyOf(index(schemes)),
        schemes.stream().map(item -> text(item.get("category"))).distinct().toList(),
        Map.copyOf(applications),
        Map.copyOf(presentations),
        Set.copyOf(drugNames));
  }

  private Map<String, Object> listSeer() {
    List<Map<String, Object>> schemes = seerSchemes();
    List<String> categories = schemes.stream().map(item -> text(item.get("category"))).distinct().sorted().toList();
    return Map.of("ok", true, "offline", true, "source", "seer", "count", schemes.size(),
        "categories", categories, "schemes", schemes);
  }

  private Map<String, Object> detailSeer(String id) {
    Map<String, Object> scheme = seerSchemes().stream()
        .filter(item -> id.equals(text(item.get("id")))).findFirst()
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Esquema SEER no encontrado."));
    return Map.of("ok", true, "scheme", scheme, "drugs", scheme.getOrDefault("drugs", List.of()));
  }

  private List<Map<String, Object>> seerSchemes() {
    Path file = catalogRoot.resolve("seer-rx-regimens.csv");
    if (!Files.isRegularFile(file)) return List.of();
    try {
      List<List<String>> rows = AjccCatalogService.parseCsv(Files.readString(file));
      if (!rows.isEmpty()) rows.removeFirst();
      List<Map<String, Object>> result = new ArrayList<>();
      for (int index = 0; index < rows.size(); index++) {
        List<String> row = rows.get(index);
        if (row.isEmpty() || row.getFirst().isBlank()) continue;
        String site = cell(row, 5, "Sitio no especificado");
        List<Map<String, Object>> drugs = List.of(cell(row, 7, "").split(";")).stream()
            .map(String::trim).filter(value -> !value.isBlank())
            .map(value -> Map.<String, Object>of("droga", value)).toList();
        Map<String, Object> scheme = new LinkedHashMap<>();
        scheme.put("id", "seer-" + index);
        scheme.put("name", cell(row, 0, ""));
        scheme.put("category", site);
        scheme.put("histology", cell(row, 2, ""));
        scheme.put("remarks", cell(row, 4, ""));
        scheme.put("alternateNames", cell(row, 6, ""));
        scheme.put("drugs", drugs);
        result.add(scheme);
      }
      return List.copyOf(result);
    } catch (IOException exception) {
      return List.of();
    }
  }

  private List<Map<String, Object>> readArray(Path file) {
    if (!Files.isRegularFile(file)) return List.of();
    try {
      JsonNode root = mapper.readTree(Files.readString(file));
      List<Map<String, Object>> rows = new ArrayList<>();
      if (root.isArray()) {
        root.forEach(node -> rows.add(mapper.convertValue(node, Map.class)));
      }
      return rows;
    } catch (IOException exception) {
      throw new IllegalStateException("Catálogo inválido: " + file, exception);
    }
  }

  private Map<String, List<Map<String, Object>>> groupByDrug(List<Map<String, Object>> rows) {
    Map<String, List<Map<String, Object>>> groups = new HashMap<>();
    rows.forEach(row -> groups.computeIfAbsent(text(row.get("idDroga")), ignored -> new ArrayList<>()).add(row));
    return groups;
  }

  private Map<String, Object> component(Map<String, Object> source) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", text(source.get("id")));
    result.put("drugId", text(source.get("idDroga")));
    result.put("drugName", text(source.get("droga")));
    result.put("day", text(source.get("dia")));
    result.put("prescribedDoseText", text(source.get("dosisDiaria")));
    result.put("doseCalculationMethod", text(source.get("calculoDosis")));
    result.put("route", text(source.get("viaAdministracion")));
    result.put("administrationTime", text(source.get("tiempoAdministracion")));
    result.put("dayHospital", !"0".equals(text(source.get("seAplicaEnHdd"))));
    result.put("applications", mapList(source.get("applications")).stream()
        .map(this::preparation).toList());
    result.put("presentations", mapList(source.get("presentations")).stream()
        .map(this::presentation).toList());
    result.put("sourcePayload", source);
    return result;
  }

  private Map<String, Object> preparation(Map<String, Object> source) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", text(source.get("id")));
    result.put("drugId", text(source.get("idDroga")));
    result.put("drugName", text(source.get("monodroga")));
    result.put("presentationReferences", text(source.get("presentaciones")));
    result.put("reconstituent", text(source.get("reconstituyente")));
    result.put("concentration", text(source.get("concentracion")));
    result.put("diluent", text(source.get("diluyente")));
    result.put("finalVolume", text(source.get("volumenFinal")));
    result.put("route", text(source.get("viaAdministracion")));
    result.put("stabilityRoomTemperature", firstText(
        source, "estabilidadTemp", "estabilidadTA"));
    result.put("stabilityRefrigerated", firstText(
        source, "estabilidadFrio", "estabilidadF"));
    result.put("laboratory", text(source.get("laboratorio")));
    result.put("photosensitive", truthy(source.get("fotosensible")));
    result.put("infusionGuide", text(source.get("guiaInfusion")));
    result.put("preparationObservations", text(source.get("observacionesPreparacion")));
    result.put("labelObservations", text(source.get("observacionesEtiqueta")));
    result.put("sourcePayload", source);
    return result;
  }

  private Map<String, Object> presentation(Map<String, Object> source) {
    Map<String, Object> result = new LinkedHashMap<>(source);
    result.put("id", text(source.get("id")));
    result.put("drugId", text(source.get("idDroga")));
    result.put("drugName", text(source.get("monodroga")));
    result.put("display", presentationLabel(source));
    return result;
  }

  private String presentationLabel(Map<String, Object> source) {
    String amount = text(source.get("cantidad"));
    return amount.isBlank() ? text(source.get("presentaciones")) : amount;
  }

  private List<Map<String, Object>> mapList(Object value) {
    if (!(value instanceof List<?> list)) return List.of();
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> map) result.add(stringMap(map));
    }
    return result;
  }

  private Map<String, Object> stringMap(Map<?, ?> value) {
    Map<String, Object> result = new LinkedHashMap<>();
    value.forEach((key, item) -> result.put(String.valueOf(key), item));
    return result;
  }

  private String firstText(Map<String, Object> source, String... keys) {
    for (String key : keys) {
      String value = text(source.get(key));
      if (!value.isBlank()) return value;
    }
    return "";
  }

  private boolean truthy(Object value) {
    String normalized = text(value).toLowerCase(Locale.ROOT);
    return Set.of("1", "true", "si", "sí", "yes").contains(normalized);
  }

  private Map<String, Map<String, Object>> index(List<Map<String, Object>> values) {
    Map<String, Map<String, Object>> result = new HashMap<>();
    values.forEach(item -> result.put(text(item.get("id")), item));
    return result;
  }

  private static String category(String name) {
    String trimmed = name.trim();
    int separator = trimmed.indexOf(" - ");
    if (separator < 0) separator = trimmed.indexOf(':');
    String value = separator > 0 ? trimmed.substring(0, separator) : "Otros";
    return value.length() > 80 ? value.substring(0, 80) : value;
  }

  private static String cell(List<String> row, int index, String fallback) {
    return index < row.size() && !row.get(index).isBlank() ? row.get(index).trim() : fallback;
  }

  private static String text(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }

  private static Integer integer(Object value) {
    try {
      return Integer.valueOf(text(value));
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private static String durationText(Integer minutes) {
    if (minutes == null || minutes < 1) return "";
    if (minutes < 60) return minutes + " min";
    return minutes / 60 + " h" + (minutes % 60 == 0 ? "" : " " + minutes % 60 + " min");
  }

  private record Snapshot(
      List<Map<String, Object>> schemes,
      Map<String, Map<String, Object>> byId,
      List<String> categories,
      Map<String, List<Map<String, Object>>> applications,
      Map<String, List<Map<String, Object>>> presentations,
      Set<String> drugNames) {
  }
}
