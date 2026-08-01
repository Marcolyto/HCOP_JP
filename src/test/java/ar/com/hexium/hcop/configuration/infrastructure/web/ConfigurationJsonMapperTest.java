package ar.com.hexium.hcop.configuration.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase.ConfigurationView;
import ar.com.hexium.hcop.configuration.domain.ConfigurationDefinition;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ConfigurationJsonMapperTest {
  private final JsonMapper objectMapper = JsonMapper.builder().build();
  private final ConfigurationJsonMapper mapper = new ConfigurationJsonMapper(objectMapper);

  @Test
  void conservaLaFormaHistoricaDeUnElementoPersistido() {
    Map<String, Object> view = mapper.view(new ConfigurationView(
        "12",
        "calculator",
        "imc",
        "IMC",
        "Índice de masa corporal",
        true,
        ConfigurationDefinition.of(Map.of("expression", "peso/talla")),
        3,
        Instant.parse("2026-07-30T10:00:00Z"),
        Instant.parse("2026-07-30T11:00:00Z")));

    assertThat(view.keySet()).containsExactly(
        "id",
        "kind",
        "key",
        "name",
        "active",
        "revision",
        "definition",
        "itemKind",
        "itemKey",
        "displayName",
        "description",
        "createdAt",
        "updatedAt");
    assertThat(view.get("id")).isEqualTo("12");
  }

  @Test
  void conservaLaFormaMinimaDeLosValoresPredeterminados() {
    Map<String, Object> view = mapper.view(new ConfigurationView(
        "",
        "tool-settings",
        "default",
        "Herramientas",
        "",
        true,
        ConfigurationDefinition.of(Map.of("enabled", true)),
        0,
        Instant.EPOCH,
        Instant.EPOCH));

    assertThat(view.keySet()).containsExactly(
        "id",
        "kind",
        "key",
        "name",
        "active",
        "revision",
        "definition");
  }

  @Test
  void mueveCamposDinamicosAUnaDefinicionSinMetadatos() {
    var input = objectMapper.readTree("""
        {
          "name": "Calculadora",
          "active": true,
          "expression": "peso / talla",
          "variables": ["peso", "talla"]
        }
        """);

    var command = mapper.createCommand("calculator", input, 5);
    Map<?, ?> definition = (Map<?, ?>) command.definition().value();

    assertThat(definition.get("expression")).isEqualTo("peso / talla");
    assertThat(definition.get("variables")).isEqualTo(java.util.List.of("peso", "talla"));
    assertThat(definition.containsKey("name")).isFalse();
  }

  @Test
  void aceptaLaRevisionEsperadaQueEnviaLaInterfazDeConfiguracion() {
    var input = objectMapper.readTree("""
        {
          "name": "Guía",
          "expectedRevision": 7,
          "definition": {
            "fileName": "guia.pdf"
          }
        }
        """);

    var command = mapper.updateCommand("guide", 12, input, 5);
    Map<?, ?> definition = (Map<?, ?>) command.definition().value();

    assertThat(command.expectedRevision()).isEqualTo(7);
    assertThat(definition.get("fileName")).isEqualTo("guia.pdf");
    assertThat(definition.containsKey("expectedRevision")).isFalse();
  }
}
