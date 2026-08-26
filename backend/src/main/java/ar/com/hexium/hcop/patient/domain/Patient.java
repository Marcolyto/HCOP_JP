package ar.com.hexium.hcop.patient.domain;

import java.time.Instant;
import java.time.LocalDate;

public record Patient(
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
