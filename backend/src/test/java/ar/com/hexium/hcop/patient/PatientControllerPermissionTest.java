package ar.com.hexium.hcop.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.auth.AuthService;
import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.patient.PatientDocumentRepository.StoredDocument;
import ar.com.hexium.hcop.patient.PatientRepository.Patient;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class PatientControllerPermissionTest {

  @Test
  void noExponePrescripcionesAlReabrirPacienteSinPermisoDeLectura() {
    PatientService patients = mock(PatientService.class);
    PatientDocumentService documents = mock(PatientDocumentService.class);
    AuthContext authContext = mock(AuthContext.class);
    AuthService auth = mock(AuthService.class);
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

    when(authContext.require(request)).thenReturn(principal);
    when(authContext.token(request)).thenReturn("token-qa");
    when(patients.require(42L)).thenReturn(patient);
    when(documents.require(42L)).thenReturn(stored);
    when(documents.state(stored)).thenReturn(storedState.deepCopy());
    when(patients.counts(storedState)).thenReturn(Map.of());
    when(patients.completeness()).thenReturn(Map.of("available", true));

    PatientController controller = new PatientController(
        patients, documents, authContext, auth, new ClinicalDocumentAccessPolicy());

    Map<String, Object> response = controller.importPatient(42L, request);

    assertThat(((JsonNode) response.get("state")).has("prescriptions")).isFalse();
  }
}
