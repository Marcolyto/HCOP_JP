package ar.com.hexium.hcop.patient.application.port.in;

import ar.com.hexium.hcop.patient.domain.EvolutionAppend;
import ar.com.hexium.hcop.patient.domain.StoredDocument;

/**
 * {@code Object document}/{@code Object evolution} son árboles JSON opacos (sigue siendo
 * {@code JsonNode}/{@code ObjectNode} real en runtime) — ni el puerto ni la aplicación tocan
 * Jackson, toda la navegación vive en {@code infrastructure.persistence.PostgresPatientDocumentStore}.
 */
public interface PatientDocumentUseCase {

  StoredDocument require(long patientId);

  Object blankTemplate();

  StoredDocument save(long patientId, Object document, long expectedRevision, long actorId);

  Object state(StoredDocument stored);

  EvolutionAppend appendImmutableEvolution(long patientId, Object evolution, long actorId);
}
