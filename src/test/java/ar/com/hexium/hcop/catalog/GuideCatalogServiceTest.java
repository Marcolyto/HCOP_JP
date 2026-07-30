package ar.com.hexium.hcop.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.config.HcopProperties;
import ar.com.hexium.hcop.configuration.ConfigurationService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GuideCatalogServiceTest {

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
    ConfigurationService configurations = mock(ConfigurationService.class);
    when(configurations.list("guide", true)).thenReturn(List.of());
    var service = new GuideCatalogService(properties, configurations);
    byte[] pdf = "%PDF-1.4\n% HCOP test\n".getBytes(StandardCharsets.US_ASCII);

    service.store("guia-prueba.pdf", new ByteArrayInputStream(pdf), pdf.length);

    assertThat(storageRoot.resolve("guides/guia-prueba.pdf")).exists();
    assertThat(catalogRoot.resolve("guides/guia-prueba.pdf")).doesNotExist();
    assertThat(service.list(false))
        .singleElement()
        .extracting(item -> item.get("name"))
        .isEqualTo("guia-prueba.pdf");
    assertThat(Files.readAllBytes(service.file("guia-prueba.pdf"))).isEqualTo(pdf);
  }
}
