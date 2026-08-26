package ar.com.hexium.hcop.patient.infrastructure.persistence;

import ar.com.hexium.hcop.platform.HcopProperties;
import ar.com.hexium.hcop.patient.application.port.out.PatientDocumentStore;
import ar.com.hexium.hcop.patient.application.service.PatientFailure;
import ar.com.hexium.hcop.patient.domain.EvolutionAppend;
import ar.com.hexium.hcop.patient.domain.Patient;
import ar.com.hexium.hcop.patient.domain.StoredDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Toda la manipulación del árbol JSON de la historia — mismo lugar que ocupaba
 * {@code PatientDocumentService} antes de F3.3, ahora implementando el puerto de aplicación.
 * Delega la persistencia cruda a {@link PostgresPatientDocumentRepository}.
 */
@Component
public class PatientDocumentStoreAdapter implements PatientDocumentStore {
  private final PostgresPatientDocumentRepository documents;
  private final ObjectMapper mapper;
  private final HcopProperties properties;
  private final Clock clock;

  public PatientDocumentStoreAdapter(
      PostgresPatientDocumentRepository documents, ObjectMapper mapper, HcopProperties properties,
      Clock clock) {
    this.documents = documents;
    this.mapper = mapper;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public java.util.Optional<StoredDocument> find(long patientId) {
    return documents.find(patientId).map(this::toDomain);
  }

  @Override
  @Transactional
  public StoredDocument createBlank(Patient patient, long actorId) {
    ObjectNode document = (ObjectNode) blankTemplate();
    applyPatient(document, patient, 1);
    return toDomain(documents.insert(patient.id(), document, actorId, false));
  }

  @Override
  public Object blankTemplate() {
    try {
      var file = properties.catalogRoot().resolve("hc-oncologica-vacia.json").normalize();
      if (Files.isRegularFile(file)) return mapper.readTree(Files.readString(file));
    } catch (IOException ignored) {
      // The deterministic in-code template below keeps startup safe if the optional file is absent.
    }
    ObjectNode document = mapper.createObjectNode();
    document.set("meta", mapper.createObjectNode());
    document.set("patient", mapper.createObjectNode());
    document.set("oncology", mapper.createObjectNode());
    document.set("exam", mapper.createObjectNode());
    document.set("evolutions", mapper.createArrayNode());
    document.set("studies", mapper.createArrayNode());
    document.set("prescriptions", mapper.createArrayNode());
    document.set("researchRecords", mapper.createArrayNode());
    return document;
  }

  @Override
  @Transactional
  public StoredDocument save(long patientId, Object document, long expectedRevision, long actorId) {
    JsonNode incoming = (JsonNode) document;
    validatePatient(incoming, patientId);
    PostgresPatientDocumentRepository.StoredDocument saved = documents
        .update(patientId, incoming, expectedRevision, actorId)
        .orElseThrow(() -> new PatientFailure(
            PatientFailure.Type.CONFLICT,
            "La historia fue modificada en otra ventana.",
            "VERSION_CONFLICT"));
    applyRevision(saved.document(), saved.revision());
    return toDomain(saved);
  }

  @Override
  public Object stateOf(StoredDocument stored) {
    JsonNode copy = ((JsonNode) stored.document()).deepCopy();
    applyRevision(copy, stored.revision());
    return copy;
  }

  @Override
  @Transactional
  public EvolutionAppend appendImmutableEvolution(long patientId, Object evolution, long actorId) {
    ObjectNode evolutionNode = (ObjectNode) evolution;
    PostgresPatientDocumentRepository.StoredDocument stored = documents.lock(patientId)
        .orElseThrow(() -> new PatientFailure(
            PatientFailure.Type.NOT_FOUND, "La historia clínica no está disponible."));
    ObjectNode document = (ObjectNode) stored.document().deepCopy();
    ArrayNode evolutions = document.withArray("evolutions");
    String evolutionId = evolutionNode.path("id").asText("");
    for (int index = evolutions.size() - 1; index >= 0; index--) {
      if (!evolutionId.isBlank() && evolutionId.equals(evolutions.get(index).path("id").asText(""))) {
        evolutions.remove(index);
      }
    }
    evolutionNode.put("immutable", true);
    evolutions.insert(0, evolutionNode);
    document.withObject("/meta").put("updatedAt", clock.instant().toString());
    PostgresPatientDocumentRepository.StoredDocument saved = documents
        .update(patientId, document, stored.revision(), actorId)
        .orElseThrow(() -> new PatientFailure(
            PatientFailure.Type.CONFLICT,
            "La historia fue modificada en otra ventana.",
            "VERSION_CONFLICT"));
    return new EvolutionAppend(evolutionNode.deepCopy(), saved.revision());
  }

  /** También usado por {@code infrastructure.bootstrap.DefaultDemoPatientBootstrap} (infra-a-infra). */
  public void applyPatient(Object value, Patient patient, long revision) {
    if (!(value instanceof ObjectNode root)) return;
    ObjectNode patientNode = root.withObject("/patient");
    patientNode.put("fullName", patient.fullName());
    patientNode.put("dni", patient.dni());
    patientNode.put("medicalRecord", patient.medicalRecord());
    patientNode.put("birthDate", patient.birthDate() == null ? "" : patient.birthDate().toString());
    patientNode.put("sex", patient.sex());
    patientNode.put("insurance", patient.insurance());
    patientNode.put("affiliateNumber", patient.affiliateNumber());
    patientNode.put("phone", patient.phone());
    patientNode.put("email", patient.email());
    patientNode.put("address", patient.address());
    patientNode.put("liraId", Long.toString(patient.id()));
    ArrayNode coverages = mapper.createArrayNode();
    if (!patient.insurance().isBlank() || !patient.affiliateNumber().isBlank()) {
      ObjectNode coverage = coverages.addObject();
      coverage.put("id", "local:primary");
      coverage.put("name", patient.insurance());
      coverage.put("affiliateNumber", patient.affiliateNumber());
      coverage.put("primary", true);
    }
    patientNode.set("coverages", coverages);

    ObjectNode meta = root.withObject("/meta");
    meta.put("version", Math.max(1, meta.path("version").asInt(1)));
    meta.put("updatedAt", clock.instant().toString());
    if (!meta.hasNonNull("createdAt")) meta.put("createdAt", clock.instant().toString());
    ObjectNode imported = meta.withObject("/liraImport");
    imported.put("system", "HCOP JP");
    imported.put("origin", patient.localOnly() ? "local" : "migration");
    imported.put("patientId", Long.toString(patient.id()));
    imported.put("importedAt", clock.instant().toString());
    meta.put("persistenceRevision", revision);
  }

  private void validatePatient(JsonNode document, long patientId) {
    String documentPatient = document.path("meta").path("liraImport").path("patientId").asText("");
    String identityPatient = document.path("patient").path("liraId").asText("");
    if ((!documentPatient.isBlank() && !documentPatient.equals(Long.toString(patientId)))
        || (!identityPatient.isBlank() && !identityPatient.equals(Long.toString(patientId)))) {
      throw new PatientFailure(
          PatientFailure.Type.CONFLICT,
          "La historia pertenece a otro paciente.",
          "CLINICAL_PATIENT_MISMATCH");
    }
  }

  private void applyRevision(JsonNode value, long revision) {
    if (value instanceof ObjectNode root) {
      root.withObject("/meta").put("persistenceRevision", revision);
    }
  }

  private StoredDocument toDomain(PostgresPatientDocumentRepository.StoredDocument stored) {
    return new StoredDocument(
        stored.patientId(), stored.document(), stored.revision(), stored.importedAt(),
        stored.createdAt(), stored.updatedAt());
  }
}
