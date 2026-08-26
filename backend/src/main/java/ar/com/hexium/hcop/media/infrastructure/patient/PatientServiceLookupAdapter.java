package ar.com.hexium.hcop.media.infrastructure.patient;

import ar.com.hexium.hcop.media.application.port.out.PatientLookupPort;
import ar.com.hexium.hcop.media.application.service.MediaFailure;
import ar.com.hexium.hcop.patient.application.port.in.PatientUseCase;
import ar.com.hexium.hcop.patient.application.service.PatientFailure;
import org.springframework.stereotype.Component;

@Component
public class PatientServiceLookupAdapter implements PatientLookupPort {
  private final PatientUseCase patients;

  public PatientServiceLookupAdapter(PatientUseCase patients) {
    this.patients = patients;
  }

  @Override
  public void requireExists(long patientId) {
    try {
      patients.require(patientId);
    } catch (PatientFailure failure) {
      throw new MediaFailure(MediaFailure.Type.NOT_FOUND, failure.getMessage());
    }
  }
}
