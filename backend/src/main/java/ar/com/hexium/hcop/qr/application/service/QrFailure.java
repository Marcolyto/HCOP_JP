package ar.com.hexium.hcop.qr.application.service;

/** Error funcional del módulo, independiente de HTTP. */
public final class QrFailure extends RuntimeException {
  private final Type type;

  public QrFailure(Type type, String message) {
    super(message);
    this.type = type;
  }

  public Type type() {
    return type;
  }

  public enum Type {
    INVALID,
    NOT_FOUND,
    CONFLICT
  }
}
