package ar.com.hexium.hcop.infusion.domain;

import java.time.Instant;
import java.time.LocalDate;

/** {@code applicationDrugs} es un árbol JSON opaco, ver {@link NewInfusion}. */
public record Logistics(
    long patientId, String treatmentId, int cycleNumber, int applicationDay,
    LocalDate plannedDate, String medicationState, String prescriptionState,
    int durationMinutes, String durationSource, String drugSummary, Object applicationDrugs,
    String notes, long revision, Instant updatedAt) {
}
