package ar.com.hexium.hcop.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.config.HcopProperties;
import ar.com.hexium.hcop.patient.PatientDocumentRepository.StoredDocument;
import ar.com.hexium.hcop.patient.PatientRepository.NewPatient;
import ar.com.hexium.hcop.patient.PatientRepository.Patient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class DefaultDemoPatientBootstrapTest {
  private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");
  private static final String TEMPLATE = """
      {
        "meta": {
          "demoSeedKey": "hcop-default-test-savatierra-v1",
          "demoContentVersion": 3
        },
        "patient": {"fullName": "IDENTIDAD QUE NO DEBE PERSISTIR"},
        "oncology": {"diagnosis": "Historia demostrativa"},
        "evolutions": []
      }
      """;

  private final PatientRepository patients = mock(PatientRepository.class);
  private final PatientDocumentRepository documents = mock(PatientDocumentRepository.class);
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final ObjectMapper mapper = new ObjectMapper();
  private final PatientDocumentService documentService = new PatientDocumentService(
      patients,
      documents,
      mapper,
      mock(HcopProperties.class),
      Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void actorExists() {
    when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(7L);
  }

  @Test
  void packagedClinicalResourceHasTheExpectedDemoShapeAndNoOriginalIdentity() throws Exception {
    String raw = new ClassPathResource("bootstrap/patients/test-savatierra-v3.json")
        .getContentAsString(StandardCharsets.UTF_8);
    JsonNode document = mapper.readTree(raw);

    assertThat(document.path("meta").path("demoSeedKey").asText())
        .isEqualTo(DefaultDemoPatientBootstrap.SEED_KEY);
    assertThat(document.path("meta").path("demoContentVersion").asLong()).isEqualTo(3);
    assertThat(document.path("patient").isObject()).isTrue();
    assertThat(document.path("patient").size()).isZero();
    assertThat(document.path("oncology").path("diagnosisRecords").size()).isEqualTo(2);
    assertThat(document.path("diagnoses").size()).isEqualTo(2);
    assertThat(document.path("studies").size()).isEqualTo(7);
    assertThat(document.path("treatments").size()).isEqualTo(5);
    assertThat(document.path("evolutions").size()).isEqualTo(7);
    assertThat(document.path("prescriptions").size()).isZero();
    assertThat(document.path("researchRecords").size()).isZero();
    assertThat(document.path("exam").path("weightKg").asText()).isEqualTo("82");
    assertThat(document.path("exam").path("heightM").asText()).isEqualTo("1.80");

    String normalizedResource = raw.toLowerCase(Locale.ROOT);
    assertThat(normalizedResource)
        .contains(
            "adenocarcinoma de colon sigmoides",
            "melanoma cutáneo de extensión superficial",
            "caso clínico compuesto de demostración",
            "folfox")
        .doesNotContain(
            "@",
            "próstata",
            "prostata",
            "tiroides",
            "gleason",
            "psa",
            "psma",
            "trimix",
            "radioterapia");
    String resourceWithoutKnownEightDigitCode = raw.replace("93655004", "");
    assertThat(resourceWithoutKnownEightDigitCode.matches(
        "(?s).*(?<!\\d)\\d{8}(?!\\d).*")).isFalse();
    JsonNode classifications = document.path("oncology").path("diagnosticClassifications");
    assertThat(classifications.path("ajcc").path("code").asText()).isEqualTo("colon");
    assertThat(classifications.path("snomed").path("code").asText()).isEqualTo("363406005");
    assertThat(classifications.path("cie10").path("code").asText()).isEqualTo("C18.9");
  }

  @Test
  void disabledSeedDoesNotReadOrWriteAnything() {
    bootstrap(false, TEMPLATE).seed();

    verifyNoInteractions(patients, documents, jdbc);
  }

  @Test
  void existingPatientAndDocumentRemainUntouched() {
    Patient patient = patient(8000000000000002L);
    when(patients.findBySeedKey(DefaultDemoPatientBootstrap.SEED_KEY))
        .thenReturn(Optional.of(patient));
    when(documents.find(patient.id())).thenReturn(Optional.of(stored(patient.id())));

    bootstrap(true, "JSON QUE NO DEBE LEERSE").seed();

    verify(patients, never()).insertSeedIfMissing(any(), anyString());
    verify(documents, never()).insertIfMissing(anyLong(), any(), anyLong(), eq(false));
    verifyNoInteractions(jdbc);
  }

  @Test
  void createsOneSeededPatientAndItsClinicalDocumentWithSyntheticIdentity() {
    Patient patient = patient(8000000000000002L);
    when(patients.findBySeedKey(DefaultDemoPatientBootstrap.SEED_KEY))
        .thenReturn(Optional.empty());
    when(patients.findDuplicate("99000002", "DEMO-SAVATIERRA-99000002"))
        .thenReturn(Optional.empty());
    when(patients.insertSeedIfMissing(any(), eq(DefaultDemoPatientBootstrap.SEED_KEY)))
        .thenReturn(Optional.of(patient));
    when(documents.find(patient.id())).thenReturn(Optional.empty());
    when(documents.insertIfMissing(eq(patient.id()), any(), eq(7L), eq(false)))
        .thenReturn(Optional.of(stored(patient.id())));

    bootstrap(true, TEMPLATE).seed();

    ArgumentCaptor<NewPatient> identity = ArgumentCaptor.forClass(NewPatient.class);
    verify(patients).insertSeedIfMissing(
        identity.capture(),
        eq(DefaultDemoPatientBootstrap.SEED_KEY));
    assertThat(identity.getValue()).satisfies(value -> {
      assertThat(value.firstName()).isEqualTo("Tomas Alejandro");
      assertThat(value.lastName()).isEqualTo("Test Savatierra");
      assertThat(value.dni()).isEqualTo("99000002");
      assertThat(value.medicalRecord()).isEqualTo("DEMO-SAVATIERRA-99000002");
      assertThat(value.birthDate()).isEqualTo(LocalDate.of(1970, 2, 14));
      assertThat(value.email()).isEqualTo("tomas.savatierra@example.invalid");
    });

    ArgumentCaptor<JsonNode> clinicalDocument = ArgumentCaptor.forClass(JsonNode.class);
    verify(documents).insertIfMissing(
        eq(patient.id()), clinicalDocument.capture(), eq(7L), eq(false));
    JsonNode saved = clinicalDocument.getValue();
    assertThat(saved.path("patient").path("fullName").asText())
        .isEqualTo("Test Savatierra, Tomas Alejandro");
    assertThat(saved.path("patient").path("dni").asText()).isEqualTo("99000002");
    assertThat(saved.path("patient").path("birthDate").asText()).isEqualTo("1970-02-14");
    assertThat(saved.path("meta").path("demo").asBoolean()).isTrue();
    assertThat(saved.path("meta").path("demoSeedKey").asText())
        .isEqualTo(DefaultDemoPatientBootstrap.SEED_KEY);
    assertThat(saved.path("meta").path("demoContentVersion").asLong()).isEqualTo(3);
    assertThat(saved.path("meta").path("demoManagedRevision").asLong()).isEqualTo(1);
    assertThat(saved.path("oncology").path("diagnosis").asText())
        .isEqualTo("Historia demostrativa");
  }

  @Test
  void repairsOnlyTheMissingDocumentOfTheMarkedPatient() {
    Patient patient = patient(8000000000000002L);
    when(patients.findBySeedKey(DefaultDemoPatientBootstrap.SEED_KEY))
        .thenReturn(Optional.of(patient));
    when(documents.find(patient.id())).thenReturn(Optional.empty());
    when(documents.insertIfMissing(eq(patient.id()), any(), eq(7L), eq(false)))
        .thenReturn(Optional.of(stored(patient.id())));

    bootstrap(true, TEMPLATE).seed();

    verify(patients, never()).findDuplicate(anyString(), anyString());
    verify(patients, never()).insertSeedIfMissing(any(), anyString());
    verify(documents).insertIfMissing(eq(patient.id()), any(), eq(7L), eq(false));
  }

  @Test
  void updatesAPristineManagedDocumentFromContentVersionTwoToThree() {
    Patient patient = patient(8000000000000002L);
    StoredDocument versionTwo = managedStored(patient.id(), 2, 1, 1);
    when(patients.findBySeedKey(DefaultDemoPatientBootstrap.SEED_KEY))
        .thenReturn(Optional.of(patient));
    when(documents.find(patient.id())).thenReturn(Optional.of(versionTwo));
    when(documents.update(eq(patient.id()), any(), eq(1L), eq(7L)))
        .thenReturn(Optional.of(stored(patient.id(), mapper.createObjectNode(), 2)));

    bootstrap(true, TEMPLATE).seed();

    ArgumentCaptor<JsonNode> replacement = ArgumentCaptor.forClass(JsonNode.class);
    verify(documents).update(eq(patient.id()), replacement.capture(), eq(1L), eq(7L));
    JsonNode saved = replacement.getValue();
    assertThat(saved.path("meta").path("demoContentVersion").asLong()).isEqualTo(3);
    assertThat(saved.path("meta").path("demoManagedRevision").asLong()).isEqualTo(2);
    assertThat(saved.path("meta").path("persistenceRevision").asLong()).isEqualTo(2);
    assertThat(saved.path("patient").path("fullName").asText())
        .isEqualTo("Test Savatierra, Tomas Alejandro");
    assertThat(saved.path("oncology").path("diagnosis").asText())
        .isEqualTo("Historia demostrativa");
    verify(documents, never()).insertIfMissing(anyLong(), any(), anyLong(), eq(false));
  }

  @Test
  void doesNotOverwriteAManagedDocumentAfterAHumanEdit() {
    Patient patient = patient(8000000000000002L);
    StoredDocument edited = managedStored(patient.id(), 2, 1, 2);
    when(patients.findBySeedKey(DefaultDemoPatientBootstrap.SEED_KEY))
        .thenReturn(Optional.of(patient));
    when(documents.find(patient.id())).thenReturn(Optional.of(edited));

    bootstrap(true, TEMPLATE).seed();

    verify(documents, never()).update(anyLong(), any(), anyLong(), anyLong());
    verifyNoInteractions(jdbc);
  }

  @Test
  void sameContentVersionIsANoOpEvenWhenTheDocumentIsStillPristine() {
    Patient patient = patient(8000000000000002L);
    StoredDocument current = managedStored(patient.id(), 3, 1, 1);
    when(patients.findBySeedKey(DefaultDemoPatientBootstrap.SEED_KEY))
        .thenReturn(Optional.of(patient));
    when(documents.find(patient.id())).thenReturn(Optional.of(current));

    bootstrap(true, TEMPLATE).seed();

    verify(documents, never()).update(anyLong(), any(), anyLong(), anyLong());
    verifyNoInteractions(jdbc);
  }

  @Test
  void anUnclassifiedOptimisticConflictIsNonFatalAndDoesNotRetry() {
    Patient patient = patient(8000000000000002L);
    StoredDocument versionTwo = managedStored(patient.id(), 2, 1, 1);
    when(patients.findBySeedKey(DefaultDemoPatientBootstrap.SEED_KEY))
        .thenReturn(Optional.of(patient));
    when(documents.find(patient.id())).thenReturn(Optional.of(versionTwo));
    when(documents.update(eq(patient.id()), any(), eq(1L), eq(7L)))
        .thenReturn(Optional.empty());

    bootstrap(true, TEMPLATE).seed();

    verify(documents).update(eq(patient.id()), any(), eq(1L), eq(7L));
    verify(documents, times(2)).find(patient.id());
    verify(documents, never()).insertIfMissing(anyLong(), any(), anyLong(), eq(false));
  }

  @Test
  void acceptsAnOptimisticConflictWhenAnotherInstanceAlreadyAppliedVersionThree() {
    Patient patient = patient(8000000000000002L);
    StoredDocument versionTwo = managedStored(patient.id(), 2, 1, 1);
    StoredDocument versionThree = managedStored(patient.id(), 3, 2, 2);
    when(patients.findBySeedKey(DefaultDemoPatientBootstrap.SEED_KEY))
        .thenReturn(Optional.of(patient));
    when(documents.find(patient.id()))
        .thenReturn(Optional.of(versionTwo), Optional.of(versionThree));
    when(documents.update(eq(patient.id()), any(), eq(1L), eq(7L)))
        .thenReturn(Optional.empty());

    bootstrap(true, TEMPLATE).seed();

    verify(documents).update(eq(patient.id()), any(), eq(1L), eq(7L));
    verify(documents, times(2)).find(patient.id());
  }

  @Test
  void acceptsAnOptimisticConflictWhenAHumanEditedTheManagedDocument() {
    Patient patient = patient(8000000000000002L);
    StoredDocument versionTwo = managedStored(patient.id(), 2, 1, 1);
    StoredDocument humanEdit = managedStored(patient.id(), 2, 1, 2);
    when(patients.findBySeedKey(DefaultDemoPatientBootstrap.SEED_KEY))
        .thenReturn(Optional.of(patient));
    when(documents.find(patient.id()))
        .thenReturn(Optional.of(versionTwo), Optional.of(humanEdit));
    when(documents.update(eq(patient.id()), any(), eq(1L), eq(7L)))
        .thenReturn(Optional.empty());

    bootstrap(true, TEMPLATE).seed();

    verify(documents).update(eq(patient.id()), any(), eq(1L), eq(7L));
    verify(documents, times(2)).find(patient.id());
  }

  @Test
  void recoversThePatientCreatedByAConcurrentBootstrap() {
    Patient patient = patient(8000000000000002L);
    when(patients.findBySeedKey(DefaultDemoPatientBootstrap.SEED_KEY))
        .thenReturn(Optional.empty(), Optional.of(patient));
    when(patients.findDuplicate("99000002", "DEMO-SAVATIERRA-99000002"))
        .thenReturn(Optional.empty());
    when(patients.insertSeedIfMissing(any(), eq(DefaultDemoPatientBootstrap.SEED_KEY)))
        .thenReturn(Optional.empty());
    when(documents.find(patient.id())).thenReturn(Optional.of(stored(patient.id())));

    bootstrap(true, TEMPLATE).seed();

    verify(patients).insertSeedIfMissing(any(), eq(DefaultDemoPatientBootstrap.SEED_KEY));
    verify(documents, never()).insertIfMissing(anyLong(), any(), anyLong(), eq(false));
  }

  @Test
  void skipsANaturalKeyCollisionWithoutTakingOverTheExistingPatient() {
    Patient collision = patient(42L);
    when(patients.findBySeedKey(DefaultDemoPatientBootstrap.SEED_KEY))
        .thenReturn(Optional.empty());
    when(patients.findDuplicate("99000002", "DEMO-SAVATIERRA-99000002"))
        .thenReturn(Optional.of(collision));

    bootstrap(true, TEMPLATE).seed();

    verify(patients, never()).insertSeedIfMissing(any(), anyString());
    verifyNoInteractions(documents, jdbc);
  }

  @Test
  void rejectsAMalformedClinicalResourceWithoutLeavingADocument() {
    Patient patient = patient(8000000000000002L);
    when(patients.findBySeedKey(DefaultDemoPatientBootstrap.SEED_KEY))
        .thenReturn(Optional.of(patient));
    when(documents.find(patient.id())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> bootstrap(true, "[]").seed())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("debe contener un objeto JSON");

    verify(documents, never()).insertIfMissing(anyLong(), any(), anyLong(), eq(false));
  }

  @Test
  void skipsAMissingDocumentWhenThereIsNoEnabledAuditActor() {
    Patient patient = patient(8000000000000002L);
    when(patients.findBySeedKey(DefaultDemoPatientBootstrap.SEED_KEY))
        .thenReturn(Optional.of(patient));
    when(documents.find(patient.id())).thenReturn(Optional.empty());
    when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(null);

    bootstrap(true, TEMPLATE).seed();

    verify(documents, never()).insertIfMissing(anyLong(), any(), anyLong(), eq(false));
  }

  @Test
  void skipsAnOutdatedDocumentBeforeUpdatingWhenThereIsNoEnabledAuditActor() {
    Patient patient = patient(8000000000000002L);
    StoredDocument versionTwo = managedStored(patient.id(), 2, 1, 1);
    when(patients.findBySeedKey(DefaultDemoPatientBootstrap.SEED_KEY))
        .thenReturn(Optional.of(patient));
    when(documents.find(patient.id())).thenReturn(Optional.of(versionTwo));
    when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(null);

    bootstrap(true, TEMPLATE).seed();

    verify(documents, never()).update(anyLong(), any(), anyLong(), anyLong());
  }

  @Test
  void skipsBeforeInsertingThePatientWhenThereIsNoEnabledAuditActor() {
    when(patients.findBySeedKey(DefaultDemoPatientBootstrap.SEED_KEY))
        .thenReturn(Optional.empty());
    when(patients.findDuplicate("99000002", "DEMO-SAVATIERRA-99000002"))
        .thenReturn(Optional.empty());
    when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(null);

    bootstrap(true, TEMPLATE).seed();

    verify(patients, never()).insertSeedIfMissing(any(), anyString());
    verifyNoInteractions(documents);
  }

  private DefaultDemoPatientBootstrap bootstrap(boolean enabled, String template) {
    return new DefaultDemoPatientBootstrap(
        patients,
        documents,
        documentService,
        jdbc,
        mapper,
        enabled,
        new ByteArrayResource(template.getBytes(StandardCharsets.UTF_8)));
  }

  private Patient patient(long id) {
    return new Patient(
        id,
        "99000002",
        "DEMO-SAVATIERRA-99000002",
        "Tomas Alejandro",
        "Test Savatierra",
        LocalDate.of(1970, 2, 14),
        "Masculino",
        "COBERTURA DEMO",
        "DEMO-9900000200",
        "2604000002",
        "tomas.savatierra@example.invalid",
        "Domicilio de prueba 100",
        true,
        NOW,
        NOW);
  }

  private StoredDocument stored(long patientId) {
    return new StoredDocument(patientId, mapper.createObjectNode(), 1, null, NOW, NOW);
  }

  private StoredDocument stored(long patientId, JsonNode document, long revision) {
    return new StoredDocument(patientId, document, revision, null, NOW, NOW);
  }

  private StoredDocument managedStored(
      long patientId,
      long contentVersion,
      long managedRevision,
      long persistenceRevision) {
    var document = mapper.createObjectNode();
    document.withObject("/meta")
        .put("demoSeedKey", DefaultDemoPatientBootstrap.SEED_KEY)
        .put("demoContentVersion", contentVersion)
        .put("demoManagedRevision", managedRevision);
    document.withObject("/narrative").put("summary", "Edición anterior");
    return stored(patientId, document, persistenceRevision);
  }
}
