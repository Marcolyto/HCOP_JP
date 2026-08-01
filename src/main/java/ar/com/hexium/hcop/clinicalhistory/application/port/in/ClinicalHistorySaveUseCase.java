package ar.com.hexium.hcop.clinicalhistory.application.port.in;

/** Guardado optimista de la hoja clínica completa. */
public interface ClinicalHistorySaveUseCase {
  SavedHistory save(SaveCommand command);

  record SaveCommand(
      long patientId, String documentJson, String documentPatientId, String identityPatientId,
      long expectedRevision, long actorId) {
  }

  record SavedHistory(String documentJson, long revision) {
  }
}
