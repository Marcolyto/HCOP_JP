package ar.com.hexium.hcop.qr.domain;

import java.util.Map;

/**
 * Contenido mínimo de la evolución inmutable generada al escanear un QR — el JSON completo
 * (author/date/createdAt/updatedAt/attachments/linkedStudyIds) lo arma
 * {@code infrastructure.patient.PatientEvolutionAdapter}, el único lugar del módulo que conoce
 * el formato de evolución de la historia clínica.
 */
public record EvolutionDraft(
    String id, String reason, String text, String specialty, boolean highlighted,
    Map<String, String> sourceRef) {
}
