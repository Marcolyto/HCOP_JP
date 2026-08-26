package ar.com.hexium.hcop.media.infrastructure.persistence;

import ar.com.hexium.hcop.platform.HcopProperties;
import ar.com.hexium.hcop.media.application.port.out.StudyTemplateManifestStore;
import ar.com.hexium.hcop.media.application.service.MediaFailure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
      throw new MediaFailure(MediaFailure.Type.INTERNAL, "No se pudo abrir la biblioteca anatómica.");
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
