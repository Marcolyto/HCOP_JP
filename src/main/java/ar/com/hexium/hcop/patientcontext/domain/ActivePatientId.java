package ar.com.hexium.hcop.patientcontext.domain;

/** Identificador estable de un paciente abierto en la sesión actual. */
public record ActivePatientId(long value) {
  public ActivePatientId {
    if (value <= 0) {
      throw new IllegalArgumentException("El identificador del paciente debe ser positivo.");
    }
  }
}
