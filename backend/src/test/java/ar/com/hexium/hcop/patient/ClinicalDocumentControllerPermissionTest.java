package ar.com.hexium.hcop.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.patient.PatientDocumentRepository.StoredDocument;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

class ClinicalDocumentControllerPermissionTest {

  @Test
  void conservaConstructoresCompatiblesAlAgregarAutoridadesEstructuradas() {
    PatientDocumentService documents = mock(PatientDocumentService.class);
    AuthContext auth = mock(AuthContext.class);
    ObjectMapper mapper = new ObjectMapper();
    Clock clock = Clock.fixed(Instant.parse("2026-08-02T21:30:00Z"), ZoneOffset.UTC);
    ClinicalDocumentAccessPolicy accessPolicy = new ClinicalDocumentAccessPolicy();
    ClinicalDocumentChangeValidator validator = new ClinicalDocumentChangeValidator();
    ClinicalSummaryPlanAuthority summary = new ClinicalSummaryPlanAuthority(mapper, clock);
    ClinicalChiefComplaintAuthority chief = new ClinicalChiefComplaintAuthority(mapper, clock);
    ClinicalCurrentIllnessAuthority illness = new ClinicalCurrentIllnessAuthority(mapper, clock);
    ClinicalPersonalHistoryAuthority personal = new ClinicalPersonalHistoryAuthority(mapper, clock);

    assertThatCode(() -> new ClinicalDocumentController(
        documents, auth, accessPolicy, validator, summary))
        .doesNotThrowAnyException();
    assertThatCode(() -> new ClinicalDocumentController(
        documents, auth, accessPolicy, validator, summary, chief))
        .doesNotThrowAnyException();
    assertThatCode(() -> new ClinicalDocumentController(
        documents, auth, accessPolicy, validator, summary, chief, illness))
        .doesNotThrowAnyException();
    assertThatCode(() -> new ClinicalDocumentController(
        documents, auth, accessPolicy, validator, summary, chief, illness, personal))
        .doesNotThrowAnyException();
  }

