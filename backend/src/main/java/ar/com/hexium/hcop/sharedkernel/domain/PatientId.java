package ar.com.hexium.hcop.sharedkernel.domain;

/**
 * Identificador local e inmutable de un paciente.
 */
public record PatientId(long value) {

  public PatientId {
    if (value < 1) throw new IllegalArgumentException("El identificador de paciente debe ser positivo.");
  }

  public static PatientId of(long value) {
    return new PatientId(value);
  }
}
