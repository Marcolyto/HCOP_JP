package ar.com.hexium.hcop.media.infrastructure.persistence;

import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.config.HcopProperties;
import ar.com.hexium.hcop.media.application.port.out.StudyTemplateManifestStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class FilesystemStudyTemplateManifestStore implements StudyTemplateManifestStore {
  private final Path manifestPath;
  private final ObjectMapper mapper;

  public FilesystemStudyTemplateManifestStore(HcopProperties properties, ObjectMapper mapper) {
    this.manifestPath = properties.catalogRoot().resolve("study-templates").resolve("manifest.json");
    this.mapper = mapper;
  }

  @Override
  public List<Object> templates() {
    JsonNode manifest;
    try {
      manifest = mapper.readTree(Files.newInputStream(manifestPath));
    } catch (IOException error) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo abrir la biblioteca anatómica.");
    }
    List<Object> templates = new ArrayList<>();
    for (JsonNode template : manifest.path("templates")) {
      @SuppressWarnings("unchecked")
      Map<String, Object> row = new LinkedHashMap<>(mapper.convertValue(template, Map.class));
      row.put("origin", "bundled");
      row.put("active", true);
      row.put("available", true);
      templates.add(row);
    }
    return templates;
  }
}
