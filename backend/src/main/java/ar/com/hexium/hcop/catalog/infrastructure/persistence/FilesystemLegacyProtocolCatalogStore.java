package ar.com.hexium.hcop.catalog.infrastructure.persistence;

import ar.com.hexium.hcop.catalog.application.port.in.LegacyProtocolCatalogUseCase.CatalogStatus;
import ar.com.hexium.hcop.catalog.application.port.in.LegacyProtocolCatalogUseCase.ProtocolSchemeCatalog;
import ar.com.hexium.hcop.catalog.application.port.in.LegacyProtocolCatalogUseCase.ProtocolSchemeDetail;
import ar.com.hexium.hcop.catalog.application.port.in.TreatmentCatalogUseCase;
import ar.com.hexium.hcop.catalog.application.port.out.LegacyProtocolCatalogStore;
import ar.com.hexium.hcop.catalog.application.service.CatalogFailure;
import ar.com.hexium.hcop.platform.HcopProperties;
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
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class FilesystemLegacyProtocolCatalogStore implements LegacyProtocolCatalogStore {
  private final Path catalogRoot;
  private final ObjectMapper mapper;
  private final TreatmentCatalogUseCase treatments;
  private volatile Snapshot snapshot;

  public FilesystemLegacyProtocolCatalogStore(
      HcopProperties properties, ObjectMapper mapper, TreatmentCatalogUseCase treatments) {
    this.catalogRoot = properties.catalogRoot();
    this.mapper = mapper;
    this.treatments = treatments;
  }

  @Override
  public ProtocolSchemeCatalog list(String source) {
    if ("seer".equalsIgnoreCase(source)) return listSeer();
    Snapshot current = snapshot();
    return new ProtocolSchemeCatalog("coir", current.categories(), current.schemes());
  }

  @Override
  public ProtocolSchemeDetail detail(String id, String source) {
    if ("seer".equalsIgnoreCase(source)) return detailSeer(id);
    Snapshot current = snapshot();
    Object scheme = current.byId().get(id);
    if (scheme == null) throw new CatalogFailure(CatalogFailure.Type.NOT_FOUND, "Esquema no encontrado.");
    List<Object> drugRows = readArray(
        catalogRoot.resolve("protocolos-lira").resolve("esquemas").resolve("detalle_" + id + ".json"));
    List<Object> drugs = drugRows.stream().map(row -> {
      @SuppressWarnings("unchecked")
      Map<String, Object> source1 = (Map<String, Object>) row;
      Map<String, Object> value = new LinkedHashMap<>(source1);
      String drugId = text(source1.get("idDroga"));
      value.put("applications", current.applications().getOrDefault(drugId, List.of()));
      value.put("presentations", current.presentations().getOrDefault(drugId, List.of()));
      return (Object) value;
    }).toList();
    return new ProtocolSchemeDetail(scheme, drugs);
  }

  @Override
  public CatalogStatus status(int tnmCount) {
    Snapshot current = snapshot();
    return new CatalogStatus(current.schemes().size(), tnmCount, "2.1 / TNM 7");
  }

  @Override
  public List<Object> clinicalComponents(String schemeId) {
    Object value = detail(schemeId, "coir").drugs();
    if (!(value instanceof List<?> rows)) return List.of();
    List<Object> result = new ArrayList<>();
    for (Object row : rows) {
      if (row instanceof Map<?, ?> map) result.add(component(stringMap(map)));
    }
    return List.copyOf(result);
  }

  @Override
  public List<Object> searchableDrugs() {
    Snapshot current = snapshot();
    Set<String> ids = new LinkedHashSet<>();
    ids.addAll(current.applications().keySet());
    ids.addAll(current.presentations().keySet());
    List<Object> result = new ArrayList<>();
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
    result.sort(Comparator.comparing(item -> text(((Map<?, ?>) item).get("name")), String.CASE_INSENSITIVE_ORDER));
    return List.copyOf(result);
  }

  @Override
  public Set<String> protocolDrugNames() {
    return snapshot().drugNames();
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
    List<Object> schemes = new ArrayList<>();
    for (Object sourceRow : readArray(catalogRoot.resolve("protocolos-lira").resolve("esquemas.json"))) {
      @SuppressWarnings("unchecked")
      Map<String, Object> source = (Map<String, Object>) sourceRow;
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
            (Object item) -> text(((Map<?, ?>) item).get("category")), String.CASE_INSENSITIVE_ORDER)
        .thenComparing(item -> text(((Map<?, ?>) item).get("name")), String.CASE_INSENSITIVE_ORDER));
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
        schemes.stream().map(item -> text(((Map<?, ?>) item).get("category"))).distinct().toList(),
        Map.copyOf(applications),
        Map.copyOf(presentations),
        Set.copyOf(drugNames));
  }

  private ProtocolSchemeCatalog listSeer() {
    List<Object> schemes = seerSchemes();
    List<String> categories = schemes.stream()
        .map(item -> text(((Map<?, ?>) item).get("category"))).distinct().sorted().toList();
    return new ProtocolSchemeCatalog("seer", categories, schemes);
  }

  @SuppressWarnings("unchecked")
  private ProtocolSchemeDetail detailSeer(String id) {
    Map<String, Object> scheme = seerSchemes().stream()
        .map(item -> (Map<String, Object>) item)
        .filter(item -> id.equals(text(item.get("id")))).findFirst()
        .orElseThrow(() -> new CatalogFailure(CatalogFailure.Type.NOT_FOUND, "Esquema SEER no encontrado."));
    return new ProtocolSchemeDetail(scheme, (List<Object>) scheme.getOrDefault("drugs", List.of()));
  }

  private List<Object> seerSchemes() {
    Path file = catalogRoot.resolve("seer-rx-regimens.csv");
    if (!Files.isRegularFile(file)) return List.of();
    try {
      List<List<String>> rows = FilesystemAjccCatalogStore.parseCsv(Files.readString(file));
      if (!rows.isEmpty()) rows.removeFirst();
      List<Object> result = new ArrayList<>();
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

  private List<Object> readArray(Path file) {
    if (!Files.isRegularFile(file)) return List.of();
    try {
      JsonNode root = mapper.readTree(Files.readString(file));
      List<Object> rows = new ArrayList<>();
      if (root.isArray()) {
        root.forEach(node -> rows.add(mapper.convertValue(node, Map.class)));
      }
      return rows;
    } catch (IOException exception) {
      throw new IllegalStateException("Catálogo inválido: " + file, exception);
    }
  }

  private Map<String, List<Map<String, Object>>> groupByDrug(List<Object> rows) {
    Map<String, List<Map<String, Object>>> groups = new HashMap<>();
    for (Object rowObject : rows) {
      @SuppressWarnings("unchecked")
      Map<String, Object> row = (Map<String, Object>) rowObject;
      groups.computeIfAbsent(text(row.get("idDroga")), ignored -> new ArrayList<>()).add(row);
    }
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
    result.put("applications", mapList(source.get("applications")).stream().map(this::preparation).toList());
    result.put("presentations", mapList(source.get("presentations")).stream().map(this::presentation).toList());
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
    result.put("stabilityRoomTemperature", firstText(source, "estabilidadTemp", "estabilidadTA"));
    result.put("stabilityRefrigerated", firstText(source, "estabilidadFrio", "estabilidadF"));
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

  private Map<String, Object> index(List<Object> values) {
    Map<String, Object> result = new HashMap<>();
    for (Object value : values) {
      Map<?, ?> item = (Map<?, ?>) value;
      result.put(text(item.get("id")), value);
    }
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
      List<Object> schemes,
      Map<String, Object> byId,
      List<String> categories,
      Map<String, List<Map<String, Object>>> applications,
      Map<String, List<Map<String, Object>>> presentations,
      Set<String> drugNames) {
  }
}
