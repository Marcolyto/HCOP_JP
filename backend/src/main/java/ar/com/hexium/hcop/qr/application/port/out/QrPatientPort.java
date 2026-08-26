package ar.com.hexium.hcop.qr.application.port.out;

import ar.com.hexium.hcop.qr.domain.QrPatientView;

/**
 * Cruza a {@code patient}, dirección permitida por el orden canónico (F3.3.0) — no rompe ningún
 * ciclo. El 404 de paciente inexistente sigue viajando como {@code ApiException} sin traducir
 * desde el adapter, mismo criterio que {@code media.infrastructure.patient.PatientServiceLookupAdapter}.
 */
public interface QrPatientPort {
  QrPatientView requirePatient(long patientId);
}
