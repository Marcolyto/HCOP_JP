package ar.com.hexium.hcop.clinicalhistory.application.port.out;

import java.util.Optional;

public interface ClinicalHistorySavePort {
  Optional<SavedDocument> update(long patientId, String documentJson, long expectedRevision, long actorId);

  record SavedDocument(String documentJson, long revision) {
  }
}
