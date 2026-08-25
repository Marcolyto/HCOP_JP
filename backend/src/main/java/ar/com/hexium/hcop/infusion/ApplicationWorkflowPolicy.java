package ar.com.hexium.hcop.infusion;

import ar.com.hexium.hcop.common.ApiException;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * Pure transition rules for one concrete protocol application.
 *
 * <p>Keeping these rules outside JDBC makes the safety gates explicit and unit-testable.
 */
final class ApplicationWorkflowPolicy {
  static final Set<String> MEDICATION_SOURCES = Set.of(
      "center_stock",
      "patient_to_bring",
      "patient_has_medication",
      "received_center",
      "pending_supplier");

  private ApplicationWorkflowPolicy() {
  }

  static void pharmacyValidation(State state, boolean approved) {
    unfinished(state);
    beforePreparation(state);
    beforeClinicalPass(state);
    if (!"confirmed".equals(state.prescriptionStatus())) {
      conflict("La aplicación todavía no posee una prescripción confirmada.");
    }
    if ("reserved".equals(state.stockStatus()) && !approved) {
      conflict("Libere la reserva antes de rechazar una orden ya reservada.");
    }
  }

  static void supplySource(State state, String source) {
    unfinished(state);
    beforePreparation(state);
    beforeClinicalPass(state);
    if (!MEDICATION_SOURCES.contains(source)) {
      badRequest("La fuente o custodia de la medicación es inválida.");
    }
    if ("reserved".equals(state.stockStatus()) && !"center_stock".equals(source)) {
      conflict("Libere primero el stock reservado antes de cambiar su procedencia.");
    }
  }

  static void reserveStock(State state) {
    unfinished(state);
    beforePreparation(state);
    beforeClinicalPass(state);
    if (!"approved".equals(state.pharmacyValidationStatus())) {
      conflict("Farmacia debe validar la orden antes de reservar stock.");
    }
    if (!"center_stock".equals(state.medicationSource())) {
      conflict("La reserva blanda sólo corresponde a medicación del stock del centro.");
    }
    if ("reserved".equals(state.stockStatus())) {
      conflict("La aplicación ya posee stock reservado.");
    }
  }

  static void releaseStock(State state) {
    unfinished(state);
    beforePreparation(state);
    beforeClinicalPass(state);
    if (!"reserved".equals(state.stockStatus())) {
      conflict("La aplicación no posee una reserva activa.");
    }
  }

  static void clinicalAuthorization(State state, boolean passed, boolean appointmentReadyToday) {
    unfinished(state);
    beforePreparation(state);
    if (passed && !appointmentReadyToday) {
      conflict(
          "El triaje requiere un turno confirmado para el día operativo actual.");
    }
    if (passed && !medicationReady(state)) {
      conflict("No se puede autorizar: la medicación todavía no está asegurada.");
    }
    if (!"approved".equals(state.pharmacyValidationStatus())) {
      conflict("Farmacia debe validar la orden antes del triaje.");
    }
  }

  static void schedule(
      String pharmacyValidationStatus,
      String medicationSource,
      String stockReservationStatus,
      String clinicalAuthorizationStatus,
      String preparationStatus,
      String administrationStatus) {
    schedule(
        "confirmed", "active", false,
        pharmacyValidationStatus, medicationSource, stockReservationStatus,
        clinicalAuthorizationStatus, preparationStatus, administrationStatus);
  }

  static void schedule(
      String prescriptionStatus,
      String continuityStatus,
      boolean prescriptionRequired,
      String pharmacyValidationStatus,
      String medicationSource,
      String stockReservationStatus,
      String clinicalAuthorizationStatus,
      String preparationStatus,
      String administrationStatus) {
    if (!"confirmed".equals(prescriptionStatus) || prescriptionRequired) {
      conflict("La aplicación requiere una prescripción médica confirmada antes de asignar turno.");
    }
    if (!"active".equals(continuityStatus)) {
      conflict("El tratamiento está suspendido y no admite nuevos turnos.");
    }
    if (!"approved".equals(pharmacyValidationStatus)) {
      conflict("Farmacia debe validar la orden antes de asignar el turno.");
    }
    if ("completed".equals(administrationStatus)) {
      conflict("La aplicación ya fue completada.");
    }
    if ("center_stock".equals(medicationSource)
        && !"reserved".equals(stockReservationStatus)) {
      conflict("Reserve el stock del centro antes de asignar el turno.");
    }
    if ("pending_supplier".equals(medicationSource)) {
      conflict("Defina cómo se obtendrá la medicación antes de asignar el turno.");
    }
    if (!Set.of("pending", "failed").contains(clinicalAuthorizationStatus)
        || !Set.of("not_started", "cancelled").contains(preparationStatus)
        || !"not_started".equals(administrationStatus)) {
      conflict(
          "El turno no puede moverse ni confirmarse después de iniciar la etapa clínica.");
    }
  }

  static void cancelAppointment(State state) {
    unfinished(state);
    if (!Set.of("pending", "failed").contains(state.clinicalStatus())
        || !Set.of("not_started", "cancelled").contains(state.preparationStatus())
        || !"not_started".equals(state.administrationStatus())) {
      conflict(
          "El turno ya ingresó a una etapa clínica y no puede retirarlo Admisión.");
    }
  }

