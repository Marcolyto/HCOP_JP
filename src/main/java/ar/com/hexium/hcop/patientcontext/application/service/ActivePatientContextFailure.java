package ar.com.hexium.hcop.patientcontext.application.service;

/** Error de aplicación traducido por el adaptador web al contrato HTTP vigente. */
public final class ActivePatientContextFailure extends RuntimeException {
  public enum Reason { PATIENT_NOT_FOUND, INVALID_SESSION }

  private final Reason reason;

  public ActivePatientContextFailure(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
