package ar.com.hexium.hcop.infusion.infrastructure.configuration;

import ar.com.hexium.hcop.infusion.application.port.in.TreatmentApplicationLogisticsUseCase;
import ar.com.hexium.hcop.infusion.application.port.out.TreatmentApplicationLogisticsStore;
import ar.com.hexium.hcop.infusion.application.service.TreatmentApplicationLogisticsApplicationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Aplica los límites transaccionales sin contaminar la capa de aplicación con Spring. */
@Service
public class TransactionalTreatmentApplicationLogisticsManagement
    implements TreatmentApplicationLogisticsUseCase {
  private final TreatmentApplicationLogisticsApplicationService delegate;

  public TransactionalTreatmentApplicationLogisticsManagement(TreatmentApplicationLogisticsStore store) {
    this.delegate = new TreatmentApplicationLogisticsApplicationService(store);
  }

  @Override
  @Transactional
  public void synchronizeExistingTreatments() {
    delegate.synchronizeExistingTreatments();
  }

  @Override
  @Transactional
  public void synchronizeTreatment(String treatmentId) {
    delegate.synchronizeTreatment(treatmentId);
  }
}
