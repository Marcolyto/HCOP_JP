package ar.com.hexium.hcop.sharedkernel.domain;

import java.util.Objects;

/**
 * Identidad clínica de una aplicación: paciente, tratamiento, ciclo y día.
 */
public record ApplicationKey(
    PatientId patientId,
    TreatmentId treatmentId,
    int cycleNumber,
    int applicationDay) {

  public ApplicationKey {
    Objects.requireNonNull(patientId, "patientId");
    Objects.requireNonNull(treatmentId, "treatmentId");
    if (cycleNumber < 1 || cycleNumber > 500) {
      throw new IllegalArgumentException("El ciclo debe estar entre 1 y 500.");
    }
    if (applicationDay < 1 || applicationDay > 366) {
      throw new IllegalArgumentException("El día de aplicación debe estar entre 1 y 366.");
    }
  }
}
