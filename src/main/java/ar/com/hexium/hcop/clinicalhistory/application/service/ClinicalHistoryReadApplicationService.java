package ar.com.hexium.hcop.clinicalhistory.application.service;

import ar.com.hexium.hcop.clinicalhistory.application.port.in.ClinicalHistoryReadUseCase;
import ar.com.hexium.hcop.clinicalhistory.application.port.out.ClinicalHistoryReadPort;

public final class ClinicalHistoryReadApplicationService implements ClinicalHistoryReadUseCase {
  private final ClinicalHistoryReadPort store;

  public ClinicalHistoryReadApplicationService(ClinicalHistoryReadPort store) {
    this.store = store;
  }

  @Override
  public HistorySnapshot require(long patientId) {
    if (patientId < 1) throw new ClinicalHistoryReadFailure("Paciente inválido.");
    return store.find(patientId)
        .orElseThrow(() -> new ClinicalHistoryReadFailure("La historia clínica no está disponible."));
  }

  public static final class ClinicalHistoryReadFailure extends RuntimeException {
    public ClinicalHistoryReadFailure(String message) {
      super(message);
    }
  }
}
