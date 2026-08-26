package ar.com.hexium.hcop.infusion.domain;

import java.time.Instant;

/** {@code sourceRef} es un árbol JSON opaco, ver {@link NewInfusion}. */
public record Infusion(
    long id, long patientId, String treatmentId, int cycleNumber, int applicationDay,
    Long applicationId,
    Instant scheduledAt, String chair, Integer durationMinutes, String clinicalStatus,
    String pharmacyStatus, String administrationStatus, boolean appointmentConfirmed,
    String notes, Object sourceRef, long revision, Instant createdAt, Instant updatedAt,
    String patientDni, String medicalRecord, String firstName, String lastName,
    String insurance, String affiliateNumber, String diagnosis, String scheme,
    String treatmentType, int totalCycles, int cycleDays) {
  public String patientName() {
    return (lastName + ", " + firstName).replaceAll("(^[, ]+|[, ]+$)", "");
  }
}
