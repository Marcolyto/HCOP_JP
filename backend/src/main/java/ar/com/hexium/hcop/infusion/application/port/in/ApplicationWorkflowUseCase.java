package ar.com.hexium.hcop.infusion.application.port.in;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Circuito auditable por aplicación (Farmacia, triaje, preparación, administración). Los
 * comandos anidados son la versión sin {@code @Schema}/{@code JsonNode} de
 * {@code infrastructure.web.ApplicationWorkflowCommands} — el borde web mapea 1:1 desde el
 * cuerpo JSON anotado hacia estos records planos, mismo criterio que
 * {@code treatment.application.port.in.TreatmentUseCase.CreateTreatmentCommand}. Los 3 campos
 * clínicos de {@code ClinicalAuthorizationCommand} quedan {@code Object} opaco (siguen siendo el
 * {@code JsonNode} real en runtime) porque el dominio nunca los interpreta campo a campo, solo
 * los transporta hasta el adapter de persistencia.
 */
public interface ApplicationWorkflowUseCase {

  List<Map<String, Object>> list(String queue, LocalDate date, String query, String medicationSource);

  Map<String, Object> get(long patientId, String treatmentId, int cycle, int day);

  String preparationLabel(long patientId, String treatmentId, int cycle, int day);

  CommandResult pharmacyValidation(
      long patientId, String treatmentId, int cycle, int day, PharmacyValidationCommand command,
      long actorId, String actorDisplayName);

  CommandResult stockReservation(
      long patientId, String treatmentId, int cycle, int day, StockReservationCommand command,
      long actorId, String actorDisplayName);

  CommandResult clinicalAuthorization(
      long patientId, String treatmentId, int cycle, int day, ClinicalAuthorizationCommand command,
      long actorId, String actorDisplayName);

  CommandResult preparationStart(
      long patientId, String treatmentId, int cycle, int day, BasicCommand command,
      long actorId, String actorDisplayName);

  CommandResult preparationComplete(
      long patientId, String treatmentId, int cycle, int day, PreparationCompleteCommand command,
      long actorId, String actorDisplayName);

  CommandResult preparationRelease(
      long patientId, String treatmentId, int cycle, int day, BasicCommand command,
      long actorId, String actorDisplayName);

  CommandResult preparationRestart(
      long patientId, String treatmentId, int cycle, int day, BasicCommand command,
      long actorId, String actorDisplayName);

  CommandResult administrationStart(
      long patientId, String treatmentId, int cycle, int day, AdministrationStartCommand command,
      long actorId, String actorDisplayName);

  CommandResult administrationComplete(
      long patientId, String treatmentId, int cycle, int day, AdministrationCompleteCommand command,
      long actorId, String actorDisplayName);

  CommandResult administrationInterrupt(
      long patientId, String treatmentId, int cycle, int day, AdministrationInterruptCommand command,
      long actorId, String actorDisplayName);

  CommandResult administrationResolve(
      long patientId, String treatmentId, int cycle, int day, AdministrationResolveCommand command,
      long actorId, String actorDisplayName);

  record CommandResult(Object workflow, boolean idempotentReplay, Object evolution, Long documentRevision) {
  }

  record PharmacyValidationCommand(
      Long expectedRevision, String idempotencyKey, Boolean validated, String medicationSource,
      String notes) {
  }

  record StockReservationCommand(
      Long expectedRevision, String idempotencyKey, Boolean reserved, String medicationSource,
      String verificationMethod, String notes, List<StockComponentInput> components) {
  }

  record StockComponentInput(
      String componentKey, String drugId, String drugName, BigDecimal requestedQuantity,
      String requestedQuantityText, String unit, Long inventoryLotId) {
  }

  /** {@code laboratory}/{@code vitalSigns}/{@code toxicity} son el árbol JSON opaco del body. */
  record ClinicalAuthorizationCommand(
      Long expectedRevision, String idempotencyKey, String decision, Object laboratory,
      Object vitalSigns, Object toxicity, String reason, LocalDate rescheduledDate) {
  }

  record BasicCommand(Long expectedRevision, String idempotencyKey, String notes) {
  }

  record PreparationCompleteCommand(
      Long expectedRevision, String idempotencyKey, String verifiedBy,
      List<PreparationInput> preparations, String notes) {
  }

  record PreparationInput(
      String componentKey, String drugName, String lot, LocalDate expiryDate, BigDecimal quantity,
      String quantityText, String unit, String diluent, String finalVolume, String concentration,
      Integer ttlMinutes, String reservationId, Long inventoryLotId) {
  }

  record AdministrationStartCommand(
      Long expectedRevision, String idempotencyKey, Boolean patientVerified, Boolean labelVerified,
      String doubleCheckBy, Instant startedAt, String notes) {
  }

  record AdministrationCompleteCommand(
      Long expectedRevision, String idempotencyKey, Instant completedAt, String actualDose,
      Boolean reactionOccurred, String reactionDescription, String observation) {
  }

  record AdministrationInterruptCommand(
      Long expectedRevision, String idempotencyKey, Instant interruptedAt, String reason,
      String actualDose, String measures, String patientCondition, String disposition) {
  }

  record AdministrationResolveCommand(
      Long expectedRevision, String idempotencyKey, Instant resolvedAt, String decision,
      String notes, String actualDose, String patientCondition) {
  }
}
