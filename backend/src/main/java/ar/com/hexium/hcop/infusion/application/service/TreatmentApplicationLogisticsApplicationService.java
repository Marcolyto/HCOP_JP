package ar.com.hexium.hcop.infusion.application.service;

import ar.com.hexium.hcop.infusion.application.port.in.TreatmentApplicationLogisticsUseCase;
import ar.com.hexium.hcop.infusion.application.port.out.TreatmentApplicationLogisticsStore;

/**
 * Sin lógica propia — mismo criterio que {@code tools.CalculatorCatalogApplicationService}: el
 * módulo existe igual como capa propia porque es el punto de extensión si la sincronización de
 * logística alguna vez necesita reglas que no son de persistencia (reintentos, notificaciones).
 */
public final class TreatmentApplicationLogisticsApplicationService
    implements TreatmentApplicationLogisticsUseCase {
  private final TreatmentApplicationLogisticsStore store;

  public TreatmentApplicationLogisticsApplicationService(TreatmentApplicationLogisticsStore store) {
    this.store = store;
  }

  @Override
  public void synchronizeExistingTreatments() {
    store.synchronizeExistingTreatments();
  }

  @Override
  public void synchronizeTreatment(String treatmentId) {
    store.synchronizeTreatment(treatmentId);
  }
}
