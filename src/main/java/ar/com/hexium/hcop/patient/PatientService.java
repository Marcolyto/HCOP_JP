package ar.com.hexium.hcop.patient;

import ar.com.hexium.hcop.auth.AuthService;
import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.patient.application.port.in.PatientCreationUseCase;
import ar.com.hexium.hcop.patient.application.port.in.PatientCreationUseCase.NewPatientData;
import ar.com.hexium.hcop.patient.application.port.in.PatientCreationUseCase.PatientProfile;
import ar.com.hexium.hcop.patient.application.service.PatientCreationApplicationService.PatientCreationFailure;
import ar.com.hexium.hcop.patient.application.service.PatientCreationApplicationService.Reason;
import ar.com.hexium.hcop.patient.PatientDocumentRepository.StoredDocument;
import ar.com.hexium.hcop.patient.PatientRepository.NewPatient;
import ar.com.hexium.hcop.patient.PatientRepository.Patient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class PatientService {
  private final PatientRepository patients;
  private final PatientDocumentService documents;
  private final AuthService auth;
  private final PatientCreationUseCase patientCreation;

  public PatientService(
      PatientRepository patients,
      PatientDocumentService documents,
      AuthService auth,
      PatientCreationUseCase patientCreation) {
    this.patients = patients;
    this.documents = documents;
    this.auth = auth;
    this.patientCreation = patientCreation;
  }

  public List<Map<String, Object>> search(String query) {
    String normalized = query == null ? "" : query.trim();
    List<Patient> found = normalized.isBlank() ? patients.recent() : patients.search(normalized);
    return found.stream().map(this::searchView).toList();
  }

  public Patient require(long patientId) {
    return patients.find(patientId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Paciente no encontrado."));
  }

  @Transactional
  public Creation create(NewPatient input, SessionPrincipal actor, String token) {
    Patient patient;
    try {
      PatientProfile created = patientCreation.create(new NewPatientData(
          input.firstName(), input.lastName(), input.dni(), input.medicalRecord(), input.birthDate(),
          input.sex(), input.insurance(), input.affiliateNumber(), input.phone(), input.email(), input.address()))
          .patient();
      patient = legacyPatient(created);
    } catch (PatientCreationFailure failure) {
      if (failure.reason() == Reason.DUPLICATE_PATIENT) {
        throw new DuplicatePatientException(legacyPatient(failure.duplicate()));
      }
      throw new ApiException(HttpStatus.BAD_REQUEST, failure.getMessage());
    }
    StoredDocument document = documents.createBlank(patient, actor.userId());
    auth.setActivePatient(token, patient.id());
    return new Creation(patient, document);
  }

  public Map<String, Object> patientView(Patient patient) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("fullName", patient.fullName());
    view.put("dni", patient.dni());
    view.put("medicalRecord", patient.medicalRecord());
    view.put("birthDate", patient.birthDate() == null ? "" : patient.birthDate().toString());
    view.put("birthDatePrecision", "day");
    view.put("phone", patient.phone());
    view.put("insurance", patient.insurance());
    view.put("affiliateNumber", patient.affiliateNumber());
    view.put("address", patient.address());
    view.put("email", patient.email());
    view.put("sex", patient.sex());
    view.put("deathDate", "");
    view.put("deathDatePrecision", "day");
    view.put("liraId", Long.toString(patient.id()));
    view.put("coverages", patient.insurance().isBlank() && patient.affiliateNumber().isBlank()
        ? List.of()
        : List.of(Map.of(
            "id", "local:primary",
            "name", patient.insurance(),
            "affiliateNumber", patient.affiliateNumber(),
            "primary", true)));
    return view;
  }

  public Map<String, Object> preview(long patientId) {
    Patient patient = require(patientId);
    StoredDocument stored = documents.require(patientId);
    JsonNode state = documents.state(stored);
    Map<String, Integer> counts = counts(state);
    Map<String, Object> preview = new LinkedHashMap<>();
    preview.put("patient", patientView(patient));
    preview.put("counts", counts);
    preview.put("completeness", completeness());
    preview.put("warnings", List.of());
    preview.put("importable", true);
    preview.put("available", true);
    return preview;
  }

  public Map<String, Integer> counts(JsonNode state) {
    return Map.of(
        "diagnoses", arraySize(state.path("oncology").path("diagnosisRecords")),
        "antecedents", arraySize(state.path("personalHistory")),
        "evolutions", arraySize(state.path("evolutions")),
        "treatments", arraySize(state.path("oncology").path("systemicTreatments")),
        "studies", arraySize(state.path("studies")));
  }

  public Map<String, Object> completeness() {
    return Map.of(
        "available", true,
        "importable", true,
        "percent", 100,
        "status", "Ficha local",
        "missing", List.of(),
        "message", "Historia clínica disponible en HCOP JP.");
  }

  private Map<String, Object> searchView(Patient patient) {
    return Map.of(
        "id", Long.toString(patient.id()),
        "fullName", patient.fullName(),
        "numeroDocumento", patient.dni(),
        "numeroHC", patient.medicalRecord(),
        "birthDate", patient.birthDate() == null ? "" : patient.birthDate().toString(),
        "migrationState", "complete",
        "origin", patient.localOnly() ? "local" : "migration");
  }

  private int arraySize(JsonNode value) {
    return value.isArray() ? value.size() : 0;
  }

  private Patient legacyPatient(PatientProfile patient) {
    return new Patient(
        patient.id(), patient.dni(), patient.medicalRecord(), patient.firstName(), patient.lastName(),
        patient.birthDate(), patient.sex(), patient.insurance(), patient.affiliateNumber(), patient.phone(),
        patient.email(), patient.address(), patient.localOnly(), patient.createdAt(), patient.updatedAt());
  }

  public record Creation(Patient patient, StoredDocument document) {
  }

  public static final class DuplicatePatientException extends RuntimeException {
    private final Patient patient;

    DuplicatePatientException(Patient patient) {
      super("Ya existe un paciente con ese DNI o historia clínica.");
      this.patient = patient;
    }

    public Patient patient() {
      return patient;
    }
  }
}