  static void startPreparation(State state) {
    unfinished(state);
    if (!"passed".equals(state.clinicalStatus())) {
      conflict("La aplicación necesita una autorización clínica PASS.");
    }
    if (!"approved".equals(state.pharmacyValidationStatus())) {
      conflict("La orden farmacéutica ya no está validada.");
    }
    if (!medicationReady(state)) {
      conflict("La medicación todavía no está disponible para preparar.");
    }
    if (!Set.of("not_started", "cancelled").contains(state.preparationStatus())) {
      conflict("La preparación ya fue iniciada.");
    }
  }

  static void completePreparation(State state) {
    unfinished(state);
    if (!"passed".equals(state.clinicalStatus())) {
      conflict("La aplicación necesita una autorización clínica PASS.");
    }
    if (!"approved".equals(state.pharmacyValidationStatus())) {
      conflict("La orden farmacéutica ya no está validada.");
    }
    if (!medicationReady(state)) {
      conflict("La medicación todavía no está disponible para preparar.");
    }
    if (!Set.of("not_started", "in_preparation").contains(state.preparationStatus())) {
      conflict("La mezcla no está en condiciones de registrarse como preparada.");
    }
  }

  static void releasePreparation(State state) {
    unfinished(state);
    if (!"prepared".equals(state.preparationStatus())) {
      conflict("Primero debe finalizarse y verificarse la preparación.");
    }
  }

  static void restartPreparation(State state) {
    unfinished(state);
    if (!Set.of("prepared", "released").contains(state.preparationStatus())) {
      conflict("Sólo puede descartarse una preparación ya finalizada o liberada.");
    }
    if (!"not_started".equals(state.administrationStatus())) {
      conflict("No se puede reiniciar una preparación cuya administración comenzó.");
    }
  }

  static void preparationRestartReason(String reason) {
    if (reason == null || reason.trim().length() < 10) {
      badRequest("Explique en al menos 10 caracteres por qué se descarta y repite la preparación.");
    }
  }

  static void startAdministration(
      State state, boolean appointmentReadyToday, boolean hasTraceablePreparation) {
    unfinished(state);
    if (!appointmentReadyToday) {
      conflict(
          "La administración requiere un turno confirmado para el día operativo actual.");
    }
    if (!"passed".equals(state.clinicalStatus())) {
      conflict("La aplicación no posee autorización clínica PASS.");
    }
    if (!"released".equals(state.preparationStatus())) {
      conflict("Farmacia todavía no liberó la preparación.");
    }
    if (!hasTraceablePreparation) {
      conflict(
          "La preparación no posee lotes y vencimiento verificables; debe rehacerse.");
    }
    if (!"not_started".equals(state.administrationStatus())) {
      conflict("La administración ya fue iniciada o cerrada.");
    }
  }

  static void completeAdministration(State state) {
    unfinished(state);
    if (!"in_progress".equals(state.administrationStatus())) {
      conflict("La administración debe estar iniciada antes de completarla.");
    }
  }

  static void interruptAdministration(State state) {
    unfinished(state);
    if (!"in_progress".equals(state.administrationStatus())) {
      conflict("Sólo puede interrumpirse una administración en curso.");
    }
  }

  static void resolveAdministration(
      State state, boolean resume, boolean preparationStillValid) {
    unfinished(state);
    if (!"withheld".equals(state.administrationStatus())) {
      conflict("La aplicación no posee una interrupción pendiente de resolución.");
    }
    if (resume && !preparationStillValid) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "La preparación venció durante la interrupción; debe descartarse y repetirse.",
          "PREPARATION_EXPIRED");
    }
  }

  static boolean medicationReady(State state) {
    return switch (state.medicationSource()) {
      case "center_stock" -> "reserved".equals(state.stockStatus());
      case "patient_has_medication", "received_center" -> true;
      default -> false;
    };
  }

  private static void unfinished(State state) {
    if ("completed".equals(state.administrationStatus())
        || "completed".equals(state.workflowStatus())) {
      conflict("La aplicación ya fue completada y es inmutable.");
    }
  }

  private static void beforePreparation(State state) {
    if (!Set.of("not_started", "cancelled").contains(state.preparationStatus())
        || !Set.of("not_started", "withheld").contains(state.administrationStatus())) {
      conflict("La etapa ya avanzó y no puede retrocederse desde esta acción.");
    }
  }

  private static void beforeClinicalPass(State state) {
    if ("passed".equals(state.clinicalStatus())) {
      conflict(
          "El triaje ya fue aprobado; no puede cambiar Farmacia sin postergar y reevaluar la aplicación.");
    }
  }

  private static void conflict(String message) {
    throw new ApiException(HttpStatus.CONFLICT, message, "INVALID_APPLICATION_TRANSITION");
  }

  private static void badRequest(String message) {
    throw new ApiException(HttpStatus.BAD_REQUEST, message);
  }

  record State(
      String workflowStatus,
      String prescriptionStatus,
      String medicationSource,
      String pharmacyValidationStatus,
      String stockStatus,
      String clinicalStatus,
      String preparationStatus,
      String administrationStatus) {
  }
}
