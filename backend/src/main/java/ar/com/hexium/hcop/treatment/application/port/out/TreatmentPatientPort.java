package ar.com.hexium.hcop.treatment.application.port.out;

import ar.com.hexium.hcop.treatment.domain.TreatmentPatientView;

/** Cruza a {@code patient}, dirección permitida por el orden canónico (F3.3.0). */
public interface TreatmentPatientPort {
  TreatmentPatientView requirePatient(long patientId);
}
