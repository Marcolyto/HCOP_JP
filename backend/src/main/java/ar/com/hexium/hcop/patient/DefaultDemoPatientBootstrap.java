package ar.com.hexium.hcop.patient;

import ar.com.hexium.hcop.patient.PatientDocumentRepository.StoredDocument;
import ar.com.hexium.hcop.patient.PatientRepository.NewPatient;
import ar.com.hexium.hcop.patient.PatientRepository.Patient;
import java.io.IOException;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class DefaultDemoPatientBootstrap {
  static final String SEED_KEY = "hcop-default-test-savatierra-v1";
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDemoPatientBootstrap.class);

  private static final NewPatient IDENTITY = new NewPatient(
      "Tomas Alejandro",
      "Test Savatierra",
      "99000002",
      "DEMO-SAVATIERRA-99000002",
      LocalDate.of(1970, 2, 14),
      "Masculino",
      "COBERTURA DEMO",
      "DEMO-9900000200",
      "2604000002",
      "tomas.savatierra@example.invalid",
      "Domicilio de prueba 100");

  private final PatientRepository patients;
  private final PatientDocumentRepository documents;
  private final PatientDocumentService documentService;
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final boolean enabled;
  private final Resource clinicalDocumentResource;

  public DefaultDemoPatientBootstrap(
      PatientRepository patients,
      PatientDocumentRepository documents,
      PatientDocumentService documentService,
      JdbcTemplate jdbc,
      ObjectMapper mapper,
      @Value("${HCOP_SEED_EXAMPLE_PATIENT:true}") boolean enabled,
      @Value("classpath:bootstrap/patients/test-savatierra-v3.json") Resource clinicalDocumentResource) {
    this.patients = patients;
    this.documents = documents;
    this.documentService = documentService;
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.enabled = enabled;
    this.clinicalDocumentResource = clinicalDocumentResource;
  }

  @Transactional
  public void seed() {
    if (!enabled) return;

    Patient patient = patients.findBySeedKey(SEED_KEY).orElse(null);
    if (patient != null) {
      ensureDocument(patient, null);
      return;
    }

    if (hasForeignNaturalKeyCollision()) return;
    Long actorId = bootstrapActorOrWarn("crear el paciente demostrativo");
    if (actorId == null) return;

    patient = patients.insertSeedIfMissing(IDENTITY, SEED_KEY)
        .orElseGet(this::recoverConcurrentSeed);
    if (patient != null) ensureDocument(patient, actorId);
  }

  private Patient recoverConcurrentSeed() {
    Patient concurrent = patients.findBySeedKey(SEED_KEY).orElse(null);
    if (concurrent != null) return concurrent;
    if (!hasForeignNaturalKeyCollision()) {
      LOGGER.warn(
          "Se omitió el paciente demostrativo {} porque no pudo crearse ni recuperarse.",
          SEED_KEY);
    }
    return null;
  }

  private boolean hasForeignNaturalKeyCollision() {
    Patient collision = patients.findDuplicate(IDENTITY.dni(), IDENTITY.medicalRecord())
        .orElse(null);
    if (collision == null) return false;
    LOGGER.warn(
        "Se omitió el paciente demostrativo {}: el DNI o la historia clínica reservados "
            + "ya pertenecen al paciente {} sin ese marcador.",
        SEED_KEY,
        collision.id());
    return true;
  }

  private void ensureDocument(Patient patient, Long knownActorId) {
    StoredDocument stored = documents.find(patient.id()).orElse(null);
    if (stored != null) {
      updateManagedDocumentIfPristine(patient, stored, knownActorId);
      return;
    }

    Long actorId = knownActorId == null
        ? bootstrapActorOrWarn("crear la historia demostrativa")
        : knownActorId;
    if (actorId == null) return;

    ObjectNode document = loadClinicalDocument();
    prepareManagedDocument(document, patient, 1);

    if (documents.insertIfMissing(patient.id(), document, actorId, false).isPresent()) return;

    StoredDocument concurrent = documents.find(patient.id()).orElse(null);
    if (concurrent == null) {
      LOGGER.warn(
          "Se omitió la historia del paciente demostrativo {} porque no pudo crearse ni recuperarse.",
          SEED_KEY);
      return;
    }
    updateManagedDocumentIfPristine(patient, concurrent, actorId);
  }

  private void updateManagedDocumentIfPristine(
      Patient patient,
      StoredDocument stored,
      Long knownActorId) {
    JsonNode storedMeta = stored.document().path("meta");
    if (!SEED_KEY.equals(storedMeta.path("demoSeedKey").asText(""))) return;

    long managedRevision = storedMeta.has("demoManagedRevision")
        ? storedMeta.path("demoManagedRevision").asLong(-1)
        : legacyManagedRevision(stored);
    if (managedRevision != stored.revision()) return;

    ObjectNode replacement = loadClinicalDocument();
    long resourceVersion = requireResourceContentVersion(replacement);
    long storedVersion = storedMeta.path("demoContentVersion").asLong(1);
    if (resourceVersion <= storedVersion) return;

    Long actorId = knownActorId == null
        ? bootstrapActorOrWarn("actualizar la historia demostrativa")
        : knownActorId;
    if (actorId == null) return;

    long nextRevision = Math.addExact(stored.revision(), 1);
    prepareManagedDocument(replacement, patient, nextRevision);
    if (documents.update(patient.id(), replacement, stored.revision(), actorId).isPresent()) return;
    acceptConcurrentDocumentChange(patient.id(), resourceVersion);
  }

  private void acceptConcurrentDocumentChange(long patientId, long targetContentVersion) {
    StoredDocument current = documents.find(patientId).orElse(null);
    if (current == null) {
      LOGGER.warn(
          "Se omitió la actualización del paciente demostrativo {}: la historia ya no existe.",
          SEED_KEY);
      return;
    }

    JsonNode meta = current.document().path("meta");
    if (!SEED_KEY.equals(meta.path("demoSeedKey").asText(""))) return;
    if (meta.path("demoContentVersion").asLong(1) >= targetContentVersion) return;

    long managedRevision = meta.has("demoManagedRevision")
        ? meta.path("demoManagedRevision").asLong(-1)
        : legacyManagedRevision(current);
    if (managedRevision != current.revision()) return;

    LOGGER.warn(
        "Se omitió la actualización del paciente demostrativo {} tras un conflicto concurrente "
            + "que no pudo clasificarse como actualización aplicada ni edición humana.",
        SEED_KEY);
  }

  private long legacyManagedRevision(StoredDocument stored) {
    // The first bootstrap release wrote the marker at revision 1 but did not
    // persist demoManagedRevision. Revision 1 is therefore the only legacy
    // state that can be proven untouched by a later human save.
    return stored.revision() == 1 ? 1 : -1;
  }

  private void prepareManagedDocument(
      ObjectNode document,
      Patient patient,
      long managedRevision) {
    requireResourceContentVersion(document);
    ObjectNode meta = document.withObject("/meta");
    documentService.applyPatient(document, patient, managedRevision);
    meta = document.withObject("/meta");
    meta.put("demo", true)
        .put("demoSeedKey", SEED_KEY)
        .put("demoManagedRevision", managedRevision);
  }

  private long requireResourceContentVersion(ObjectNode document) {
    ObjectNode meta = document.withObject("/meta");
    String resourceSeedKey = meta.path("demoSeedKey").asText("");
    if (!SEED_KEY.equals(resourceSeedKey)) {
      throw new IllegalStateException(
          "El recurso clínico demostrativo no tiene el marcador esperado " + SEED_KEY + ".");
    }
    long version = meta.path("demoContentVersion").asLong(0);
    if (version < 1) {
      throw new IllegalStateException(
          "El recurso clínico demostrativo no declara un demoContentVersion válido.");
    }
    return version;
  }

  private Long bootstrapActorOrWarn(String action) {
    Long actorId = jdbc.queryForObject(
        "SELECT min(id) FROM local_users WHERE enabled = true",
        Long.class);
    if (actorId == null) {
      LOGGER.warn(
          "Se omitió {} porque no hay un usuario habilitado para auditar la operación.",
          action);
    }
    return actorId;
  }

  private ObjectNode loadClinicalDocument() {
    try (var input = clinicalDocumentResource.getInputStream()) {
      JsonNode value = mapper.readTree(input);
      if (value instanceof ObjectNode object) return object.deepCopy();
      throw new IllegalStateException(
          "El recurso clínico del paciente demostrativo debe contener un objeto JSON.");
    } catch (IOException exception) {
      throw new IllegalStateException(
          "No se pudo leer el recurso clínico bootstrap/patients/test-savatierra-v3.json.",
          exception);
    }
  }
}
