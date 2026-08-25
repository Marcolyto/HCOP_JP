package ar.com.hexium.hcop.patient;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.patient.PatientDocumentRepository.StoredDocument;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/hc")
public class ClinicalDocumentController {
  private final PatientDocumentService documents;
  private final AuthContext auth;
  private final ClinicalDocumentAccessPolicy accessPolicy;
  private final ClinicalDocumentChangeValidator changeValidator;
  private final ClinicalSummaryPlanAuthority summaryPlanAuthority;
  private final ClinicalChiefComplaintAuthority chiefComplaintAuthority;
  private final ClinicalCurrentIllnessAuthority currentIllnessAuthority;
  private final ClinicalPersonalHistoryAuthority personalHistoryAuthority;
  private final ClinicalPhysicalExamAuthority physicalExamAuthority;

  @Autowired
  public ClinicalDocumentController(
      PatientDocumentService documents,
      AuthContext auth,
      ClinicalDocumentAccessPolicy accessPolicy,
      ClinicalDocumentChangeValidator changeValidator,
      ClinicalSummaryPlanAuthority summaryPlanAuthority,
      ClinicalChiefComplaintAuthority chiefComplaintAuthority,
      ClinicalCurrentIllnessAuthority currentIllnessAuthority,
      ClinicalPersonalHistoryAuthority personalHistoryAuthority,
      ClinicalPhysicalExamAuthority physicalExamAuthority) {
    this.documents = documents;
    this.auth = auth;
    this.accessPolicy = accessPolicy;
    this.changeValidator = changeValidator;
    this.summaryPlanAuthority = summaryPlanAuthority;
    this.chiefComplaintAuthority = chiefComplaintAuthority;
    this.currentIllnessAuthority = currentIllnessAuthority;
    this.personalHistoryAuthority = personalHistoryAuthority;
    this.physicalExamAuthority = physicalExamAuthority;
  }

  /** Backward-compatible constructor retained for focused controller tests. */
  public ClinicalDocumentController(
      PatientDocumentService documents,
      AuthContext auth,
      ClinicalDocumentAccessPolicy accessPolicy,
      ClinicalDocumentChangeValidator changeValidator,
      ClinicalSummaryPlanAuthority summaryPlanAuthority,
      ClinicalChiefComplaintAuthority chiefComplaintAuthority,
      ClinicalCurrentIllnessAuthority currentIllnessAuthority,
      ClinicalPersonalHistoryAuthority personalHistoryAuthority) {
    this(
        documents,
        auth,
        accessPolicy,
        changeValidator,
        summaryPlanAuthority,
        chiefComplaintAuthority,
        currentIllnessAuthority,
        personalHistoryAuthority,
        null);
  }

  /** Backward-compatible constructor retained for focused controller tests. */
  public ClinicalDocumentController(
      PatientDocumentService documents,
      AuthContext auth,
      ClinicalDocumentAccessPolicy accessPolicy,
      ClinicalDocumentChangeValidator changeValidator,
      ClinicalSummaryPlanAuthority summaryPlanAuthority,
      ClinicalChiefComplaintAuthority chiefComplaintAuthority,
      ClinicalCurrentIllnessAuthority currentIllnessAuthority) {
    this(
        documents,
        auth,
        accessPolicy,
        changeValidator,
        summaryPlanAuthority,
        chiefComplaintAuthority,
        currentIllnessAuthority,
        null,
        null);
  }

  /** Backward-compatible constructor retained for focused controller tests. */
  public ClinicalDocumentController(
      PatientDocumentService documents,
      AuthContext auth,
      ClinicalDocumentAccessPolicy accessPolicy,
      ClinicalDocumentChangeValidator changeValidator,
      ClinicalSummaryPlanAuthority summaryPlanAuthority,
      ClinicalChiefComplaintAuthority chiefComplaintAuthority) {
    this(
        documents,
        auth,
        accessPolicy,
        changeValidator,
        summaryPlanAuthority,
        chiefComplaintAuthority,
        null,
        null,
        null);
  }

  /** Backward-compatible constructor retained for focused controller tests. */
  public ClinicalDocumentController(
      PatientDocumentService documents,
      AuthContext auth,
      ClinicalDocumentAccessPolicy accessPolicy,
      ClinicalDocumentChangeValidator changeValidator,
      ClinicalSummaryPlanAuthority summaryPlanAuthority) {
    this(
        documents,
        auth,
        accessPolicy,
        changeValidator,
        summaryPlanAuthority,
        null,
        null,
        null,
        null);
  }

  @GetMapping
  ResponseEntity<JsonNode> get(HttpServletRequest request) {
    auth.requirePermission(request, "section.history.view");
    SessionPrincipal principal = auth.require(request);
    JsonNode state = principal.activePatientId() == null
        ? documents.blankTemplate()
        : documents.state(documents.require(principal.activePatientId()));
    state = accessPolicy.visibleState(state, principal);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.PRAGMA, "no-cache")
        .body(state);
  }

  @PutMapping
  ResponseEntity<Map<String, Object>> put(
      @RequestBody JsonNode state,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.history.edit");
    SessionPrincipal principal = auth.require(request);
    if (principal.activePatientId() == null) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "Abra un paciente antes de guardar.",
          "ACTIVE_PATIENT_REQUIRED");
    }
    StoredDocument current = documents.require(principal.activePatientId());
    JsonNode stateToSave = accessPolicy.writableState(state, current.document(), principal);
    long expected = state.path("meta").path("persistenceRevision").asLong(0);
    if (expected < 1) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "Falta la revisión de la historia clínica.",
          "CLINICAL_REVISION_REQUIRED");
    }
    changeValidator.validate(stateToSave, current.document());
    stateToSave = summaryPlanAuthority.canonicalize(stateToSave, current.document(), principal);
    if (chiefComplaintAuthority != null) {
      stateToSave = chiefComplaintAuthority.canonicalize(
          stateToSave,
          current.document(),
          principal);
    }
    if (currentIllnessAuthority != null) {
      stateToSave = currentIllnessAuthority.canonicalize(
          stateToSave,
          current.document(),
          principal);
    }
    if (personalHistoryAuthority != null) {
      stateToSave = personalHistoryAuthority.canonicalize(
          stateToSave,
          current.document(),
          principal);
    }
    if (physicalExamAuthority != null) {
      stateToSave = physicalExamAuthority.canonicalize(
          stateToSave,
          current.document(),
          principal);
    }
    StoredDocument saved = documents.save(
        principal.activePatientId(),
        stateToSave,
        expected,
        principal.userId());
    JsonNode canonicalState = accessPolicy.visibleState(documents.state(saved), principal);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(Map.of(
            "ok", true,
            "state", canonicalState,
            "unified", Map.of("persisted", true, "revision", saved.revision())));
  }

  @PostMapping("/restore-demo-on-reload")
  Map<String, Object> restoreDemo() {
    return Map.of("ok", true, "restored", false, "persistent", true);
  }
}
