package ar.com.hexium.hcop.treatment.infrastructure.patient;

import ar.com.hexium.hcop.patient.application.port.in.PatientUseCase;
import ar.com.hexium.hcop.patient.application.service.PatientFailure;
import ar.com.hexium.hcop.patient.domain.Patient;
import ar.com.hexium.hcop.treatment.application.port.out.TreatmentPatientPort;
import ar.com.hexium.hcop.treatment.application.service.TreatmentFailure;
import ar.com.hexium.hcop.treatment.domain.TreatmentPatientView;
import org.springframework.stereotype.Component;

@Component
public class TreatmentPatientAdapter implements TreatmentPatientPort {
  private final PatientUseCase patients;

  public TreatmentPatientAdapter(PatientUseCase patients) {
    this.patients = patients;
  }

  @Override
  public TreatmentPatientView requirePatient(long patientId) {
    try {
      Patient patient = patients.require(patientId);
      return new TreatmentPatientView(
          patient.id(), patient.fullName(), patient.dni(), patient.medicalRecord(),
          patient.birthDate(), patient.sex());
    } catch (PatientFailure failure) {
      throw new TreatmentFailure(TreatmentFailure.Type.NOT_FOUND, failure.getMessage());
    }
  }
}
