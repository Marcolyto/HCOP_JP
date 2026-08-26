package ar.com.hexium.hcop.treatment.application.service;

/** Error funcional del módulo, independiente de HTTP. */
public final class TreatmentFailure extends RuntimeException {
  private final Type type;

  public TreatmentFailure(Type type, String message) {
    super(message);
    this.type = type;
  }

  public Type type() {
    return type;
  }

  public enum Type {
    INVALID,
    NOT_FOUND,
    UNPROCESSABLE
  }
}
