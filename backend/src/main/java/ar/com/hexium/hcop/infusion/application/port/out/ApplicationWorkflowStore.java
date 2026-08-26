package ar.com.hexium.hcop.infusion.application.port.out;

import ar.com.hexium.hcop.infusion.application.port.in.ApplicationWorkflowUseCase.AdministrationCompleteCommand;
import ar.com.hexium.hcop.infusion.application.port.in.ApplicationWorkflowUseCase.AdministrationInterruptCommand;
import ar.com.hexium.hcop.infusion.application.port.in.ApplicationWorkflowUseCase.AdministrationResolveCommand;
import ar.com.hexium.hcop.infusion.application.port.in.ApplicationWorkflowUseCase.AdministrationStartCommand;
import ar.com.hexium.hcop.infusion.application.port.in.ApplicationWorkflowUseCase.BasicCommand;
import ar.com.hexium.hcop.infusion.application.port.in.ApplicationWorkflowUseCase.ClinicalAuthorizationCommand;
import ar.com.hexium.hcop.infusion.application.port.in.ApplicationWorkflowUseCase.CommandResult;
import ar.com.hexium.hcop.infusion.application.port.in.ApplicationWorkflowUseCase.PharmacyValidationCommand;
import ar.com.hexium.hcop.infusion.application.port.in.ApplicationWorkflowUseCase.PreparationCompleteCommand;
import ar.com.hexium.hcop.infusion.application.port.in.ApplicationWorkflowUseCase.StockReservationCommand;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Único adapter real: {@code infrastructure.persistence.PostgresApplicationWorkflowStore} — ahí
 * vive toda la lógica de negocio (validaciones vía {@code domain.ApplicationWorkflowPolicy},
 * construcción de JSON, SQL). Este puerto tiene el mismo shape que
 * {@code ApplicationWorkflowUseCase} a propósito: la aplicación no agrega nada por sí misma
 * (mismo patrón que {@code tools.CalculatorCatalogApplicationService}).
 */
public interface ApplicationWorkflowStore {

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
}
