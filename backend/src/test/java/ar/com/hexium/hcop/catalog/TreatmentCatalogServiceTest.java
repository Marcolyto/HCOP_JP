package ar.com.hexium.hcop.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.hexium.hcop.config.HcopProperties;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import tools.jackson.databind.json.JsonMapper;

class TreatmentCatalogServiceTest {

  @Test
  void exposesTheCompleteCatalogIncludingBreastProtocols() {
    Path runtime = Path.of("runtime").toAbsolutePath().normalize();
    HcopProperties properties = new HcopProperties(
        runtime,
        runtime.resolve("catalogs"),
        runtime.resolve("storage"),
        "http://127.0.0.1:5180",
        "HCOP_SESSION",
        60,
        262_144_000L,
        15_728_640L,
        "test-qr-secret",
        "test-encryption-secret");
    TreatmentCatalogService catalog = new TreatmentCatalogService(
        properties,
        JsonMapper.builder().build(),
        new JdbcTemplate() {
          @Override
          public void query(String sql, RowCallbackHandler callback) {
            // Esta prueba aísla el catálogo distribuido; no agrega protocolos personalizados.
          }
        });

    var schemes = catalog.schemes("");

    assertThat(schemes).hasSizeGreaterThan(200);
    assertThat(schemes)
        .extracting(item -> String.valueOf(item.get("nombre")))
        .anyMatch(name -> name.toUpperCase().contains("MAMA"));
    assertThat(catalog.scheme("347"))
        .isPresent()
        .get()
        .extracting(TreatmentCatalogService.Scheme::durationMinutes)
        .isEqualTo(120);
  }
}
