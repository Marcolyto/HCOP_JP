package ar.com.hexium.hcop.patient.application.port.out;

import ar.com.hexium.hcop.patient.domain.NewPatient;
import ar.com.hexium.hcop.patient.domain.Patient;
import java.util.List;
import java.util.Optional;

public interface PatientStore {

  List<Patient> search(String query);

  List<Patient> recent();

  Optional<Patient> find(long patientId);

  Optional<Patient> findBySeedKey(String seedKey);

  Optional<Patient> findDuplicate(String dni, String medicalRecord);

  Patient insert(NewPatient input);

  Optional<Patient> insertSeedIfMissing(NewPatient input, String seedKey);
}
