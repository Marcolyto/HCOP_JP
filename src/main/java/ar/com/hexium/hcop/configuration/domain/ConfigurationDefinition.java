package ar.com.hexium.hcop.configuration.domain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Árbol de datos independiente de JSON para formularios, calculadoras y parámetros configurables.
 */
public final class ConfigurationDefinition {
  private final Object value;

  private ConfigurationDefinition(Object value) {
    this.value = freeze(Objects.requireNonNull(value, "value"));
    if (!(this.value instanceof Map<?, ?>) && !(this.value instanceof List<?>)) {
      throw new IllegalArgumentException("La definición debe ser un objeto o una lista.");
    }
  }

  public static ConfigurationDefinition of(Object value) {
    return new ConfigurationDefinition(value);
  }

  public static ConfigurationDefinition emptyObject() {
    return new ConfigurationDefinition(Map.of());
  }

  /**
   * Devuelve un árbol inmutable compuesto sólo por mapas, listas y escalares.
   */
  public Object value() {
    return value;
  }

  private static Object freeze(Object candidate) {
    if (candidate == null
        || candidate instanceof String
        || candidate instanceof Boolean
        || candidate instanceof Integer
        || candidate instanceof Long
        || candidate instanceof Short
        || candidate instanceof Byte
        || candidate instanceof BigInteger
        || candidate instanceof BigDecimal) {
      return candidate;
    }
    if (candidate instanceof Float number) {
      if (!Float.isFinite(number)) throw new IllegalArgumentException("La definición contiene un número inválido.");
      return number;
    }
    if (candidate instanceof Double number) {
      if (!Double.isFinite(number)) throw new IllegalArgumentException("La definición contiene un número inválido.");
      return number;
    }
    if (candidate instanceof Map<?, ?> map) {
      Map<String, Object> copy = new LinkedHashMap<>();
      map.forEach((key, nested) -> {
        if (!(key instanceof String text) || text.isBlank()) {
          throw new IllegalArgumentException("Las claves de la definición deben ser texto no vacío.");
        }
        copy.put(text, freeze(nested));
      });
      return Collections.unmodifiableMap(copy);
    }
    if (candidate instanceof List<?> list) {
      List<Object> copy = new ArrayList<>(list.size());
      list.forEach(nested -> copy.add(freeze(nested)));
      return Collections.unmodifiableList(copy);
    }
    throw new IllegalArgumentException(
        "La definición contiene un tipo no soportado: " + candidate.getClass().getSimpleName());
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || other instanceof ConfigurationDefinition definition
        && value.equals(definition.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
