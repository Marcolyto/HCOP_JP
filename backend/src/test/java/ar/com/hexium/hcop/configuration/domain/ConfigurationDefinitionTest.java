package ar.com.hexium.hcop.configuration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigurationDefinitionTest {

  @Test
  void copiaElArbolYLoExponeComoValorInmutable() {
    List<Object> fields = new ArrayList<>(List.of("peso", "talla"));
    Map<String, Object> source = new LinkedHashMap<>();
    source.put("enabled", true);
    source.put("fields", fields);

    ConfigurationDefinition definition = ConfigurationDefinition.of(source);
    source.put("enabled", false);
    fields.add("edad");

    Map<?, ?> stored = (Map<?, ?>) definition.value();
    assertThat(stored.get("enabled")).isEqualTo(true);
    assertThat(stored.get("fields")).isEqualTo(List.of("peso", "talla"));
    assertThatThrownBy(() -> ((Map<Object, Object>) stored).put("other", true))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rechazaTiposQueNoPertenecenAUnDocumentoEstructurado() {
    assertThatThrownBy(() -> ConfigurationDefinition.of(Map.of("clock", new Object())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tipo no soportado");
  }
}
