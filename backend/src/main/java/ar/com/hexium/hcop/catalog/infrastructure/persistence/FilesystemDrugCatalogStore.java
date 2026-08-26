package ar.com.hexium.hcop.catalog.infrastructure.persistence;

import ar.com.hexium.hcop.catalog.application.port.in.LegacyProtocolCatalogUseCase;
import ar.com.hexium.hcop.catalog.application.port.out.DrugCatalogStore;
import ar.com.hexium.hcop.config.HcopProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class FilesystemDrugCatalogStore implements DrugCatalogStore {
  private final List<Object> drugs;

  public FilesystemDrugCatalogStore(
      HcopProperties properties, ObjectMapper mapper, LegacyProtocolCatalogUseCase protocols) {
    Map<String, Map<String, Object>> unique = new LinkedHashMap<>();
    try {
      for (Object itemObject : protocols.searchableDrugs()) {
        @SuppressWarnings("unchecked")
        Map<String, Object> item = (Map<String, Object>) itemObject;
        String name = String.valueOf(item.getOrDefault("name", "")).trim();
        if (!name.isBlank()) unique.putIfAbsent(normalize(name), new LinkedHashMap<>(item));
      }
    } catch (RuntimeException ignored) {
      // El vademecum local de apoyo sigue disponible si el catalogo COIR no puede leerse.
    }
    try {
      JsonNode source = mapper.readTree(
          Files.readString(properties.catalogRoot().resolve("medicamentos-ar-demo.json")));
      if (source.isArray()) {
        int index = 0;
        for (JsonNode item : source) {
          String generic = item.path("generic").asText("").trim();
          String brand = item.path("brand").asText("").trim();
          String presentation = item.path("presentation").asText("").trim();
          String name = !generic.isBlank() ? generic : brand;
          if (name.isBlank()) continue;
          String key = normalize(name);
          Map<String, Object> value = new LinkedHashMap<>();
          value.put("id", "med-" + (++index));
          value.put("name", name);
          value.put("nombre", name);
          value.put("genericName", generic);
          value.put("brand", brand);
          value.put("presentation", presentation);
          value.put("form", item.path("form").asText(""));
          value.put("laboratory", item.path("laboratory").asText(""));
          value.put("source", "catalogo-local");
          Map<String, Object> existing = unique.get(key);
          if (existing == null) {
            unique.put(key, value);
          } else if (String.valueOf(existing.getOrDefault("presentation", "")).isBlank()) {
            existing.put("presentation", presentation);
            existing.put("form", item.path("form").asText(""));
            existing.put("laboratory", item.path("laboratory").asText(""));
          }
        }
      }
    } catch (IOException ignored) {
      // El administrador de protocolos permite crear drogas manuales si falta el catálogo.
    }
    this.drugs = List.copyOf(unique.values());
  }

  @Override
  public List<Object> drugs() {
    return drugs;
  }

  private static String normalize(String value) {
    return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("\\s+", " ")
        .trim();
  }
}
