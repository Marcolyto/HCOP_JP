package ar.com.hexium.hcop.clinicalhistory.application.port.in;

import java.time.Instant;

/** Recupera una historia clínica persistida sin depender de su formato JSON. */
public interface ClinicalHistoryReadUseCase {
  HistorySnapshot require(long patientId);

  record HistorySnapshot(
      long patientId,
      String documentJson,
      long revision,
      Instant importedAt,
      Instant createdAt,
      Instant updatedAt) {
  }
}
