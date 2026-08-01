package ar.com.hexium.hcop.clinicalhistory.application.port.in;

/** Agrega una evolución inmutable manteniendo la revisión de la historia. */
public interface ClinicalEvolutionUseCase {
  AppendResult append(AppendCommand command);

  record AppendCommand(long patientId, String evolutionId, String immutableEvolutionJson, long actorId) {
  }

  record AppendResult(long revision) {
  }
}
