package ar.com.hexium.hcop.catalog.infrastructure.persistence;

import ar.com.hexium.hcop.catalog.application.port.out.DiagnosisEquivalenceStore;
import ar.com.hexium.hcop.catalog.domain.DiagnosisEquivalence;
import ar.com.hexium.hcop.platform.HcopProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class FilesystemDiagnosisEquivalenceStore implements DiagnosisEquivalenceStore {
  private final HcopProperties properties;
  private final ObjectMapper mapper;
  private volatile List<DiagnosisEquivalence> cache;

  public FilesystemDiagnosisEquivalenceStore(HcopProperties properties, ObjectMapper mapper) {
    this.properties = properties;
    this.mapper = mapper;
  }

  @Override
  public List<DiagnosisEquivalence> equivalences() {
    List<DiagnosisEquivalence> snapshot = cache;
    if (snapshot != null) return snapshot;
    synchronized (this) {
      if (cache != null) return cache;
      var file = properties.catalogRoot().resolve("diagnosis-equivalences.json");
      List<DiagnosisEquivalence> loaded = new ArrayList<>();
      try {
        JsonNode root = mapper.readTree(Files.readString(file));
        for (JsonNode row : root) {
          if (!row.isArray() || row.size() < 6) continue;
          loaded.add(new DiagnosisEquivalence(
              row.get(0).asText(), row.get(1).asText(), row.get(2).asText(),
              row.get(3).asText(), row.get(4).asText(), row.get(5).asText()));
        }
      } catch (IOException exception) {
        throw new IllegalStateException("No se pudo leer el catálogo diagnóstico local.", exception);
      }
      cache = List.copyOf(loaded);
      return cache;
    }
  }
}
