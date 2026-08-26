package ar.com.hexium.hcop.infusion.application.service;

import ar.com.hexium.hcop.infusion.application.port.in.ApplicationWorkflowUseCase;
import ar.com.hexium.hcop.infusion.application.port.out.ApplicationWorkflowStore;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Sin lógica propia — {@link ApplicationWorkflowStore} ya resuelve todo (ver su javadoc). */
public final class ApplicationWorkflowApplicationService implements ApplicationWorkflowUseCase {
  private final ApplicationWorkflowStore store;

  public ApplicationWorkflowApplicationService(ApplicationWorkflowStore store) {
    this.store = store;
  }

  @Override
  public List<Map<String, Object>> list(String queue, LocalDate date, String query, String medicationSource) {
    return store.list(queue, date, query, medicationSource);
  }

  @Override
  public Map<String, Object> get(long patientId, String treatmentId, int cycle, int day) {
    return store.get(patientId, treatmentId, cycle, day);
  }

  @Override
  public String preparationLabel(long patientId, String treatmentId, int cycle, int day) {
    return store.preparationLabel(patientId, treatmentId, cycle, day);
  }

  @Override
  public CommandResult pharmacyValidation(
      long patientId, String treatmentId, int cycle, int day, PharmacyValidationCommand command,
      long actorId, String actorDisplayName) {
    return store.pharmacyValidation(patientId, treatmentId, cycle, day, command, actorId, actorDisplayName);
  }

  @Override
  public CommandResult stockReservation(
      long patientId, String treatmentId, int cycle, int day, StockReservationCommand command,
      long actorId, String actorDisplayName) {
    return store.stockReservation(patientId, treatmentId, cycle, day, command, actorId, actorDisplayName);
  }

  @Override
  public CommandResult clinicalAuthorization(
      long patientId, String treatmentId, int cycle, int day, ClinicalAuthorizationCommand command,
      long actorId, String actorDisplayName) {
    return store.clinicalAuthorization(patientId, treatmentId, cycle, day, command, actorId, actorDisplayName);
  }

  @Override
  public CommandResult preparationStart(
      long patientId, String treatmentId, int cycle, int day, BasicCommand command,
      long actorId, String actorDisplayName) {
    return store.preparationStart(patientId, treatmentId, cycle, day, command, actorId, actorDisplayName);
  }

  @Override
  public CommandResult preparationComplete(
      long patientId, String treatmentId, int cycle, int day, PreparationCompleteCommand command,
      long actorId, String actorDisplayName) {
    return store.preparationComplete(patientId, treatmentId, cycle, day, command, actorId, actorDisplayName);
  }

  @Override
  public CommandResult preparationRelease(
      long patientId, String treatmentId, int cycle, int day, BasicCommand command,
      long actorId, String actorDisplayName) {
    return store.preparationRelease(patientId, treatmentId, cycle, day, command, actorId, actorDisplayName);
  }

  @Override
  public CommandResult preparationRestart(
      long patientId, String treatmentId, int cycle, int day, BasicCommand command,
      long actorId, String actorDisplayName) {
    return store.preparationRestart(patientId, treatmentId, cycle, day, command, actorId, actorDisplayName);
  }

  @Override
  public CommandResult administrationStart(
      long patientId, String treatmentId, int cycle, int day, AdministrationStartCommand command,
      long actorId, String actorDisplayName) {
    return store.administrationStart(patientId, treatmentId, cycle, day, command, actorId, actorDisplayName);
  }

  @Override
  public CommandResult administrationComplete(
      long patientId, String treatmentId, int cycle, int day, AdministrationCompleteCommand command,
      long actorId, String actorDisplayName) {
    return store.administrationComplete(patientId, treatmentId, cycle, day, command, actorId, actorDisplayName);
  }

  @Override
  public CommandResult administrationInterrupt(
      long patientId, String treatmentId, int cycle, int day, AdministrationInterruptCommand command,
      long actorId, String actorDisplayName) {
    return store.administrationInterrupt(patientId, treatmentId, cycle, day, command, actorId, actorDisplayName);
  }

  @Override
  public CommandResult administrationResolve(
      long patientId, String treatmentId, int cycle, int day, AdministrationResolveCommand command,
      long actorId, String actorDisplayName) {
    return store.administrationResolve(patientId, treatmentId, cycle, day, command, actorId, actorDisplayName);
  }
}
