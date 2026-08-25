package ar.com.hexium.hcop.common;

import org.springframework.http.HttpStatus;

public final class ApiException extends RuntimeException {
  private final HttpStatus status;
  private final String code;

  public ApiException(HttpStatus status, String message) {
    this(status, message, "");
  }

  public ApiException(HttpStatus status, String message, String code) {
    super(message);
    this.status = status;
    this.code = code == null ? "" : code;
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }
}
