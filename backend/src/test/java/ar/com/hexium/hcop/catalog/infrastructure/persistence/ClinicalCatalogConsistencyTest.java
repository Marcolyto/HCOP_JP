package ar.com.hexium.hcop.catalog.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.hexium.hcop.catalog.application.service.DrugCatalogApplicationService;
import ar.com.hexium.hcop.catalog.application.service.LegacyProtocolCatalogApplicationService;
import ar.com.hexium.hcop.config.HcopProperties;
import java.nio.file.Path;
import java.util.Map;
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
    PostgresTreatmentSchemeStore treatments = new PostgresTreatmentSchemeStore(properties, mapper, emptyJdbc());
    FilesystemLegacyProtocolCatalogStore protocolStore = new FilesystemLegacyProtocolCatalogStore(
        properties, mapper, new ar.com.hexium.hcop.catalog.application.service.TreatmentCatalogApplicationService(treatments));
    var protocols = new LegacyProtocolCatalogApplicationService(protocolStore);
    FilesystemDrugCatalogStore drugStore = new FilesystemDrugCatalogStore(properties, mapper, protocols);
    var drugs = new DrugCatalogApplicationService(drugStore);

    var components = protocols.clinicalComponents("347");

    assertThat(components)
        .extracting(item -> String.valueOf(((Map<?, ?>) item).get("drugName")))
        .containsExactly("Ondansetron", "Irinotecan");
    @SuppressWarnings("unchecked")
    Map<String, Object> second = (Map<String, Object>) components.get(1);
    assertThat(second)
        .containsEntry("drugId", "101")
        .containsEntry("administrationTime", "EV 90 MIN")
        .containsEntry("route", "Endovenosa");
    assertThat((java.util.List<?>) second.get("applications")).isNotEmpty();
    assertThat((java.util.List<?>) second.get("presentations")).isNotEmpty();

    assertThat(drugs.total()).isGreaterThan(100);
    assertThat(drugs.search("irinotecan"))
        .filteredOn(item -> "101".equals(((Map<?, ?>) item).get("id")))
        .singleElement()
        .satisfies(item -> {
          @SuppressWarnings("unchecked")
          Map<String, Object> value = (Map<String, Object>) item;
          assertThat(value).containsEntry("id", "101");
          assertThat((java.util.List<?>) value.get("instructions")).isNotEmpty();
          assertThat((java.util.List<?>) value.get("presentations")).isNotEmpty();
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
