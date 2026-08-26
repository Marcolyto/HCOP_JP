package ar.com.hexium.hcop.catalog.application.service;

/**
 * Error funcional compartido por los sub-catálogos del módulo, independiente de HTTP.
 */
public final class CatalogFailure extends RuntimeException {
  private final Type type;

  public CatalogFailure(Type type, String message) {
    super(message);
    this.type = type;
  }

  public Type type() {
    return type;
  }

  public enum Type {
    INVALID,
    NOT_FOUND
  }
}
