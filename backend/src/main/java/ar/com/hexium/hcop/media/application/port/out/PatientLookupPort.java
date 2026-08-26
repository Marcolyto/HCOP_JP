package ar.com.hexium.hcop.media.application.port.out;

/** Rompe la dependencia directa a {@code patient} (todavía no hexagonal) — patrón #7 del plan F3. */
public interface PatientLookupPort {

  /** Lanza si el paciente no existe — el mismo criterio que {@code PatientService.require}. */
  void requireExists(long patientId);
}
