package ar.com.hexium.hcop.infusion.domain;

import java.util.Optional;
import java.util.Set;

/**
 * Pure transition rules for one concrete protocol application.
 *
 * <p>Keeping these rules outside JDBC makes the safety gates explicit and unit-testable. Cada
 * método devuelve la primera {@link Violation} encontrada (o vacío si la transición es válida) —
 * el original lanzaba {@code ApiException} directo, pero {@code domain} no puede conocer
 * {@code HttpStatus}; quien llama (la aplicación) traduce la violación a {@code InfusionFailure}.
 */
public final class ApplicationWorkflowPolicy {
  public static final Set<String> MEDICATION_SOURCES = Set.of(
      "center_stock",
      "patient_to_bring",
      "patient_has_medication",
      "received_center",
      "pending_supplier");

  private ApplicationWorkflowPolicy() {
  }

  public static Optional<Violation> pharmacyValidation(State state, boolean approved) {
    Optional<Violation> v;
    if ((v = unfinished(state)).isPresent()) return v;
    if ((v = beforePreparation(state)).isPresent()) return v;
    if ((v = beforeClinicalPass(state)).isPresent()) return v;
    if (!"confirmed".equals(state.prescriptionStatus())) {
      return conflict("La aplicación todavía no posee una prescripción confirmada.");
    }
    if ("reserved".equals(state.stockStatus()) && !approved) {
      return conflict("Libere la reserva antes de rechazar una orden ya reservada.");
    }
    return Optional.empty();
  }

  public static Optional<Violation> supplySource(State state, String source) {
    Optional<Violation> v;
    if ((v = unfinished(state)).isPresent()) return v;
    if ((v = beforePreparation(state)).isPresent()) return v;
    if ((v = beforeClinicalPass(state)).isPresent()) return v;
    if (!MEDICATION_SOURCES.contains(source)) {
      return invalid("La fuente o custodia de la medicación es inválida.");
    }
    if ("reserved".equals(state.stockStatus()) && !"center_stock".equals(source)) {
      return conflict("Libere primero el stock reservado antes de cambiar su procedencia.");
    }
    return Optional.empty();
  }

  public static Optional<Violation> reserveStock(State state) {
    Optional<Violation> v;
    if ((v = unfinished(state)).isPresent()) return v;
    if ((v = beforePreparation(state)).isPresent()) return v;
    if ((v = beforeClinicalPass(state)).isPresent()) return v;
    if (!"approved".equals(state.pharmacyValidationStatus())) {
      return conflict("Farmacia debe validar la orden antes de reservar stock.");
    }
    if (!"center_stock".equals(state.medicationSource())) {
      return conflict("La reserva blanda sólo corresponde a medicación del stock del centro.");
    }
    if ("reserved".equals(state.stockStatus())) {
      return conflict("La aplicación ya posee stock reservado.");
    }
    return Optional.empty();
  }

  public static Optional<Violation> releaseStock(State state) {
    Optional<Violation> v;
    if ((v = unfinished(state)).isPresent()) return v;
    if ((v = beforePreparation(state)).isPresent()) return v;
    if ((v = beforeClinicalPass(state)).isPresent()) return v;
    if (!"reserved".equals(state.stockStatus())) {
      return conflict("La aplicación no posee una reserva activa.");
    }
    return Optional.empty();
  }

  public static Optional<Violation> clinicalAuthorization(
      State state, boolean passed, boolean appointmentReadyToday) {
    Optional<Violation> v;
    if ((v = unfinished(state)).isPresent()) return v;
    if ((v = beforePreparation(state)).isPresent()) return v;
    if (passed && !appointmentReadyToday) {
      return conflict("El triaje requiere un turno confirmado para el día operativo actual.");
    }
    if (passed && !medicationReady(state)) {
      return conflict("No se puede autorizar: la medicación todavía no está asegurada.");
    }
    if (!"approved".equals(state.pharmacyValidationStatus())) {
      return conflict("Farmacia debe validar la orden antes del triaje.");
    }
    return Optional.empty();
  }

  public static Optional<Violation> schedule(
      String pharmacyValidationStatus,
      String medicationSource,
      String stockReservationStatus,
      String clinicalAuthorizationStatus,
      String preparationStatus,
      String administrationStatus) {
    return schedule(
        "confirmed", "active", false,
        pharmacyValidationStatus, medicationSource, stockReservationStatus,
        clinicalAuthorizationStatus, preparationStatus, administrationStatus);
  }

