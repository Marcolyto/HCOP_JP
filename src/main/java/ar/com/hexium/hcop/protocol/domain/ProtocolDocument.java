package ar.com.hexium.hcop.protocol.domain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Documento estructurado inmutable para los campos variables de un protocolo.
 */
public final class ProtocolDocument {
  private final Map<String, Object> value;

  private ProtocolDocument(Map<String, Object> value) {
    this.value = freezeMap(Objects.requireNonNull(value, "value"));
  }

  public static ProtocolDocument of(Map<String, ?> value) {
    return new ProtocolDocument(new LinkedHashMap<>(value));
  }

  public static ProtocolDocument empty() {
    return new ProtocolDocument(Map.of());
  }

  public Map<String, Object> value() {
    return value;
  }

  public String text(String key) {
    Object candidate = value.get(key);
    return candidate == null ? "" : String.valueOf(candidate).strip();
  }

  public Integer integer(String key) {
    Object candidate = value.get(key);
    if (candidate instanceof Number number) return number.intValue();
    try {
      String text = candidate == null ? "" : String.valueOf(candidate).strip();
      return text.isBlank() || "null".equals(text) ? null : Integer.valueOf(text);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  public List<ProtocolDocument> documents(String key) {
    Object candidate = value.get(key);
    if (!(candidate instanceof List<?> list)) return List.of();
    List<ProtocolDocument> result = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> map) result.add(of(stringMap(map)));
    }
    return List.copyOf(result);
  }

  private static Map<String, Object> stringMap(Map<?, ?> source) {
    Map<String, Object> result = new LinkedHashMap<>();
    source.forEach((key, nested) -> result.put(String.valueOf(key), nested));
    return result;
  }

  private static Map<String, Object> freezeMap(Map<?, ?> source) {
    Map<String, Object> copy = new LinkedHashMap<>();
    source.forEach((key, nested) -> {
      if (!(key instanceof String text) || text.isBlank()) {
        throw new IllegalArgumentException("Las claves del protocolo deben ser texto no vacío.");
      }
      copy.put(text, freeze(nested));
    });
    return Collections.unmodifiableMap(copy);
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
      if (!Float.isFinite(number)) throw new IllegalArgumentException("El protocolo contiene un número inválido.");
      return number;
    }
    if (candidate instanceof Double number) {
      if (!Double.isFinite(number)) throw new IllegalArgumentException("El protocolo contiene un número inválido.");
      return number;
    }
    if (candidate instanceof Map<?, ?> map) return freezeMap(map);
    if (candidate instanceof List<?> list) {
      List<Object> copy = new ArrayList<>(list.size());
      list.forEach(item -> copy.add(freeze(item)));
      return Collections.unmodifiableList(copy);
    }
    throw new IllegalArgumentException(
        "El protocolo contiene un tipo no soportado: " + candidate.getClass().getSimpleName());
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || other instanceof ProtocolDocument document && value.equals(document.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }
}
