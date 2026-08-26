package ar.com.hexium.hcop.treatment.infrastructure.patient;

import ar.com.hexium.hcop.patient.application.port.out.TreatmentSummaryPort;
import ar.com.hexium.hcop.treatment.TreatmentService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** F3.3.0: único lugar que implementa el puerto cruzado {@code patient} → {@code treatment}. */
@Component
public class TreatmentSummaryAdapter implements TreatmentSummaryPort {
  private final TreatmentService treatments;

  public TreatmentSummaryAdapter(TreatmentService treatments) {
    this.treatments = treatments;
  }

  @Override
  public List<Map<String, Object>> list(long patientId) {
    return treatments.list(patientId);
  }
}
