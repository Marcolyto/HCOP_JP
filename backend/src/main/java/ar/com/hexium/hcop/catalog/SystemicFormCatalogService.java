package ar.com.hexium.hcop.catalog;

import ar.com.hexium.hcop.config.HcopProperties;
import java.io.IOException;
import java.nio.file.Files;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class SystemicFormCatalogService {
  private final JsonNode forms;

  public SystemicFormCatalogService(HcopProperties properties, ObjectMapper mapper) {
    try {
      this.forms = mapper.readTree(Files.readString(
          properties.catalogRoot().resolve("systemic-forms.json")));
    } catch (IOException exception) {
      throw new IllegalStateException("No se pudo cargar el catálogo de formularios.", exception);
    }
  }

  public JsonNode forms() {
    return forms;
  }

  public JsonNode find(String id) {
    for (JsonNode form : forms) {
      if (form.path("id").asText("").equals(id)) return form;
    }
    return null;
  }
}
