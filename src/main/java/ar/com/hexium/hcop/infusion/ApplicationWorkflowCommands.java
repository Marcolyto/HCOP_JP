package ar.com.hexium.hcop.infusion;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import tools.jackson.databind.JsonNode;

final class ApplicationWorkflowCommands {
  private ApplicationWorkflowCommands() {
  }

  @Schema(name = "PharmacyValidationCommand")
  record PharmacyValidation(
      @Schema(example = "3") Long expectedRevision,
      @Schema(example = "pharmacy-validation-8001-trt-1-1") String idempotencyKey,
      @Schema(description = "true aprueba la auditoría farmacéutica; false la rechaza.") Boolean validated,
      @Schema(
          allowableValues = {
              "center_stock", "patient_to_bring", "patient_has_medication",
              "received_center", "pending_supplier"
          },
          example = "center_stock")
      String medicationSource,
      @Schema(example = "Dosis, vía, intervalo y premedicación verificados.") String notes) {
  }

  @Schema(name = "StockReservationCommand")
  record StockReservation(
      @Schema(example = "4") Long expectedRevision,
      @Schema(example = "stock-lock-8001-trt-1-1") String idempotencyKey,
      @Schema(description = "true reserva; false libera la reserva activa.") Boolean reserved,
      @Schema(
          allowableValues = {
              "center_stock", "patient_to_bring", "patient_has_medication",
              "received_center", "pending_supplier"
          },
          example = "center_stock")
      String medicationSource,
      @Schema(
          description = "inventory usa stock cuantificado; manual exige una constatación explícita.",
          allowableValues = {"inventory", "manual"},
          example = "manual")
      String verificationMethod,
      @Schema(example = "Disponibilidad constatada físicamente por Farmacia.") String notes,
      List<StockComponent> components) {
  }

  @Schema(name = "StockReservationComponent")
  record StockComponent(
      String componentKey,
      String drugId,
      @Schema(example = "Irinotecan") String drugName,
      BigDecimal requestedQuantity,
      @Schema(example = "250 mg") String requestedQuantityText,
      @Schema(example = "mg") String unit,
      @Schema(description = "Lote del inventario; obligatorio para verificación inventory.") Long inventoryLotId) {
  }

  @Schema(name = "ClinicalAuthorizationCommand")
  record ClinicalAuthorization(
      @Schema(example = "5") Long expectedRevision,
      @Schema(example = "triage-8001-trt-1-1-20260730") String idempotencyKey,
      @Schema(allowableValues = {"PASS", "FAIL"}, example = "PASS") String decision,
      @Schema(description = "Hemograma, función renal/hepática y fecha de extracción.") JsonNode laboratory,
      @Schema(description = "Signos vitales y peso del día.") JsonNode vitalSigns,
      @Schema(description = "Toxicidades y estado funcional evaluados.") JsonNode toxicity,
      @Schema(
          description = "Obligatorio para FAIL y para justificar un PASS con alertas clínicas.")
      String reason,
      @Schema(description = "Nueva fecha sugerida cuando se posterga.") LocalDate rescheduledDate) {
  }

  @Schema(name = "BasicApplicationWorkflowCommand")
  record Basic(
      Long expectedRevision,
      String idempotencyKey,
      String notes) {
  }

  @Schema(name = "PreparationCompleteCommand")
  record PreparationComplete(
      Long expectedRevision,
      String idempotencyKey,
      @Schema(description = "ID o usuario del segundo profesional que verifica la mezcla.")
      String verifiedBy,
      List<Preparation> preparations,
      String notes) {
  }

  @Schema(name = "PreparationTrace")
  record Preparation(
      @Schema(
          description = """
              Clave canónica del componente prescripto. Para clientes nuevos es obligatoria;
              el servidor sólo la infiere en solicitudes legacy cuando puede resolverla sin
              perder la correspondencia uno a uno.
              """)
      String componentKey,
      String drugName,
      String lot,
      LocalDate expiryDate,
      BigDecimal quantity,
      String quantityText,
      String unit,
      String diluent,
      String finalVolume,
      String concentration,
      @Schema(minimum = "1", maximum = "10080", example = "240") Integer ttlMinutes,
      @Schema(description = "Reserva por componente que se consume, si corresponde.") String reservationId,
      @Schema(description = "Lote del inventario que respalda la preparación, si corresponde.") Long inventoryLotId) {
  }

  @Schema(name = "AdministrationStartCommand")
  record AdministrationStart(
      Long expectedRevision,
      String idempotencyKey,
      @Schema(description = "Confirmación positiva de identidad del paciente.") Boolean patientVerified,
      @Schema(description = "Confirmación de coincidencia con la etiqueta/QR.") Boolean labelVerified,
      @Schema(description = "ID o usuario del segundo profesional; debe ser distinto del actor.") String doubleCheckBy,
      Instant startedAt,
      String notes) {
  }

  @Schema(name = "AdministrationCompleteCommand")
  record AdministrationComplete(
      Long expectedRevision,
      String idempotencyKey,
      Instant completedAt,
      String actualDose,
      Boolean reactionOccurred,
      String reactionDescription,
      String observation) {
  }

  @Schema(name = "AdministrationInterruptCommand")
  record AdministrationInterrupt(
      Long expectedRevision,
      String idempotencyKey,
      Instant interruptedAt,
      @Schema(description = "Causa clínica u operativa de la interrupción.") String reason,
      @Schema(description = "Dosis administrada hasta el momento de detenerse.") String actualDose,
      @Schema(description = "Medidas adoptadas inmediatamente.") String measures,
      @Schema(description = "Condición del paciente tras la intervención.") String patientCondition,
      @Schema(
          allowableValues = {"observation", "medical_review", "emergency_transfer"},
          example = "observation")
      String disposition) {
  }

  @Schema(name = "AdministrationResolveCommand")
  record AdministrationResolve(
      Long expectedRevision,
      String idempotencyKey,
      Instant resolvedAt,
      @Schema(allowableValues = {"resume", "terminate"}, example = "resume")
      String decision,
      @Schema(description = "Decisión y condiciones para reanudar o cerrar.") String notes,
      @Schema(description = "Dosis total administrada si se cierra definitivamente.") String actualDose,
      @Schema(description = "Condición del paciente al resolver la interrupción.") String patientCondition) {
  }
}
