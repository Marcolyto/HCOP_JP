package ar.com.hexium.hcop.treatment.domain;

import java.time.LocalDate;

public record TreatmentPatientView(
    long id, String fullName, String dni, String medicalRecord, LocalDate birthDate, String sex) {
}
