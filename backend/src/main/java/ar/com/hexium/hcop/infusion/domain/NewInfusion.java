package ar.com.hexium.hcop.infusion.domain;

import java.time.Instant;
import java.util.List;

/** {@code sourceRef} es un árbol JSON opaco (mismo patrón que
 * {@code catalog.domain.TreatmentScheme.definition()}) — sigue siendo el {@code ObjectNode} real
 * en runtime, el dominio nunca lo interpreta. */
public record NewInfusion(
    long patientId, String treatmentId, int cycleNumber, int applicationDay,
    Instant scheduledAt, String chair, Integer durationMinutes, String clinicalStatus,
    String pharmacyStatus, String administrationStatus, boolean appointmentConfirmed,
    String notes, Object sourceRef, List<Medication> medications) {
}
