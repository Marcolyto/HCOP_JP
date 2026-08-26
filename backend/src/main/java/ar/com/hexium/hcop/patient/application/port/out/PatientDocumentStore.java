package ar.com.hexium.hcop.patient.application.port.out;

import ar.com.hexium.hcop.patient.domain.EvolutionAppend;
import ar.com.hexium.hcop.patient.domain.Patient;
import ar.com.hexium.hcop.patient.domain.StoredDocument;
import java.util.Optional;

/**
 * Absorbe toda la manipulación del árbol JSON de la historia (plantilla en blanco, estampado de
 * identidad del paciente, validación de pertenencia, versionado inmutable de evoluciones) — el
 * único lugar del módulo que toca Jackson de verdad. La aplicación es un pasamano sin lógica
 * propia (mismo criterio que {@code tools.CalculatorCatalogApplicationService}); la
 * implementación puede lanzar {@code PatientFailure} directo (infraestructura → aplicación es la
 * dirección permitida).
 */
public interface PatientDocumentStore {

  Optional<StoredDocument> find(long patientId);

  StoredDocument createBlank(Patient patient, long actorId);

  Object blankTemplate();

  /** Valida pertenencia al paciente y persiste — lanza {@code PatientFailure.CONFLICT} si la
   * historia pertenece a otro paciente o si la revisión ya cambió. */
  StoredDocument save(long patientId, Object document, long expectedRevision, long actorId);

  Object stateOf(StoredDocument stored);

  EvolutionAppend appendImmutableEvolution(long patientId, Object evolution, long actorId);
}
