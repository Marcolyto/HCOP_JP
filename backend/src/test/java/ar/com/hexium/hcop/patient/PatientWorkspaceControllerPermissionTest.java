package ar.com.hexium.hcop.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.auth.AuthService;
import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.infusion.InfusionService;
import ar.com.hexium.hcop.patient.PatientDocumentRepository.StoredDocument;
import ar.com.hexium.hcop.patient.PatientRepository.Patient;
import ar.com.hexium.hcop.treatment.TreatmentService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class PatientWorkspaceControllerPermissionTest {

  @Test
  void noExponePrescripcionesYMarcaElWorkspaceComoNoAlmacenable() {
    PatientService patients = mock(PatientService.class);
    PatientDocumentService documents = mock(PatientDocumentService.class);
    TreatmentService treatments = mock(TreatmentService.class);
    InfusionService infusions = mock(InfusionService.class);
    AuthService authService = mock(AuthService.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    SessionPrincipal principal = new SessionPrincipal(
        7L, "enfermeria", "", "Enfermería", "", "", true, 42L,
        List.of(), Set.of("section.history.view", "section.history.edit"));
    Instant now = Instant.parse("2026-08-01T12:00:00Z");
    Patient patient = new Patient(
        42L, "99000001", "QA-001", "Paciente", "Angular", LocalDate.of(1980, 1, 2),
        "Femenino", "Cobertura", "AF-1", "", "", "", true, now, now);
    ObjectNode storedState = new ObjectMapper().createObjectNode();
    storedState.putArray("prescriptions").addObject().put("id", "rx-1");
    StoredDocument stored = new StoredDocument(42L, storedState, 3L, null, now, now);

    when(auth.require(request)).thenReturn(principal);
    when(patients.require(42L)).thenReturn(patient);
    when(documents.require(42L)).thenReturn(stored);
    when(documents.state(stored)).thenReturn(storedState.deepCopy());
    when(treatments.list(42L)).thenReturn(List.of());
    when(infusions.list(42L, null)).thenReturn(List.of());
    when(patients.patientView(patient)).thenReturn(Map.of("fullName", patient.fullName()));
    when(patients.counts(org.mockito.ArgumentMatchers.any(JsonNode.class))).thenReturn(Map.of());
    when(patients.completeness()).thenReturn(Map.of("available", true));

    PatientWorkspaceController controller = new PatientWorkspaceController(
        patients, documents, treatments, infusions, authService, auth,
        new ClinicalDocumentAccessPolicy());

    ResponseEntity<Map<String, Object>> http = controller.workspace(42L, request);
    assertThat(http.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(http.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
    assertThat(http.getHeaders().getFirst(HttpHeaders.PRAGMA)).isEqualTo("no-cache");
    Map<String, Object> response = http.getBody();
    assertThat(response).isNotNull();
    JsonNode state = (JsonNode) response.get("state");
    @SuppressWarnings("unchecked")
    Map<String, Object> document = (Map<String, Object>) response.get("document");

    assertThat(state.has("prescriptions")).isFalse();
    assertThat(((JsonNode) document.get("document")).has("prescriptions")).isFalse();
    assertThat(response.get("revision")).isEqualTo(3L);
    assertThat(response.get("updatedAt")).isEqualTo(now.toString());
  }
}