  public static Optional<Violation> schedule(
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
      return conflict("La aplicación requiere una prescripción médica confirmada antes de asignar turno.");
    }
    if (!"active".equals(continuityStatus)) {
      return conflict("El tratamiento está suspendido y no admite nuevos turnos.");
    }
    if (!"approved".equals(pharmacyValidationStatus)) {
      return conflict("Farmacia debe validar la orden antes de asignar el turno.");
    }
    if ("completed".equals(administrationStatus)) {
      return conflict("La aplicación ya fue completada.");
    }
    if ("center_stock".equals(medicationSource)
        && !"reserved".equals(stockReservationStatus)) {
      return conflict("Reserve el stock del centro antes de asignar el turno.");
    }
    if ("pending_supplier".equals(medicationSource)) {
      return conflict("Defina cómo se obtendrá la medicación antes de asignar el turno.");
    }
    if (!Set.of("pending", "failed").contains(clinicalAuthorizationStatus)
        || !Set.of("not_started", "cancelled").contains(preparationStatus)
        || !"not_started".equals(administrationStatus)) {
      return conflict("El turno no puede moverse ni confirmarse después de iniciar la etapa clínica.");
    }
    return Optional.empty();
  }

  public static Optional<Violation> cancelAppointment(State state) {
    Optional<Violation> v;
    if ((v = unfinished(state)).isPresent()) return v;
    if (!Set.of("pending", "failed").contains(state.clinicalStatus())
        || !Set.of("not_started", "cancelled").contains(state.preparationStatus())
        || !"not_started".equals(state.administrationStatus())) {
      return conflict("El turno ya ingresó a una etapa clínica y no puede retirarlo Admisión.");
    }
    return Optional.empty();
  }

  public static Optional<Violation> startPreparation(State state) {
    Optional<Violation> v;
    if ((v = unfinished(state)).isPresent()) return v;
    if (!"passed".equals(state.clinicalStatus())) {
      return conflict("La aplicación necesita una autorización clínica PASS.");
    }
    if (!"approved".equals(state.pharmacyValidationStatus())) {
      return conflict("La orden farmacéutica ya no está validada.");
    }
    if (!medicationReady(state)) {
      return conflict("La medicación todavía no está disponible para preparar.");
    }
    if (!Set.of("not_started", "cancelled").contains(state.preparationStatus())) {
      return conflict("La preparación ya fue iniciada.");
    }
    return Optional.empty();
  }

  public static Optional<Violation> completePreparation(State state) {
    Optional<Violation> v;
    if ((v = unfinished(state)).isPresent()) return v;
    if (!"passed".equals(state.clinicalStatus())) {
      return conflict("La aplicación necesita una autorización clínica PASS.");
    }
    if (!"approved".equals(state.pharmacyValidationStatus())) {
      return conflict("La orden farmacéutica ya no está validada.");
    }
    if (!medicationReady(state)) {
      return conflict("La medicación todavía no está disponible para preparar.");
    }
    if (!Set.of("not_started", "in_preparation").contains(state.preparationStatus())) {
      return conflict("La mezcla no está en condiciones de registrarse como preparada.");
    }
    return Optional.empty();
  }

  public static Optional<Violation> releasePreparation(State state) {
    Optional<Violation> v;
    if ((v = unfinished(state)).isPresent()) return v;
    if (!"prepared".equals(state.preparationStatus())) {
      return conflict("Primero debe finalizarse y verificarse la preparación.");
    }
    return Optional.empty();
  }

  public static Optional<Violation> restartPreparation(State state) {
    Optional<Violation> v;
    if ((v = unfinished(state)).isPresent()) return v;
    if (!Set.of("prepared", "released").contains(state.preparationStatus())) {
      return conflict("Sólo puede descartarse una preparación ya finalizada o liberada.");
    }
    if (!"not_started".equals(state.administrationStatus())) {
      return conflict("No se puede reiniciar una preparación cuya administración comenzó.");
    }
    return Optional.empty();
  }

  public static Optional<Violation> preparationRestartReason(String reason) {
    if (reason == null || reason.trim().length() < 10) {
      return invalid("Explique en al menos 10 caracteres por qué se descarta y repite la preparación.");
    }
    return Optional.empty();
  }

  public static Optional<Violation> startAdministration(
      State state, boolean appointmentReadyToday, boolean hasTraceablePreparation) {
    Optional<Violation> v;
    if ((v = unfinished(state)).isPresent()) return v;
    if (!appointmentReadyToday) {
      return conflict("La administración requiere un turno confirmado para el día operativo actual.");
    }
    if (!"passed".equals(state.clinicalStatus())) {
      return conflict("La aplicación no posee autorización clínica PASS.");
    }
    if (!"released".equals(state.preparationStatus())) {
      return conflict("Farmacia todavía no liberó la preparación.");
    }
    if (!hasTraceablePreparation) {
      return conflict("La preparación no posee lotes y vencimiento verificables; debe rehacerse.");
    }
    if (!"not_started".equals(state.administrationStatus())) {
      return conflict("La administración ya fue iniciada o cerrada.");
    }
    return Optional.empty();
  }

  public static Optional<Violation> completeAdministration(State state) {
    Optional<Violation> v;
    if ((v = unfinished(state)).isPresent()) return v;
    if (!"in_progress".equals(state.administrationStatus())) {
      return conflict("La administración debe estar iniciada antes de completarla.");
    }
    return Optional.empty();
  }

  public static Optional<Violation> interruptAdministration(State state) {
    Optional<Violation> v;
    if ((v = unfinished(state)).isPresent()) return v;
    if (!"in_progress".equals(state.administrationStatus())) {
      return conflict("Sólo puede interrumpirse una administración en curso.");
    }
    return Optional.empty();
  }

  public static Optional<Violation> resolveAdministration(
      State state, boolean resume, boolean preparationStillValid) {
    Optional<Violation> v;
    if ((v = unfinished(state)).isPresent()) return v;
    if (!"withheld".equals(state.administrationStatus())) {
      return conflict("La aplicación no posee una interrupción pendiente de resolución.");
    }
    if (resume && !preparationStillValid) {
      return conflict(
          "La preparación venció durante la interrupción; debe descartarse y repetirse.",
          "PREPARATION_EXPIRED");
    }
    return Optional.empty();
  }

  public static boolean medicationReady(State state) {
    return switch (state.medicationSource()) {
      case "center_stock" -> "reserved".equals(state.stockStatus());
      case "patient_has_medication", "received_center" -> true;
      default -> false;
    };
  }

  private static Optional<Violation> unfinished(State state) {
    if ("completed".equals(state.administrationStatus())
        || "completed".equals(state.workflowStatus())) {
      return conflict("La aplicación ya fue completada y es inmutable.");
    }
    return Optional.empty();
  }

  private static Optional<Violation> beforePreparation(State state) {
    if (!Set.of("not_started", "cancelled").contains(state.preparationStatus())
        || !Set.of("not_started", "withheld").contains(state.administrationStatus())) {
      return conflict("La etapa ya avanzó y no puede retrocederse desde esta acción.");
    }
    return Optional.empty();
  }

  private static Optional<Violation> beforeClinicalPass(State state) {
    if ("passed".equals(state.clinicalStatus())) {
      return conflict(
          "El triaje ya fue aprobado; no puede cambiar Farmacia sin postergar y reevaluar la aplicación.");
    }
    return Optional.empty();
  }

  private static Optional<Violation> conflict(String message) {
    return Optional.of(new Violation(Violation.Type.CONFLICT, message, "INVALID_APPLICATION_TRANSITION"));
  }

  private static Optional<Violation> conflict(String message, String code) {
    return Optional.of(new Violation(Violation.Type.CONFLICT, message, code));
  }

  private static Optional<Violation> invalid(String message) {
    return Optional.of(new Violation(Violation.Type.INVALID, message, null));
  }

  public record State(
      String workflowStatus,
      String prescriptionStatus,
      String medicationSource,
      String pharmacyValidationStatus,
      String stockStatus,
      String clinicalStatus,
      String preparationStatus,
      String administrationStatus) {
  }

  public record Violation(Type type, String message, String code) {
    public enum Type { INVALID, CONFLICT }
  }
}
