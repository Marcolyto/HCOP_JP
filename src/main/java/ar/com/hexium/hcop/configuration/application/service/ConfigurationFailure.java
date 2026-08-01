package ar.com.hexium.hcop.configuration.application.service;

/**
 * Error funcional del módulo, independiente de HTTP.
 */
public final class ConfigurationFailure extends RuntimeException {
  private final Type type;
  private final String code;

  public ConfigurationFailure(Type type, String message) {
    this(type, message, "");
  }

  public ConfigurationFailure(Type type, String message, String code) {
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
    CONFLICT
  }
}
