package ar.com.hexium.hcop.treatment.domain;

import java.time.Instant;
import java.time.LocalDate;

/**
 * {@code payload} es el árbol JSON opaco del tratamiento (incluye campos arbitrarios que el
 * frontend envió y que no tienen modelo propio) — mismo patrón que
 * {@code catalog.domain.TreatmentScheme.definition()}: el dominio nunca lo interpreta, solo lo
 * transporta; en runtime sigue siendo el {@code JsonNode} real.
 */
public record Treatment(
    String id, long patientId, String diagnosisId, LocalDate createdOn, LocalDate firstCycleDate,
    int initialCycle, int cycleCount, int cycleDays, String treatmentType, String intent,
    String diagnosis, String schemeId, String schemeName, String oncologist, String status,
    String consentStatus, boolean consentAvailable, Integer durationMinutes, Object payload,
    long revision, Instant createdAt, Instant updatedAt) {
}
