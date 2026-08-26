package ar.com.hexium.hcop.infusion.domain;

import java.time.LocalDate;

/** {@code applicationDrugs} es un árbol JSON opaco, ver {@link NewInfusion}. */
public record Candidate(
    long patientId, String treatmentId, int cycleNumber, int applicationDay,
    LocalDate plannedDate,
    String medicationState, String prescriptionState, String logisticsNotes,
    long logisticsRevision, String patientDni, String medicalRecord, String firstName,
    String lastName, String insurance, String affiliateNumber, String diagnosis,
    String schemeId, String scheme, String treatmentType, int totalCycles, int cycleDays,
    Integer durationMinutes, String durationSource, String drugSummary, Object applicationDrugs,
    String pharmacyValidationStatus, String medicationSource,
    String stockReservationStatus, String applicationWorkflowStatus,
    Long applicationWorkflowRevision,
    String continuityStatus, Integer effectiveFromCycle, String suspensionReason,
    LocalDate resumeDate, boolean prescriptionRequired, Long managementRevision) {
  public String patientName() {
    return (lastName + ", " + firstName).replaceAll("(^[, ]+|[, ]+$)", "");
  }
}
