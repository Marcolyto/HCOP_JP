package ar.com.hexium.hcop.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.hexium.hcop.config.HcopProperties;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class ClinicalCatalogConsistencyTest {

  @Test
  void exposesCoirComponentsPreparationsAndSearchableOncologyDrugs() {
    Path runtime = Path.of("runtime").toAbsolutePath().normalize();
    HcopProperties properties = properties(runtime);
    ObjectMapper mapper = JsonMapper.builder().build();
    TreatmentCatalogService treatments = new TreatmentCatalogService(
        properties, mapper, emptyJdbc());
    LegacyProtocolCatalogService protocols = new LegacyProtocolCatalogService(
        properties, mapper, treatments);
    DrugCatalogService drugs = new DrugCatalogService(properties, mapper, protocols);

    var components = protocols.clinicalComponents("347");

    assertThat(components)
        .extracting(item -> String.valueOf(item.get("drugName")))
        .containsExactly("Ondansetron", "Irinotecan");
    assertThat(components.get(1))
        .containsEntry("drugId", "101")
        .containsEntry("administrationTime", "EV 90 MIN")
        .containsEntry("route", "Endovenosa");
    assertThat((java.util.List<?>) components.get(1).get("applications")).isNotEmpty();
    assertThat((java.util.List<?>) components.get(1).get("presentations")).isNotEmpty();

    assertThat(drugs.total()).isGreaterThan(100);
    assertThat(drugs.search("irinotecan"))
        .filteredOn(item -> "101".equals(item.get("id")))
        .singleElement()
        .satisfies(item -> {
          assertThat(item).containsEntry("id", "101");
          assertThat((java.util.List<?>) item.get("instructions")).isNotEmpty();
          assertThat((java.util.List<?>) item.get("presentations")).isNotEmpty();
        });
  }

  private HcopProperties properties(Path runtime) {
    return new HcopProperties(
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
  }

  private JdbcTemplate emptyJdbc() {
    return new JdbcTemplate() {
      @Override
      public void query(String sql, RowCallbackHandler callback) {
        // This test isolates the distributed catalogs from custom database items.
      }
    };
  }
}
