package ar.com.hexium.hcop.admin.application.service;

/**
 * Error funcional del módulo, independiente de HTTP.
 */
public final class AdminFailure extends RuntimeException {
  private final Type type;

  public AdminFailure(Type type, String message) {
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
