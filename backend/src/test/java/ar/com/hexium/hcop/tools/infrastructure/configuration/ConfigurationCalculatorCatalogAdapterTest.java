package ar.com.hexium.hcop.tools.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase;
import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase.ConfigurationView;
import ar.com.hexium.hcop.configuration.domain.ConfigurationDefinition;
import ar.com.hexium.hcop.tools.domain.CalculatorSummary;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConfigurationCalculatorCatalogAdapterTest {

  @Test
  void proyectaSoloCalculadorasActivas() {
    ConfigurationManagementUseCase configurations = mock(ConfigurationManagementUseCase.class);
    when(configurations.list("calculator", false)).thenReturn(List.of(
        view("17", "bsa-local", "SC institucional", true, 4, Map.of("mode", "formula")),
        view("18", "archived", "Archivada", false, 2, Map.of("mode", "score"))));
    ConfigurationCalculatorCatalogAdapter adapter = new ConfigurationCalculatorCatalogAdapter(configurations);

    List<CalculatorSummary> active = adapter.activeCalculators();

    assertThat(active).singleElement().satisfies(item -> {
      assertThat(item.id()).isEqualTo("17");
      assertThat(item.key()).isEqualTo("bsa-local");
      assertThat(item.revision()).isEqualTo(4);
      assertThat(item.definition()).isEqualTo(Map.of("mode", "formula"));
    });
  }

  @Test
  void devuelveVacioSinAjustesActivos() {
    ConfigurationManagementUseCase configurations = mock(ConfigurationManagementUseCase.class);
    when(configurations.list("tool-settings", false)).thenReturn(List.of());
    ConfigurationCalculatorCatalogAdapter adapter = new ConfigurationCalculatorCatalogAdapter(configurations);

    Optional<CalculatorSummary> settings = adapter.activeToolSettings();

    assertThat(settings).isEmpty();
  }

  @Test
  void proyectaElPrimerAjusteInstitucionalActivo() {
    ConfigurationManagementUseCase configurations = mock(ConfigurationManagementUseCase.class);
    when(configurations.list("tool-settings", false)).thenReturn(List.of(
        view("", "default", "Herramientas", true, 0, Map.of("enabled", true))));
    ConfigurationCalculatorCatalogAdapter adapter = new ConfigurationCalculatorCatalogAdapter(configurations);

    Optional<CalculatorSummary> settings = adapter.activeToolSettings();

    assertThat(settings).isPresent();
    assertThat(settings.get().key()).isEqualTo("default");
  }

  private ConfigurationView view(
      String id, String key, String name, boolean active, long revision, Object definition) {
    return new ConfigurationView(
        id, "calculator", key, name, "Descripción", active,
        ConfigurationDefinition.of(definition), revision, Instant.EPOCH, Instant.EPOCH);
  }
}
