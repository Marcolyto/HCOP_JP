package ar.com.hexium.hcop.clinicalhistory.infrastructure.persistence;

import ar.com.hexium.hcop.clinicalhistory.application.port.out.ClinicalHistoryTemplatePort;
import ar.com.hexium.hcop.config.HcopProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Repository
public class ClinicalHistoryTemplateFileAdapter implements ClinicalHistoryTemplatePort {
  private final HcopProperties properties;
  private final ObjectMapper mapper;

  public ClinicalHistoryTemplateFileAdapter(HcopProperties properties, ObjectMapper mapper) {
    this.properties = properties;
    this.mapper = mapper;
  }

  @Override
  public JsonNode load() {
    try {
      Path file = properties.catalogRoot().resolve("hc-oncologica-vacia.json").normalize();
      if (Files.isRegularFile(file)) {
        JsonNode loaded = mapper.readTree(Files.readString(file));
        if (loaded != null && loaded.isObject()) return loaded;
      }
    } catch (IOException ignored) {
      // Fallback to deterministic template.
    }
    return defaultTemplate();
  }

  private JsonNode defaultTemplate() {
    ObjectNode document = mapper.createObjectNode();
    document.set("meta", mapper.createObjectNode());
    document.set("patient", mapper.createObjectNode());
    document.set("oncology", mapper.createObjectNode());
    document.set("exam", mapper.createObjectNode());
    document.set("evolutions", mapper.createArrayNode());
    document.set("studies", mapper.createArrayNode());
    document.set("prescriptions", mapper.createArrayNode());
    document.set("researchRecords", mapper.createArrayNode());
    return document;
  }
}
