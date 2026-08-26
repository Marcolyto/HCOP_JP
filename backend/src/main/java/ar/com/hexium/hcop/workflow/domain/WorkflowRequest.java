package ar.com.hexium.hcop.workflow.domain;

import java.time.Instant;
import java.time.LocalDate;

/**
 * {@code context} es el árbol JSON opaco que acompaña la solicitud (patientName/patientDni/
 * scheme/diagnosis) — mismo patrón que {@code catalog.domain.TreatmentScheme.definition()}: el
 * dominio nunca lo interpreta, solo lo transporta desde la persistencia hasta la respuesta web.
 */
public record WorkflowRequest(
    long id, String type, String status, long patientId, String treatmentId, int cycleNumber,
    long requestedBy, long assignedTo, String message, Object context, String resolution,
    String resolutionReason, LocalDate resumeDate, Instant seenAt, Instant resolvedAt,
    Long resolvedBy, Instant createdAt, Instant updatedAt, String patientDni, String patientName,
    String scheme, String diagnosis, String requestedByName, String assignedToName) {
}
