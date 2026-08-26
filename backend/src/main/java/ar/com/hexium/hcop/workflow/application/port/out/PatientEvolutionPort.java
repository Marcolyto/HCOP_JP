package ar.com.hexium.hcop.workflow.application.port.out;

import ar.com.hexium.hcop.workflow.domain.EvolutionDraft;

/**
 * Anexa una evolución inmutable a la historia clínica del paciente — cruce a {@code patient},
 * dirección permitida por el orden canónico de F3.3.0 (no rompe ningún ciclo). {@code evolution}
 * en el resultado es el mismo árbol JSON opaco (con {@code immutable:true} ya agregado) que
 * también recibe {@code TreatmentWorkflowStore.insertEvent} para el registro de auditoría.
 */
public interface PatientEvolutionPort {

  AppendedEvolution append(
      long patientId, EvolutionDraft draft, long actorId, String actorDisplayName);

  record AppendedEvolution(Object evolution, long revision) {
  }
}
