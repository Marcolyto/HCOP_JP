package ar.com.hexium.hcop.patient.application.port.in;

import java.time.Instant;
import java.time.LocalDate;

/** Alta clínica mínima, independiente de HTTP y de PostgreSQL. */
public interface PatientCreationUseCase {
  Creation create(NewPatientData input);

  record Creation(PatientProfile patient) {
  }

  record NewPatientData(
      String firstName,
      String lastName,
      String dni,
      String medicalRecord,
      LocalDate birthDate,
      String sex,
      String insurance,
      String affiliateNumber,
      String phone,
      String email,
      String address) {
  }

  record PatientProfile(
      long id,
      String dni,
      String medicalRecord,
      String firstName,
      String lastName,
      LocalDate birthDate,
      String sex,
      String insurance,
      String affiliateNumber,
      String phone,
      String email,
      String address,
      boolean localOnly,
      Instant createdAt,
      Instant updatedAt) {
    public String fullName() {
      String joined = String.join(", ", lastName, firstName).replaceAll("(^[, ]+|[, ]+$)", "");
      return joined.isBlank() ? Long.toString(id) : joined;
    }
  }
}
