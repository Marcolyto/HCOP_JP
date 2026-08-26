package ar.com.hexium.hcop.integration.application.service;

/**
 * Error funcional del módulo, independiente de HTTP. Los errores de transporte/protocolo del LLM
 * (deshabilitado, timeout, upstream, JSON estructurado inválido) los sigue lanzando el adapter
 * como {@code ApiException} directo — igual que hoy, no son reglas de negocio de este módulo.
 */
public final class IntegrationFailure extends RuntimeException {
  private final Type type;
  private final String code;

  public IntegrationFailure(Type type, String message) {
    this(type, message, "");
  }

  public IntegrationFailure(Type type, String message, String code) {
    super(message);
    this.type = type;
    this.code = code == null ? "" : code;
  }

  public Type type() {
    return type;
  }

  public String code() {
    return code;
  }

  public enum Type {
    INVALID,
    NOT_FOUND
  }
}
