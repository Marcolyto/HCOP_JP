package ar.com.hexium.hcop.media.application.service;

/** Error funcional del módulo, independiente de HTTP. */
public final class MediaFailure extends RuntimeException {
  private final Type type;
  private final String code;

  public MediaFailure(Type type, String message) {
    this(type, message, "");
  }

  public MediaFailure(Type type, String message, String code) {
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
    CONFLICT,
    UNSUPPORTED_FORMAT,
    FORBIDDEN,
    TOO_LARGE,
    INTERNAL
  }
}
