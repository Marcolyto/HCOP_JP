package ar.com.hexium.hcop.workflow.domain;

import java.time.Instant;
import java.time.LocalDate;

public record ManagementState(
    long patientId, String treatmentId, String status, Integer effectiveFromCycle,
    String reason, LocalDate resumeDate, boolean prescriptionRequired, long revision,
    Instant updatedAt) {
}