  @Test
  void ocultaPrescripcionesSinPermisoDeLectura() {
    PatientDocumentService documents = mock(PatientDocumentService.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    SessionPrincipal principal = new SessionPrincipal(
        7L, "enfermeria", "", "Enfermería", "", "", true, 42L,
        List.of(), Set.of("section.history.view", "section.history.edit"));
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode storedState = mapper.createObjectNode();
    storedState.putArray("prescriptions").addObject().put("id", "rx-1");
    Instant now = Instant.parse("2026-08-01T12:00:00Z");
    StoredDocument stored = new StoredDocument(42L, storedState, 3L, null, now, now);

    when(auth.require(request)).thenReturn(principal);
    when(documents.require(42L)).thenReturn(stored);
    when(documents.state(stored)).thenReturn(storedState.deepCopy());

    JsonNode response = new ClinicalDocumentController(
        documents,
        auth,
        new ClinicalDocumentAccessPolicy(),
        new ClinicalDocumentChangeValidator(),
        new ClinicalSummaryPlanAuthority(mapper, Clock.fixed(now, ZoneOffset.UTC)))
        .get(request).getBody();

    assertThat(response).isNotNull();
    assertThat(response.has("prescriptions")).isFalse();
  }

  @Test
  void rechazaCambiosDePrescripcionSinPermisoEspecifico() {
    PatientDocumentService documents = mock(PatientDocumentService.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    SessionPrincipal principal = new SessionPrincipal(
        7L, "enfermeria", "", "Enfermería", "", "", true, 42L,
        List.of(), Set.of("section.history.edit", "section.history.view"));
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode storedState = mapper.createObjectNode();
    storedState.putArray("prescriptions");
    ObjectNode incomingState = storedState.deepCopy();
    incomingState.withObject("/meta").put("persistenceRevision", 3);
    incomingState.withArray("prescriptions").addObject().put("id", "rx-1");
    Instant now = Instant.parse("2026-08-01T12:00:00Z");

    when(auth.require(request)).thenReturn(principal);
    when(documents.require(42L)).thenReturn(new StoredDocument(42L, storedState, 3L, null, now, now));

    ClinicalDocumentController controller = new ClinicalDocumentController(
        documents,
        auth,
        new ClinicalDocumentAccessPolicy(),
        new ClinicalDocumentChangeValidator(),
        new ClinicalSummaryPlanAuthority(mapper, Clock.fixed(now, ZoneOffset.UTC)));

    assertThatThrownBy(() -> controller.put(incomingState, request))
        .isInstanceOfSatisfying(ApiException.class, error -> {
          org.assertj.core.api.Assertions.assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN);
          org.assertj.core.api.Assertions.assertThat(error.getMessage()).contains("prescripciones");
        });
  }

  @Test
  void conservaPrescripcionesOcultasAlEditarOtraParteDeLaHistoria() {
    PatientDocumentService documents = mock(PatientDocumentService.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    SessionPrincipal principal = new SessionPrincipal(
        7L, "enfermeria", "", "Enfermería", "", "", true, 42L,
        List.of(), Set.of("section.history.edit", "section.history.view"));
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode storedState = mapper.createObjectNode();
    storedState.putArray("prescriptions").addObject().put("id", "rx-1");
    ObjectNode incomingState = mapper.createObjectNode();
    incomingState.withObject("/meta").put("persistenceRevision", 3);
    incomingState.withObject("/narrative").put("summary", "Evolución autorizada");
    Instant now = Instant.parse("2026-08-01T12:00:00Z");
    StoredDocument stored = new StoredDocument(42L, storedState, 3L, null, now, now);
    StoredDocument saved = new StoredDocument(42L, storedState, 4L, null, now, now);

    when(auth.require(request)).thenReturn(principal);
    when(documents.require(42L)).thenReturn(stored);
    when(documents.save(eq(42L), org.mockito.ArgumentMatchers.any(JsonNode.class), eq(3L), eq(7L)))
        .thenReturn(saved);
    when(documents.state(saved)).thenReturn(storedState.deepCopy());

    new ClinicalDocumentController(
        documents,
        auth,
        new ClinicalDocumentAccessPolicy(),
        new ClinicalDocumentChangeValidator(),
        new ClinicalSummaryPlanAuthority(mapper, Clock.fixed(now, ZoneOffset.UTC)))
        .put(incomingState, request);

    ArgumentCaptor<JsonNode> stateCaptor = ArgumentCaptor.forClass(JsonNode.class);
    verify(documents).save(eq(42L), stateCaptor.capture(), eq(3L), eq(7L));
    assertThat(stateCaptor.getValue().path("prescriptions")).isEqualTo(storedState.path("prescriptions"));
    assertThat(stateCaptor.getValue().path("narrative").path("summary").asText())
        .isEqualTo("Evolución autorizada");
  }

  @Test
  void permiteCambiarPrescripcionesConLecturaYEdicionEspecificas() {
    PatientDocumentService documents = mock(PatientDocumentService.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    SessionPrincipal principal = new SessionPrincipal(
        9L, "oncologia", "", "Oncología", "", "", true, 42L,
        List.of(), Set.of(
            "section.history.edit", "section.history.view",
            "section.prescriptions.view", "section.prescriptions.edit"));
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode storedState = mapper.createObjectNode();
    storedState.putArray("prescriptions");
    ObjectNode incomingState = storedState.deepCopy();
    incomingState.withObject("/meta").put("persistenceRevision", 3);
    incomingState.withArray("prescriptions").addObject().put("id", "rx-autorizada");
    Instant now = Instant.parse("2026-08-01T12:00:00Z");
    StoredDocument stored = new StoredDocument(42L, storedState, 3L, null, now, now);
    StoredDocument saved = new StoredDocument(42L, incomingState, 4L, null, now, now);

    when(auth.require(request)).thenReturn(principal);
    when(documents.require(42L)).thenReturn(stored);
    when(documents.save(eq(42L), org.mockito.ArgumentMatchers.any(JsonNode.class), eq(3L), eq(9L)))
        .thenReturn(saved);
    when(documents.state(saved)).thenReturn(incomingState.deepCopy());

    new ClinicalDocumentController(
        documents,
        auth,
        new ClinicalDocumentAccessPolicy(),
        new ClinicalDocumentChangeValidator(),
        new ClinicalSummaryPlanAuthority(mapper, Clock.fixed(now, ZoneOffset.UTC)))
        .put(incomingState, request);

    ArgumentCaptor<JsonNode> stateCaptor = ArgumentCaptor.forClass(JsonNode.class);
    verify(documents).save(eq(42L), stateCaptor.capture(), eq(3L), eq(9L));
    assertThat(stateCaptor.getValue().path("prescriptions").get(0).path("id").asText())
        .isEqualTo("rx-autorizada");
  }
}
