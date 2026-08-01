package ar.com.hexium.hcop.clinicalhistory.application.service;

import ar.com.hexium.hcop.clinicalhistory.application.port.in.ClinicalHistorySaveUseCase;
import ar.com.hexium.hcop.clinicalhistory.application.port.out.ClinicalHistorySavePort;

/** Regla de persistencia optimista de una historia clínica. */
public final class ClinicalHistorySaveApplicationService implements ClinicalHistorySaveUseCase {
  private final ClinicalHistorySavePort store;

  public ClinicalHistorySaveApplicationService(ClinicalHistorySavePort store) {
    this.store = store;
  }

  @Override
  public SavedHistory save(SaveCommand command) {
    validatePatient(command);
    var saved = store.update(
        command.patientId(), command.documentJson(), command.expectedRevision(), command.actorId())
        .orElseThrow(() -> new ClinicalHistorySaveFailure(
            "La historia fue modificada en otra ventana."));
    return new SavedHistory(saved.documentJson(), saved.revision());
  }

  private void validatePatient(SaveCommand command) {
    String documentPatient = command.documentPatientId();
    String identityPatient = command.identityPatientId();
    String expected = Long.toString(command.patientId());
    if ((!documentPatient.isBlank() && !documentPatient.equals(expected))
        || (!identityPatient.isBlank() && !identityPatient.equals(expected))) {
      throw new ClinicalHistorySaveFailure("La historia pertenece a otro paciente.");
    }
  }

  public static final class ClinicalHistorySaveFailure extends RuntimeException {
    public ClinicalHistorySaveFailure(String message) {
      super(message);
    }
  }
}
