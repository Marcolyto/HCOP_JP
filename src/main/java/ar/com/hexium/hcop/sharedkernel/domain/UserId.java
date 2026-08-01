package ar.com.hexium.hcop.sharedkernel.domain;

/**
 * Identificador local e inmutable del actor clínico o administrativo.
 */
public record UserId(long value) {

  public UserId {
    if (value < 1) throw new IllegalArgumentException("El identificador de usuario debe ser positivo.");
  }

  public static UserId of(long value) {
    return new UserId(value);
  }
}
