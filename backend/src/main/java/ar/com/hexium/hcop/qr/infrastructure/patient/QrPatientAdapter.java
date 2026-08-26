package ar.com.hexium.hcop.qr.infrastructure.patient;

import ar.com.hexium.hcop.patient.application.port.in.PatientUseCase;
import ar.com.hexium.hcop.patient.application.service.PatientFailure;
import ar.com.hexium.hcop.patient.domain.Patient;
import ar.com.hexium.hcop.qr.application.port.out.QrPatientPort;
import ar.com.hexium.hcop.qr.application.service.QrFailure;
import ar.com.hexium.hcop.qr.domain.QrPatientView;
import org.springframework.stereotype.Component;

@Component
public class QrPatientAdapter implements QrPatientPort {
  private final PatientUseCase patients;

  public QrPatientAdapter(PatientUseCase patients) {
    this.patients = patients;
  }

  @Override
  public QrPatientView requirePatient(long patientId) {
    try {
      Patient patient = patients.require(patientId);
      return new QrPatientView(patient.id(), patient.fullName(), patient.dni());
    } catch (PatientFailure failure) {
      throw new QrFailure(QrFailure.Type.NOT_FOUND, failure.getMessage());
    }
  }
}
