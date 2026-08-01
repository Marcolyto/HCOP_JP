package ar.com.hexium.hcop.clinicalhistory.application.port.out;

import ar.com.hexium.hcop.clinicalhistory.application.port.in.ClinicalHistoryReadUseCase.HistorySnapshot;
import java.util.Optional;

public interface ClinicalHistoryReadPort {
  Optional<HistorySnapshot> find(long patientId);
}
