package ar.com.hexium.hcop.guide.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.hexium.hcop.config.HcopProperties;
import ar.com.hexium.hcop.guide.domain.GuideFileName;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalGuideFileStoreTest {
  @TempDir
  Path temporary;

  @Test
  void storesMutableGuidesInPersistentStorageInsteadOfBundledCatalogs() throws Exception {
    Path catalogRoot = temporary.resolve("catalogs");
    Path storageRoot = temporary.resolve("storage");
    var properties = new HcopProperties(
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
    var store = new LocalGuideFileStore(properties);
    byte[] pdf = "%PDF-1.4\n% HCOP test\n".getBytes(StandardCharsets.US_ASCII);
    GuideFileName name = GuideFileName.fromRaw("guia-prueba.pdf");

    store.store(name, new ByteArrayInputStream(pdf), 1024);

    assertThat(storageRoot.resolve("guides/guia-prueba.pdf")).exists();
    assertThat(catalogRoot.resolve("guides/guia-prueba.pdf")).doesNotExist();
    assertThat(store.list())
        .singleElement()
        .extracting(item -> item.name().value())
        .isEqualTo("guia-prueba.pdf");
    try (var content = store.open(name).orElseThrow().content()) {
      assertThat(content.readAllBytes()).isEqualTo(pdf);
    }
    try (var temporaryFiles = Files.list(storageRoot.resolve("guides"))) {
      assertThat(temporaryFiles.filter(path -> path.toString().endsWith(".part")))
          .isEmpty();
    }
  }
}
