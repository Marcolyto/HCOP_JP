package ar.com.hexium.hcop.infusion.infrastructure.treatment;

import ar.com.hexium.hcop.infusion.application.port.in.TreatmentApplicationLogisticsUseCase;
import ar.com.hexium.hcop.treatment.application.port.out.TreatmentApplicationSyncPort;
import org.springframework.stereotype.Component;

/** F3.3.0: único lugar que implementa el puerto cruzado {@code treatment} → {@code infusion}. */
@Component
public class TreatmentApplicationSyncAdapter implements TreatmentApplicationSyncPort {
  private final TreatmentApplicationLogisticsUseCase applicationLogistics;

  public TreatmentApplicationSyncAdapter(
      TreatmentApplicationLogisticsUseCase applicationLogistics) {
    this.applicationLogistics = applicationLogistics;
  }

  @Override
  public void synchronize(String treatmentId) {
    applicationLogistics.synchronizeTreatment(treatmentId);
  }
}
