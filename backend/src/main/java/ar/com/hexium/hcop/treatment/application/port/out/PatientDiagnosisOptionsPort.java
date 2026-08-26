package ar.com.hexium.hcop.treatment.application.port.out;

import ar.com.hexium.hcop.treatment.domain.DiagnosisOption;
import java.util.List;

/**
 * Cruza a {@code patient}, dirección permitida por el orden canónico (F3.3.0). La proyección es
 * propia de {@code treatment} (más rica que la de {@code diagnosis.application.port.in.DiagnosisUseCase}
 * — agrega código CIE-10 y estadio al rótulo) así que no se reusa ese puerto de otro módulo.
 */
public interface PatientDiagnosisOptionsPort {
  List<DiagnosisOption> diagnosisOptions(long patientId);
}
