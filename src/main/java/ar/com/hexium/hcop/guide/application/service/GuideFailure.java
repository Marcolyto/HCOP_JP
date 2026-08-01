package ar.com.hexium.hcop.guide.application.service;

public final class GuideFailure extends RuntimeException {
  public enum Type {
    INVALID,
    NOT_FOUND,
    TOO_LARGE,
    STORAGE
  }

  private final Type type;
  private final String code;

  public GuideFailure(Type type, String message, String code) {
    super(message);
    this.type = type;
    this.code = code;
  }

  public GuideFailure(Type type, String message) {
    this(type, message, switch (type) {
      case INVALID -> "INVALID_GUIDE";
      case NOT_FOUND -> "GUIDE_NOT_FOUND";
      case TOO_LARGE -> "GUIDE_TOO_LARGE";
      case STORAGE -> "GUIDE_STORAGE_ERROR";
    });
  }

  public Type type() {
    return type;
  }

  public String code() {
    return code;
  }
}
