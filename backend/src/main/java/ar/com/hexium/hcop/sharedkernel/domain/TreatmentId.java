package ar.com.hexium.hcop.sharedkernel.domain;

import java.util.Objects;

/**
 * Identificador opaco de un tratamiento.
 */
public record TreatmentId(String value) {

  public TreatmentId {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isEmpty()) throw new IllegalArgumentException("El identificador de tratamiento es obligatorio.");
    if (value.length() > 128) {
      throw new IllegalArgumentException("El identificador de tratamiento supera 128 caracteres.");
    }
  }

  public static TreatmentId of(String value) {
    return new TreatmentId(value);
  }
}
