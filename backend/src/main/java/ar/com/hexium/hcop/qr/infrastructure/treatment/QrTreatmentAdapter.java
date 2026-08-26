package ar.com.hexium.hcop.qr.infrastructure.treatment;

import ar.com.hexium.hcop.qr.application.port.out.QrTreatmentPort;
import ar.com.hexium.hcop.qr.domain.QrTreatmentView;
import ar.com.hexium.hcop.treatment.TreatmentRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class QrTreatmentAdapter implements QrTreatmentPort {
  private final TreatmentRepository treatments;

  public QrTreatmentAdapter(TreatmentRepository treatments) {
    this.treatments = treatments;
  }

  @Override
  public Optional<QrTreatmentView> findTreatment(long patientId, String treatmentId) {
    return treatments.find(patientId, treatmentId)
        .map(treatment -> new QrTreatmentView(
            treatment.id(), treatment.schemeName(), treatment.diagnosis()));
  }
}
