package ar.com.hexium.hcop.auth.application.service;

/** Error de aplicación traducido por el adaptador web al contrato HTTP vigente. */
public final class AuthenticationFailure extends RuntimeException {
  public enum Reason {
    INVALID_CREDENTIALS,
    INVALID_NEW_PASSWORD,
    INVALID_CURRENT_PASSWORD,
    INVALID_BOOTSTRAP_CONFIGURATION
  }

  private final Reason reason;

  public AuthenticationFailure(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() { return reason; }
}
