package ar.com.hexium.hcop.diagnosis.domain;

/**
 * Proyección de una entrada de diagnóstico dentro de la historia clínica del paciente.
 * {@code source} es el registro crudo tal como vive en la historia (un árbol JSON opaco, el
 * mismo patrón que {@code catalog.domain.TreatmentScheme.definition()}) — {@code null} cuando el
 * registro se sintetizó a partir del diagnóstico oncológico "actual" (sin un registro propio en
 * {@code oncology.diagnosisRecords}).
 */
public record DiagnosisRecord(
    String id, String display, String date, String stage, Object source) {
}
