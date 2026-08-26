package ar.com.hexium.hcop.patient.domain;

import java.time.Instant;

/**
 * {@code document} es el árbol JSON opaco de la historia clínica completa — mismo patrón que
 * {@code catalog.domain.TreatmentScheme.definition()}; sigue siendo el {@code JsonNode} real en
 * runtime, nunca convertido. Consumido directo por varios módulos ya hexagonales
 * ({@code diagnosis}, {@code treatment}) que lo castean de vuelta en su propia infraestructura.
 */
public record StoredDocument(
    long patientId,
    Object document,
    long revision,
    Instant importedAt,
    Instant createdAt,
    Instant updatedAt) {
}
