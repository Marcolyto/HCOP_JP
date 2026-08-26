package ar.com.hexium.hcop.patient.infrastructure.web;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.auth.AuthService;
import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.patient.application.port.in.PatientDocumentUseCase;
import ar.com.hexium.hcop.patient.application.port.in.PatientUseCase;
import ar.com.hexium.hcop.patient.application.port.in.PatientUseCase.Creation;
import ar.com.hexium.hcop.patient.application.port.in.PatientUseCase.DuplicatePatientException;
import ar.com.hexium.hcop.patient.domain.NewPatient;
import ar.com.hexium.hcop.patient.domain.Patient;
import ar.com.hexium.hcop.patient.domain.StoredDocument;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class PatientController {
  private final PatientUseCase patients;
  private final PatientDocumentUseCase documents;
  private final PatientJsonMapper json;
  private final AuthContext authContext;
  private final AuthService auth;
  private final ClinicalDocumentAccessPolicy accessPolicy;

  public PatientController(
      PatientUseCase patients,
      PatientDocumentUseCase documents,
      PatientJsonMapper json,
      AuthContext authContext,
      AuthService auth,
      ClinicalDocumentAccessPolicy accessPolicy) {
    this.patients = patients;
    this.documents = documents;
    this.json = json;
    this.authContext = authContext;
    this.auth = auth;
    this.accessPolicy = accessPolicy;
  }

  @GetMapping({"/api/clinical/patients", "/api/lira/patients"})
  Map<String, Object> search(@RequestParam(defaultValue = "") String q, HttpServletRequest request) {
    authContext.requirePermission(request, "section.history.view");
    List<Map<String, Object>> found = patients.search(q).stream().map(json::searchView).toList();
    return Map.of("ok", true, "patients", found, "total", found.size());
  }

  @PostMapping("/api/clinical/patients")
  ResponseEntity<Map<String, Object>> create(
      @RequestBody NewPatientRequest body,
      HttpServletRequest request) {
    authContext.requirePermission(request, "section.history.edit");
    SessionPrincipal actor = authContext.require(request);
    try {
      Creation creation = patients.create(body.toDomain(), actor.userId(), authContext.sessionId(request));
      JsonNode state = accessPolicy.visibleState(
          (JsonNode) documents.state(creation.document()), actor);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("ok", true);
      result.put("created", true);
      result.put("patientId", Long.toString(creation.patient().id()));
      result.put("revision", creation.document().revision());
      result.put("patient", json.patientView(creation.patient()));
      result.put("state", state);
      return ResponseEntity.status(HttpStatus.CREATED).body(result);
    } catch (DuplicatePatientException duplicate) {
      Patient existing = duplicate.patient();
      return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
          "ok", false,
          "code", "DUPLICATE_PATIENT",
          "error", duplicate.getMessage(),
          "existingPatient", Map.of(
              "id", Long.toString(existing.id()),
              "fullName", existing.fullName())));
    }
  }

  @GetMapping("/api/lira/patients/{patientId}/preview")
  Map<String, Object> preview(
      @PathVariable long patientId,
      HttpServletRequest request) {
    authContext.requirePermission(request, "section.history.view");
    Patient patient = patients.require(patientId);
    StoredDocument stored = documents.require(patientId);
    Object state = documents.state(stored);
    Map<String, Object> preview = new LinkedHashMap<>();
    preview.put("patient", json.patientView(patient));
    preview.put("counts", json.counts(state));
    preview.put("completeness", json.completeness());
    preview.put("warnings", List.of());
    preview.put("importable", true);
    preview.put("available", true);
    return Map.of("ok", true, "preview", preview);
  }

  @PostMapping({"/api/lira/patients/{patientId}/import", "/api/lira/patients/{patientId}/refresh"})
  Map<String, Object> importPatient(
      @PathVariable long patientId,
      HttpServletRequest request) {
    authContext.requirePermission(request, "section.history.view");
    SessionPrincipal principal = authContext.require(request);
    Patient patient = patients.require(patientId);
    StoredDocument document = documents.require(patientId);
    auth.setActivePatient(authContext.sessionId(request), patientId);
    return Map.of(
        "ok", true,
        "state", accessPolicy.visibleState((JsonNode) documents.state(document), principal),
        "counts", json.counts(document.document()),
        "completeness", json.completeness(),
        "warnings", List.of(),
        "revision", document.revision(),
        "reused", true,
        "refreshed", true);
  }

  public record NewPatientRequest(
      String firstName,
      String lastName,
      String dni,
      String medicalRecord,
      LocalDate birthDate,
      String sex,
      String insurance,
      String affiliateNumber,
      String phone,
      String email,
      String address) {
    NewPatient toDomain() {
      return new NewPatient(
          clean(firstName), clean(lastName), clean(dni), clean(medicalRecord), birthDate,
          clean(sex), clean(insurance), clean(affiliateNumber), clean(phone), clean(email), clean(address));
    }

    private String clean(String value) {
      return value == null ? "" : value.trim();
    }
  }
}
