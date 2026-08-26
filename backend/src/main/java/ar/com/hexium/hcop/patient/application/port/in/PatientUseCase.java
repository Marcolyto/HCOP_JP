package ar.com.hexium.hcop.patient.application.port.in;

import ar.com.hexium.hcop.patient.domain.NewPatient;
import ar.com.hexium.hcop.patient.domain.Patient;
import ar.com.hexium.hcop.patient.domain.StoredDocument;
import java.util.List;

public interface PatientUseCase {

  List<Patient> search(String query);

  Patient require(long patientId);

  Creation create(NewPatient input, long actorId, String sessionId);

  record Creation(Patient patient, StoredDocument document) {
  }

  final class DuplicatePatientException extends RuntimeException {
    private final Patient patient;

    public DuplicatePatientException(Patient patient) {
      super("Ya existe un paciente con ese DNI o historia clínica.");
      this.patient = patient;
    }

    public Patient patient() {
      return patient;
    }
  }
}
