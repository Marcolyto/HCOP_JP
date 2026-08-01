package ar.com.hexium.hcop.clinicalhistory.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.hexium.hcop.clinicalhistory.infrastructure.persistence.ClinicalHistoryTemplateFileAdapter;
import ar.com.hexium.hcop.config.HcopProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ClinicalHistoryTemplateFileAdapterTest {
  @TempDir
  Path temporary;

  @Test
  void fallbackToDeterministicTemplateWhenTemplateFileIsMissing() {
    Path catalogRoot = temporary.resolve("catalogs");
    Path storageRoot = temporary.resolve("storage");
    var properties = properties(catalogRoot, storageRoot);
    var adapter = new ClinicalHistoryTemplateFileAdapter(properties, new ObjectMapper());

    JsonNode template = adapter.load();

    assertThat(template.has("patient")).isTrue();
    assertThat(template.path("evolutions").isArray()).isTrue();
  }

  @Test
  void fallbackToDeterministicTemplateWhenTemplateFileIsInvalid() throws Exception {
    Path catalogRoot = temporary.resolve("catalogs");
    Path templateFile = catalogRoot.resolve("hc-oncologica-vacia.json");
    Files.createDirectories(catalogRoot);
    Files.writeString(templateFile, "[]", StandardCharsets.UTF_8);

    var properties = properties(catalogRoot, temporary.resolve("storage"));
    var adapter = new ClinicalHistoryTemplateFileAdapter(properties, new ObjectMapper());

    JsonNode template = adapter.load();

    assertThat(template.has("oncology")).isTrue();
  }

  @Test
  void loadsTemplateWhenCatalogFileContainsAnObject() throws Exception {
    Path catalogRoot = temporary.resolve("catalogs");
    Path templateFile = catalogRoot.resolve("hc-oncologica-vacia.json");
    Files.createDirectories(catalogRoot);
    Files.writeString(templateFile, "{\"meta\":{\"version\":1}}", StandardCharsets.UTF_8);

    var properties = properties(catalogRoot, temporary.resolve("storage"));
    var adapter = new ClinicalHistoryTemplateFileAdapter(properties, new ObjectMapper());

    JsonNode template = adapter.load();

    assertThat(template.path("meta").path("version").asInt()).isEqualTo(1);
  }

  private HcopProperties properties(Path catalogRoot, Path storageRoot) {
    return new HcopProperties(
        temporary,
        catalogRoot,
        storageRoot,
        "http://127.0.0.1:5180",
        "HCOP_SESSION",
        60,
        1024,
        1024,
        "test-qr-secret",
        "test-encryption-secret");
  }
}
