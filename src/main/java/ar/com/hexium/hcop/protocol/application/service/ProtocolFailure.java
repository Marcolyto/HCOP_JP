package ar.com.hexium.hcop.protocol.application.service;

public final class ProtocolFailure extends RuntimeException {
  public enum Type {
    INVALID,
    NOT_FOUND,
    CONFLICT
  }

  private final Type type;
  private final String code;

  public ProtocolFailure(Type type, String message, String code) {
    super(message);
    this.type = type;
    this.code = code;
  }

  public ProtocolFailure(Type type, String message) {
    this(type, message, switch (type) {
      case INVALID -> "INVALID_PROTOCOL";
      case NOT_FOUND -> "PROTOCOL_NOT_FOUND";
      case CONFLICT -> "PROTOCOL_CONFLICT";
    });
  }

  public Type type() {
    return type;
  }

  public String code() {
    return code;
  }
}
