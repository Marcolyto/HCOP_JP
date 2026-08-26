package ar.com.hexium.hcop.qr.application.port.out;

import ar.com.hexium.hcop.qr.domain.EvolutionDraft;

/** Anexa una evolución inmutable a la historia clínica — cruce a {@code patient}, dirección
 * permitida por el orden canónico de F3.3.0. */
public interface PatientEvolutionPort {

  AppendedEvolution append(long patientId, EvolutionDraft draft, long actorId, String actorDisplayName);

  record AppendedEvolution(Object evolution, long revision) {
  }
}
