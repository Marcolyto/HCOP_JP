package ar.com.hexium.hcop.qr.application.port.out;

import ar.com.hexium.hcop.qr.domain.QrTreatmentView;
import java.util.Optional;

/** Cruza a {@code treatment}, dirección permitida por el orden canónico (F3.3.0). */
public interface QrTreatmentPort {
  Optional<QrTreatmentView> findTreatment(long patientId, String treatmentId);
}
