package ar.com.hexium.hcop.protocol.domain;

import java.util.Objects;

/**
 * Identificador externo de un protocolo personalizado o del catálogo COIR.
 */
public record ProtocolId(String value, Source source) {
  public enum Source {
    CUSTOM,
    COIR
  }

  public ProtocolId {
    value = Objects.requireNonNull(value, "value").strip();
    source = Objects.requireNonNull(source, "source");
    if (value.isBlank()) throw new IllegalArgumentException("El identificador del protocolo es obligatorio.");
  }

  public static ProtocolId custom(long id) {
    if (id < 1) throw new IllegalArgumentException("El identificador del protocolo es inválido.");
    return new ProtocolId(Long.toString(id), Source.CUSTOM);
  }

  public static ProtocolId coir(String schemeId) {
    String normalized = Objects.requireNonNull(schemeId, "schemeId").strip();
    if (normalized.isBlank()) throw new IllegalArgumentException("El identificador COIR es obligatorio.");
    return new ProtocolId("coir-" + normalized, Source.COIR);
  }

  public static ProtocolId parse(String externalId) {
    String normalized = externalId == null ? "" : externalId.strip();
    if (normalized.startsWith("coir-") && normalized.length() > 5) {
      return coir(normalized.substring(5));
    }
    try {
      return custom(Long.parseLong(normalized));
    } catch (NumberFormatException invalid) {
      throw new IllegalArgumentException("El protocolo no existe.", invalid);
    }
  }

  public boolean catalog() {
    return source == Source.COIR;
  }

  public long customValue() {
    if (catalog()) throw new IllegalStateException("Un protocolo COIR no tiene identificador numérico local.");
    return Long.parseLong(value);
  }

  public String coirValue() {
    if (!catalog()) throw new IllegalStateException("Un protocolo local no tiene identificador COIR.");
    return value.substring("coir-".length());
  }
}
