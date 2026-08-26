package ar.com.hexium.hcop.workflow.domain;

/**
 * Contenido mínimo de una evolución inmutable generada por una acción de workflow — el JSON
 * completo (specialty fija, highlighted, attachments vacíos, sourceRef) lo arma
 * {@code infrastructure.patient.PatientEvolutionAdapter}, el único lugar del módulo que conoce
 * el formato de evolución de la historia clínica.
 */
public record EvolutionDraft(
    String id, String reason, String text, String treatmentId, Long requestId, Integer cycleNumber) {
}
