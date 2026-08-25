package ar.com.hexium.hcop.patient;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.auth.AuthService;
import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.infusion.InfusionService;
import ar.com.hexium.hcop.patient.PatientDocumentRepository.StoredDocument;
import ar.com.hexium.hcop.patient.PatientRepository.Patient;
import ar.com.hexium.hcop.treatment.TreatmentService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class PatientWorkspaceController {
  private final PatientService patients;
  private final PatientDocumentService documents;
  private final TreatmentService treatments;
  private final InfusionService infusions;
  private final AuthService authService;
  private final AuthContext auth;
  private final ClinicalDocumentAccessPolicy accessPolicy;

  public PatientWorkspaceController(
      PatientService patients,
      PatientDocumentService documents,
      TreatmentService treatments,
      InfusionService infusions,
      AuthService authService,
      AuthContext auth,
      ClinicalDocumentAccessPolicy accessPolicy) {
    this.patients = patients;
    this.documents = documents;
    this.treatments = treatments;
    this.infusions = infusions;
    this.authService = authService;
    this.auth = auth;
    this.accessPolicy = accessPolicy;
  }

  @PostMapping("/api/clinical/patients/{patientId}/activate")
  Map<String, Object> activate(@PathVariable long patientId, HttpServletRequest request) {
    auth.requirePermission(request, "section.history.view");
    SessionPrincipal principal = auth.require(request);
    authService.setActivePatient(auth.token(request), patientId);
    return workspace(patientId, principal);
  }

  @GetMapping("/api/clinical/patients/{patientId}/workspace")
  ResponseEntity<Map<String, Object>> workspace(
      @PathVariable long patientId,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.history.view");
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.PRAGMA, "no-cache")
        .body(workspace(patientId, auth.require(request)));
  }

  private Map<String, Object> workspace(long patientId, SessionPrincipal principal) {
    Patient patient = patients.require(patientId);
    StoredDocument stored = documents.require(patientId);
    JsonNode state = accessPolicy.visibleState(documents.state(stored), principal);
    List<Map<String, Object>> treatmentRows = treatments.list(patientId);
    List<Map<String, Object>> infusionRows = infusions.list(patientId, null);
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("patientId", Long.toString(patientId));
    document.put("document", state);
    document.put("revision", stored.revision());
    document.put("updatedAt", stored.updatedAt().toString());
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("ok", true);
    response.put("patientId", Long.toString(patientId));
    response.put("patient", patients.patientView(patient));
    response.put("state", state);
    response.put("document", document);
    response.put("revision", stored.revision());
    response.put("updatedAt", stored.updatedAt().toString());
    response.put("counts", patients.counts(state));
    response.put("completeness", patients.completeness());
    response.put("warnings", List.of());
    response.put("treatments", Map.of(
        "oncology", treatmentRows,
        "nonOncology", List.of(),
        "procedures", List.of(),
        "referrals", List.of()));
    response.put("infusions", infusionRows);
    return response;
  }
}
