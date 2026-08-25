package ar.com.hexium.hcop.patient;

import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.config.HcopProperties;
import ar.com.hexium.hcop.patient.PatientDocumentRepository.StoredDocument;
import ar.com.hexium.hcop.patient.PatientRepository.Patient;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class PatientDocumentService {
  private final PatientRepository patients;
  private final PatientDocumentRepository documents;
  private final ObjectMapper mapper;
  private final HcopProperties properties;
  private final Clock clock;

  public PatientDocumentService(
      PatientRepository patients,
      PatientDocumentRepository documents,
      ObjectMapper mapper,
      HcopProperties properties,
      Clock clock) {
    this.patients = patients;
    this.documents = documents;
    this.mapper = mapper;
    this.properties = properties;
    this.clock = clock;
  }

  @Transactional
  public StoredDocument createBlank(Patient patient, long actorId) {
    JsonNode document = blankTemplate();
    applyPatient(document, patient, 1);
    return documents.insert(patient.id(), document, actorId, false);
  }

  public JsonNode blankTemplate() {
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

  public StoredDocument require(long patientId) {
    return documents.find(patientId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La historia clínica no está disponible."));
  }

  @Transactional
  public StoredDocument save(long patientId, JsonNode document, long expectedRevision, long actorId) {
    validatePatient(document, patientId);
    StoredDocument saved = documents.update(patientId, document, expectedRevision, actorId)
        .orElseThrow(() -> new ApiException(
            HttpStatus.CONFLICT,
            "La historia fue modificada en otra ventana.",
            "VERSION_CONFLICT"));
    applyRevision(saved.document(), saved.revision());
    return saved;
  }

  public JsonNode state(StoredDocument stored) {
    JsonNode copy = stored.document().deepCopy();
    applyRevision(copy, stored.revision());
    return copy;
  }

  @Transactional
  public EvolutionAppend appendImmutableEvolution(long patientId, ObjectNode evolution, long actorId) {
    StoredDocument stored = documents.lock(patientId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La historia clínica no está disponible."));
    ObjectNode document = (ObjectNode) stored.document().deepCopy();
    ArrayNode evolutions = document.withArray("evolutions");
    String evolutionId = evolution.path("id").asText("");
    for (int index = evolutions.size() - 1; index >= 0; index--) {
      if (!evolutionId.isBlank() && evolutionId.equals(evolutions.get(index).path("id").asText(""))) {
        evolutions.remove(index);
      }
    }
    evolution.put("immutable", true);
    evolutions.insert(0, evolution);
    document.withObject("/meta").put("updatedAt", clock.instant().toString());
    StoredDocument saved = documents.update(patientId, document, stored.revision(), actorId)
        .orElseThrow(() -> new ApiException(
            HttpStatus.CONFLICT,
            "La historia fue modificada en otra ventana.",
            "VERSION_CONFLICT"));
    return new EvolutionAppend(evolution.deepCopy(), saved.revision());
  }

  public void applyPatient(JsonNode value, Patient patient, long revision) {
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
      throw new ApiException(
          HttpStatus.CONFLICT,
          "La historia pertenece a otro paciente.",
          "CLINICAL_PATIENT_MISMATCH");
    }
  }

  private void applyRevision(JsonNode value, long revision) {
    if (value instanceof ObjectNode root) {
      root.withObject("/meta").put("persistenceRevision", revision);
    }
  }

  public record EvolutionAppend(ObjectNode evolution, long revision) {
  }
}
