package ar.com.hexium.hcop.catalog.infrastructure.persistence;

import ar.com.hexium.hcop.catalog.application.port.out.SystemicFormCatalogStore;
import ar.com.hexium.hcop.config.HcopProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class FilesystemSystemicFormCatalogStore implements SystemicFormCatalogStore {
  private final JsonNode forms;
  private final ObjectMapper mapper;

  public FilesystemSystemicFormCatalogStore(HcopProperties properties, ObjectMapper mapper) {
    this.mapper = mapper;
    try {
      this.forms = mapper.readTree(Files.readString(properties.catalogRoot().resolve("systemic-forms.json")));
    } catch (IOException exception) {
      throw new IllegalStateException("No se pudo cargar el catálogo de formularios.", exception);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Optional<List<Object>> forms() {
    if (!forms.isArray()) return Optional.empty();
    return Optional.of(mapper.convertValue(forms, List.class));
  }

  @Override
  public Object find(String id) {
    for (JsonNode form : forms) {
      if (form.path("id").asText("").equals(id)) return mapper.convertValue(form, Object.class);
    }
    return null;
  }
}
