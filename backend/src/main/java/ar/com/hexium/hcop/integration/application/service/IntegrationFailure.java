package ar.com.hexium.hcop.integration.application.service;

/**
 * Error funcional del módulo, independiente de HTTP. Los errores de transporte/protocolo del LLM
 * (deshabilitado, timeout, upstream, JSON estructurado inválido) los traduce el adapter HTTP a
 * {@code UNAVAILABLE}/{@code TIMEOUT}/{@code UPSTREAM_ERROR} (F3.4) — no son reglas de negocio de
 * este módulo, pero tampoco HTTP.
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
    NOT_FOUND,
    UNAVAILABLE,
    UPSTREAM_ERROR,
    TIMEOUT
  }
}
