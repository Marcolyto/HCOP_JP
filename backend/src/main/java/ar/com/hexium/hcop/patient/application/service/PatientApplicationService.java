package ar.com.hexium.hcop.patient.application.service;

import ar.com.hexium.hcop.auth.AuthService;
import ar.com.hexium.hcop.patient.application.port.in.PatientDocumentUseCase;
import ar.com.hexium.hcop.patient.application.port.in.PatientUseCase;
import ar.com.hexium.hcop.patient.application.port.out.PatientDocumentStore;
import ar.com.hexium.hcop.patient.application.port.out.PatientStore;
import ar.com.hexium.hcop.patient.domain.NewPatient;
import ar.com.hexium.hcop.patient.domain.Patient;
import ar.com.hexium.hcop.patient.domain.StoredDocument;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

public final class PatientApplicationService implements PatientUseCase {
  private final PatientStore patients;
  private final PatientDocumentStore documents;
  private final AuthService auth;
  private final Clock clock;

  public PatientApplicationService(
      PatientStore patients, PatientDocumentStore documents, AuthService auth, Clock clock) {
    this.patients = patients;
    this.documents = documents;
    this.auth = auth;
    this.clock = clock;
  }

  @Override
  public List<Patient> search(String query) {
    String normalized = query == null ? "" : query.trim();
    return normalized.isBlank() ? patients.recent() : patients.search(normalized);
  }

  @Override
  public Patient require(long patientId) {
    return patients.find(patientId)
        .orElseThrow(() -> new PatientFailure(PatientFailure.Type.NOT_FOUND, "Paciente no encontrado."));
  }

  @Override
  public Creation create(NewPatient input, long actorId, String sessionId) {
    validate(input);
    patients.findDuplicate(input.dni(), input.medicalRecord()).ifPresent(existing -> {
      throw new DuplicatePatientException(existing);
    });
    Patient patient = patients.insert(input);
    StoredDocument document = documents.createBlank(patient, actorId);
    auth.setActivePatient(sessionId, patient.id());
    return new Creation(patient, document);
  }

  private void validate(NewPatient input) {
    if (input.firstName() == null || input.firstName().isBlank()) {
      throw new PatientFailure(PatientFailure.Type.INVALID, "El nombre es obligatorio.");
    }
    if (input.lastName() == null || input.lastName().isBlank()) {
      throw new PatientFailure(PatientFailure.Type.INVALID, "El apellido es obligatorio.");
    }
    if ((input.dni() == null || input.dni().isBlank())
        && (input.medicalRecord() == null || input.medicalRecord().isBlank())) {
      throw new PatientFailure(PatientFailure.Type.INVALID, "Informe el DNI o la historia clínica.");
    }
    if (input.birthDate() != null && input.birthDate().isAfter(LocalDate.now(clock))) {
      throw new PatientFailure(PatientFailure.Type.INVALID, "La fecha de nacimiento no puede ser futura.");
    }
  }
}
