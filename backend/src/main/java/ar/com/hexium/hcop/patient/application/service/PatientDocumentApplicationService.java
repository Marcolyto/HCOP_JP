package ar.com.hexium.hcop.patient.application.service;

import ar.com.hexium.hcop.patient.application.port.in.PatientDocumentUseCase;
import ar.com.hexium.hcop.patient.application.port.out.PatientDocumentStore;
import ar.com.hexium.hcop.patient.domain.EvolutionAppend;
import ar.com.hexium.hcop.patient.domain.StoredDocument;

/** Sin lógica propia — toda la manipulación del JSON vive en {@link PatientDocumentStore}. */
public final class PatientDocumentApplicationService implements PatientDocumentUseCase {
  private final PatientDocumentStore documents;

  public PatientDocumentApplicationService(PatientDocumentStore documents) {
    this.documents = documents;
  }

  @Override
  public StoredDocument require(long patientId) {
    return documents.find(patientId)
        .orElseThrow(() -> new PatientFailure(
            PatientFailure.Type.NOT_FOUND, "La historia clínica no está disponible."));
  }

  @Override
  public Object blankTemplate() {
    return documents.blankTemplate();
  }

  @Override
  public StoredDocument save(long patientId, Object document, long expectedRevision, long actorId) {
    return documents.save(patientId, document, expectedRevision, actorId);
  }

  @Override
  public Object state(StoredDocument stored) {
    return documents.stateOf(stored);
  }

  @Override
  public EvolutionAppend appendImmutableEvolution(long patientId, Object evolution, long actorId) {
    return documents.appendImmutableEvolution(patientId, evolution, actorId);
  }
}
