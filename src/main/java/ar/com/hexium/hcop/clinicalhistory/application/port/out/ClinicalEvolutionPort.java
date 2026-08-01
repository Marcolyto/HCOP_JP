package ar.com.hexium.hcop.clinicalhistory.application.port.out;

import java.time.Instant;
import java.util.OptionalLong;

public interface ClinicalEvolutionPort {
  OptionalLong append(long patientId, String evolutionId, String immutableEvolutionJson, long actorId, Instant occurredAt);
}
