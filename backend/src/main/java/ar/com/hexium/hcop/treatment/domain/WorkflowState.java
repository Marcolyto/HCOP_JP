package ar.com.hexium.hcop.treatment.domain;

import java.time.LocalDate;
import java.util.Map;

public record WorkflowState(
    String continuityStatus, Integer effectiveFromCycle, String suspensionReason,
    LocalDate resumeDate, boolean prescriptionRequired, long managementRevision,
    Map<Integer, String> prescriptionStates,
    Map<Integer, Map<String, Long>> pendingRequestIdsByCycle) {
}
