package ar.com.hexium.hcop.patientcontext.application.service;

import ar.com.hexium.hcop.patientcontext.application.port.in.ActivePatientContextUseCase;
import ar.com.hexium.hcop.patientcontext.application.port.out.PatientContextPatientPort;
import ar.com.hexium.hcop.patientcontext.application.port.out.SessionActivePatientPort;
import ar.com.hexium.hcop.patientcontext.domain.ActivePatientId;
import java.time.Clock;

/** Caso de uso puro: abrir un paciente, reemplazarlo o limpiar el contexto de la sesión. */
public final class ActivePatientContextApplicationService implements ActivePatientContextUseCase {
  private final PatientContextPatientPort patients;
  private final SessionActivePatientPort sessions;
  private final Clock clock;

  public ActivePatientContextApplicationService(
      PatientContextPatientPort patients,
      SessionActivePatientPort sessions,
      Clock clock) {
    this.patients = patients;
    this.sessions = sessions;
    this.clock = clock;
  }

  @Override
  public void select(SelectCommand command) {
    if (command == null || command.sessionToken() == null || command.sessionToken().isBlank()) {
      throw new ActivePatientContextFailure(
          ActivePatientContextFailure.Reason.INVALID_SESSION,
          "No se encontró una sesión válida para actualizar el paciente activo.");
    }
    ActivePatientId patientId = command.patientId() == null ? null : new ActivePatientId(command.patientId());
    if (patientId != null && !patients.exists(patientId)) {
      throw new ActivePatientContextFailure(
          ActivePatientContextFailure.Reason.PATIENT_NOT_FOUND,
          "Paciente no encontrado.");
    }
    sessions.assign(command.sessionToken(), patientId, clock.instant());
  }
}
