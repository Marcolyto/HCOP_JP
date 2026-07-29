package ar.com.hexium.hcop.configuration;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.catalog.DrugCatalogService;
import ar.com.hexium.hcop.catalog.LegacyProtocolCatalogService;
import ar.com.hexium.hcop.catalog.TreatmentCatalogService;
import ar.com.hexium.hcop.catalog.TreatmentCatalogService.Scheme;
import ar.com.hexium.hcop.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@RestController
public class ProtocolController {
  private final ConfigurationService configurations;
  private final TreatmentCatalogService schemes;
  private final LegacyProtocolCatalogService coirCatalog;
  private final DrugCatalogService drugs;
  private final AuthContext auth;
  private final ObjectMapper mapper;

  public ProtocolController(
      ConfigurationService configurations,
      TreatmentCatalogService schemes,
      LegacyProtocolCatalogService coirCatalog,
      DrugCatalogService drugs,
      AuthContext auth,
      ObjectMapper mapper) {
    this.configurations = configurations;
    this.schemes = schemes;
    this.coirCatalog = coirCatalog;
    this.drugs = drugs;
    this.auth = auth;
    this.mapper = mapper;
  }

  @GetMapping("/api/clinical/protocols")
  Map<String, Object> list(
      @RequestParam(defaultValue = "0") int includeArchived,
      @RequestParam(defaultValue = "0") int includeCatalog,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.protocols.view");
    List<Map<String, Object>> custom = configurations.list("protocol", includeArchived == 1)
        .stream().map(this::protocol).toList();
    List<Map<String, Object>> result = new ArrayList<>(custom);
    if (includeCatalog == 1) {
      var linked = custom.stream()
          .map(item -> String.valueOf(item.getOrDefault("coirSchemeId", "")))
          .filter(value -> !value.isBlank())
          .collect(java.util.stream.Collectors.toSet());
      for (Scheme item : schemes.allSchemes()) {
        if (item.custom()) continue;
        if (!linked.contains(item.id())) result.add(catalogProtocol(item, false));
      }
    }
    long current = custom.stream().filter(item -> Boolean.TRUE.equals(item.get("active"))).count();
    return Map.of(
        "ok", true,
        "protocols", result,
        "total", result.size(),
        "currentCount", current,
        "catalogCount", result.size() - custom.size());
  }

  @GetMapping("/api/clinical/protocols/{id}")
  Map<String, Object> get(@PathVariable String id, HttpServletRequest request) {
    auth.requirePermission(request, "section.protocols.view");
    if (id.startsWith("coir-")) {
      String schemeId = id.substring("coir-".length());
      Scheme scheme = schemes.scheme(schemeId)
          .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Protocolo no encontrado."));
      return Map.of("ok", true, "protocol", catalogProtocol(scheme, true));
    }
    long numeric = numericId(id);
    Map<String, Object> item = configurations.list("protocol", true).stream()
        .filter(candidate -> id.equals(String.valueOf(candidate.get("id"))))
        .findFirst()
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Protocolo no encontrado."));
    return Map.of("ok", true, "protocol", protocol(item));
  }

