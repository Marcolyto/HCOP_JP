package ar.com.hexium.hcop.sharedkernel.domain;

/**
 * Revisión positiva utilizada para control optimista de concurrencia.
 */
public record Revision(long value) {

  public Revision {
    if (value < 1) throw new IllegalArgumentException("La revisión debe ser positiva.");
  }

  public static Revision initial() {
    return new Revision(1);
  }

  public Revision next() {
    if (value == Long.MAX_VALUE) throw new IllegalStateException("La revisión alcanzó su límite.");
    return new Revision(value + 1);
  }
}
