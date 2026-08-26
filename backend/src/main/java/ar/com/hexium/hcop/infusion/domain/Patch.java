package ar.com.hexium.hcop.infusion.domain;

import java.time.Instant;

/** {@code sourceRef} es un árbol JSON opaco, ver {@link NewInfusion}. */
public record Patch(
    Instant scheduledAt, String chair, Integer durationMinutes, String clinicalStatus,
    String pharmacyStatus, String administrationStatus, Boolean appointmentConfirmed,
    String notes, Object sourceRef) {
}
