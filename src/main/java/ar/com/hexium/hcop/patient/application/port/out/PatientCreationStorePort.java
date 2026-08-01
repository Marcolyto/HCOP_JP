package ar.com.hexium.hcop.patient.application.port.out;

import ar.com.hexium.hcop.patient.application.port.in.PatientCreationUseCase.NewPatientData;
import ar.com.hexium.hcop.patient.application.port.in.PatientCreationUseCase.PatientProfile;
import java.util.Optional;

public interface PatientCreationStorePort {
  Optional<PatientProfile> findDuplicate(String dni, String medicalRecord);

  PatientProfile insert(NewPatientData input);
}
