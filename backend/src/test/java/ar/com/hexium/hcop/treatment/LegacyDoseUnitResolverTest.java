package ar.com.hexium.hcop.treatment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class LegacyDoseUnitResolverTest {
  private final JsonMapper mapper = JsonMapper.builder().build();

  @TempDir
  Path temporaryDirectory;

  @Test
  void resolvesOnlyAnUnambiguousPresentationUnit() throws Exception {
    Path source = temporaryDirectory.resolve("indicacionAplicacion.json");
    Files.writeString(source, """
        [
          {"idDroga":"9001","monodroga":"Droga A","presentaciones":"50 mg amp"},
          {"idDroga":"9001","monodroga":"Droga A","presentaciones":"100 mg amp"}
        ]
        """);
    LegacyDoseUnitResolver resolver = new LegacyDoseUnitResolver(source, mapper);

    assertThat(resolver.resolve(mapper.readTree("""
        {"idDroga":"9001","droga":"Droga A"}
        """))).isEqualTo("mg");
  }

  @Test
  void refusesToGuessWhenPresentationsDisagree() throws Exception {
    Path source = temporaryDirectory.resolve("indicacionAplicacion.json");
    Files.writeString(source, """
        [
          {"idDroga":"9002","monodroga":"Droga B","presentaciones":"50 mg amp"},
          {"idDroga":"9002","monodroga":"Droga B","presentaciones":"100 UI amp"}
        ]
        """);
    LegacyDoseUnitResolver resolver = new LegacyDoseUnitResolver(source, mapper);

    assertThat(resolver.resolve(mapper.readTree("""
        {"idDroga":"9002","droga":"Droga B"}
        """))).isEmpty();
  }

  @Test
  void resolvesEveryComponentInTheDistributedLegacyProtocols() throws Exception {
    Path catalog = Path.of("runtime/catalogs/protocolos-lira").toAbsolutePath().normalize();
    LegacyDoseUnitResolver resolver =
        new LegacyDoseUnitResolver(catalog.resolve("indicacionAplicacion.json"), mapper);
    List<String> unresolved = new ArrayList<>();

    try (var details = Files.list(catalog.resolve("esquemas"))) {
      for (Path detail : details
          .filter(path -> path.getFileName().toString().matches("detalle_\\d+\\.json"))
          .sorted()
          .toList()) {
        JsonNode components = mapper.readTree(Files.readString(detail));
        for (JsonNode component : components) {
          if (resolver.resolve(component).isBlank()) {
            unresolved.add(
                detail.getFileName() + ":"
                    + component.path("id").asText("?") + ":"
                    + component.path("droga").asText("?"));
          }
        }
      }
    }

    assertThat(unresolved).isEmpty();
  }
}