  @PostMapping("/api/clinical/protocols")
  ResponseEntity<Map<String, Object>> create(
      @RequestBody JsonNode body,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.protocols.edit");
    ObjectNode configuration = configurationBody(body);
    Map<String, Object> protocol = protocol(configurations.create(
        "protocol", configuration, auth.require(request).userId()));
    schemes.invalidate();
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("ok", true, "protocol", protocol));
  }

  @PutMapping("/api/clinical/protocols/{id}")
  Map<String, Object> update(
      @PathVariable String id,
      @RequestBody JsonNode body,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.protocols.edit");
    Map<String, Object> protocol = protocol(configurations.update(
        "protocol", numericId(id), configurationBody(body), auth.require(request).userId()));
    schemes.invalidate();
    return Map.of("ok", true, "protocol", protocol);
  }

  @DeleteMapping("/api/clinical/protocols/{id}")
  Map<String, Object> archive(@PathVariable String id, HttpServletRequest request) {
    auth.requirePermission(request, "section.protocols.edit");
    Map<String, Object> protocol = protocol(configurations.archive(
        "protocol", numericId(id), auth.require(request).userId()));
    schemes.invalidate();
    return Map.of("ok", true, "protocol", protocol);
  }

  @GetMapping("/api/clinical/coir-catalog")
  Map<String, Object> coir(HttpServletRequest request) {
    auth.requirePermission(request, "section.protocols.view");
    List<Map<String, Object>> catalog = schemes.allSchemes().stream()
        .filter(item -> !item.custom()).map(item -> {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("coirSchemeId", item.id());
      row.put("schemeName", item.name());
      row.put("durationMinutes", item.durationMinutes());
      row.put("durationText", durationText(item.durationMinutes()));
      row.put("cycleDays", item.cycleDays());
      row.put("entryType", "treatment");
      return row;
    }).toList();
    return Map.of("ok", true, "catalog", catalog, "total", catalog.size());
  }

  @GetMapping("/api/clinical/drugs")
  Map<String, Object> drugs(
      @RequestParam(defaultValue = "") String q,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.protocols.view");
    List<Map<String, Object>> result = drugs.search(q);
    return Map.of("ok", true, "drugs", result, "total", result.size());
  }

  private ObjectNode configurationBody(JsonNode body) {
    ObjectNode result = mapper.createObjectNode();
    result.put("name", body.path("name").asText("").trim());
    result.put("description", body.path("description").asText(""));
    result.put("active", body.path("active").asBoolean(true));
    if (body.has("revision")) result.put("revision", body.path("revision").asLong());
    ObjectNode definition = result.putObject("definition");
    body.properties().forEach(entry -> {
      if (!Set.of("id", "name", "description", "active", "revision", "catalogOnly").contains(entry.getKey())) {
        definition.set(entry.getKey(), entry.getValue().deepCopy());
      }
    });
    return result;
  }

  private Map<String, Object> protocol(Map<String, Object> item) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.putAll(item);
    Object definitionValue = item.get("definition");
    if (definitionValue instanceof JsonNode definition) {
      definition.properties().forEach(entry -> result.put(entry.getKey(), mapper.convertValue(entry.getValue(), Object.class)));
    } else if (definitionValue instanceof Map<?, ?> definition) {
      definition.forEach((key, value) -> result.put(String.valueOf(key), value));
    }
    result.put("catalogOnly", false);
    Object components = result.getOrDefault("components", List.of());
    result.put("components", components);
    result.put("componentCount", components instanceof List<?> list ? list.size() : 0);
    Integer duration = integer(result.get("durationMinutes"));
    result.put("durationText", durationText(duration));
    result.putIfAbsent("category", "");
    result.putIfAbsent("cycleDays", 21);
    String coir = String.valueOf(result.getOrDefault("coirSchemeId", ""));
    result.put("coirLinks", coir.isBlank() || "null".equals(coir)
        ? List.of() : List.of(Map.of("coirSchemeId", coir)));
    return result;
  }

  private Map<String, Object> catalogProtocol(Scheme scheme, boolean includeComponents) {
    Map<String, Object> result = new LinkedHashMap<>();
    String id = scheme.id();
    result.put("id", "coir-" + id);
    result.put("coirSchemeId", id);
    result.put("name", scheme.name());
    result.put("category", category(scheme.name()));
    result.put("description", "Esquema operativo importado del catálogo COIR.");
    result.put("cycleDays", scheme.cycleDays());
    result.put("durationMinutes", scheme.durationMinutes());
    result.put("durationText", durationText(scheme.durationMinutes()));
    result.put("active", true);
    result.put("catalogOnly", true);
    List<Map<String, Object>> components = includeComponents
        ? coirCatalog.clinicalComponents(id) : List.of();
    result.put("components", components);
    result.put("componentCount", catalogComponentCount(scheme));
    result.put("coirLinks", List.of());
    return result;
  }

  private int catalogComponentCount(Scheme scheme) {
    JsonNode source = scheme.definition().path("drugs");
    if (!source.isArray()) source = scheme.definition().path("drogas");
    if (!source.isArray()) source = scheme.definition().path("components");
    return source.isArray() ? source.size() : 0;
  }

  private String category(String name) {
    String value = name == null ? "" : name.trim();
    int separator = value.indexOf(" - ");
    if (separator < 0) separator = value.indexOf(':');
    if (separator > 0) value = value.substring(0, separator);
    return value.isBlank() ? "Otros" : value;
  }

  private long numericId(String value) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException invalid) {
      throw new ApiException(HttpStatus.NOT_FOUND, "Protocolo no encontrado.");
    }
  }

  private Integer integer(Object value) {
    if (value instanceof Number number) return number.intValue();
    try {
      String text = String.valueOf(value);
      return text.isBlank() || "null".equals(text) ? null : Integer.valueOf(text);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private String durationText(Integer minutes) {
    if (minutes == null || minutes < 1) return "";
    return minutes < 60 ? minutes + " min"
        : (minutes / 60) + " h" + (minutes % 60 == 0 ? "" : " " + (minutes % 60) + " min");
  }

}
