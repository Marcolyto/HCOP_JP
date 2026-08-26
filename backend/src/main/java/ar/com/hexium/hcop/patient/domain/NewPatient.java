package ar.com.hexium.hcop.patient.domain;

import java.time.LocalDate;

public record NewPatient(
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
