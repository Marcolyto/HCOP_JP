package ar.com.hexium.hcop.qr.infrastructure.patient;

import ar.com.hexium.hcop.patient.PatientRepository.Patient;
import ar.com.hexium.hcop.patient.PatientService;
import ar.com.hexium.hcop.qr.application.port.out.QrPatientPort;
import ar.com.hexium.hcop.qr.domain.QrPatientView;
import org.springframework.stereotype.Component;

@Component
public class QrPatientAdapter implements QrPatientPort {
  private final PatientService patients;

  public QrPatientAdapter(PatientService patients) {
    this.patients = patients;
  }

  @Override
  public QrPatientView requirePatient(long patientId) {
    Patient patient = patients.require(patientId);
    return new QrPatientView(patient.id(), patient.fullName(), patient.dni());
  }
}
