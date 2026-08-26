package ar.com.hexium.hcop.infusion.application.port.out;

/**
 * El algoritmo de planificación (convertir el protocolo prescrito en turnos de Hospital de Día
 * reales) trabaja íntegramente sobre el árbol JSON legacy del detalle de tratamiento — no hay un
 * modelo de dominio propio que valga la pena introducir sin rediseñar el protocolo entero (mismo
 * criterio que {@code catalog.domain.TreatmentScheme.definition()}). Por eso este puerto no tiene
 * más granularidad que el caso de uso: toda la lógica real vive en el adapter.
 */
public interface TreatmentApplicationLogisticsStore {

  void synchronizeExistingTreatments();

  void synchronizeTreatment(String treatmentId);
}
