package ar.com.hexium.hcop.workflow.domain;

/** Proyección mínima de un tratamiento necesaria para el workflow de suspensión/reanudación. */
public record TreatmentWorkflowSummary(
    long patientId, String treatmentId, String scheme, String diagnosis, String status,
    String patientDni, String patientName, int initialCycle, int cycleCount) {
}
