package ar.com.hexium.hcop.patient.application.service;

import ar.com.hexium.hcop.patient.application.port.in.PatientCreationUseCase;
import ar.com.hexium.hcop.patient.application.port.in.PatientCreationUseCase.NewPatientData;
import ar.com.hexium.hcop.patient.application.port.in.PatientCreationUseCase.PatientProfile;
import ar.com.hexium.hcop.patient.application.port.out.PatientCreationStorePort;
import java.time.Clock;
import java.time.LocalDate;

/** Regla de alta sin dependencia de Spring, JDBC ni controlador. */
public final class PatientCreationApplicationService implements PatientCreationUseCase {
  private final PatientCreationStorePort store;
  private final Clock clock;

  public PatientCreationApplicationService(PatientCreationStorePort store, Clock clock) {
    this.store = store;
    this.clock = clock;
  }

  @Override
  public Creation create(NewPatientData input) {
    validate(input);
    store.findDuplicate(input.dni(), input.medicalRecord()).ifPresent(existing -> {
      throw new PatientCreationFailure(Reason.DUPLICATE_PATIENT,
          "Ya existe un paciente con ese DNI o historia clínica.", existing);
    });
    return new Creation(store.insert(input));
  }

  private void validate(NewPatientData input) {
    if (input.firstName() == null || input.firstName().isBlank()) fail("El nombre es obligatorio.");
    if (input.lastName() == null || input.lastName().isBlank()) fail("El apellido es obligatorio.");
    if ((input.dni() == null || input.dni().isBlank())
        && (input.medicalRecord() == null || input.medicalRecord().isBlank())) {
      fail("Informe el DNI o la historia clínica.");
    }
    if (input.birthDate() != null && input.birthDate().isAfter(LocalDate.now(clock))) {
      fail("La fecha de nacimiento no puede ser futura.");
    }
  }

  private void fail(String message) {
    throw new PatientCreationFailure(Reason.INVALID_INPUT, message, null);
  }

  public enum Reason { INVALID_INPUT, DUPLICATE_PATIENT }

  public static final class PatientCreationFailure extends RuntimeException {
    private final Reason reason;
    private final PatientProfile duplicate;

    public PatientCreationFailure(Reason reason, String message, PatientProfile duplicate) {
      super(message);
      this.reason = reason;
      this.duplicate = duplicate;
    }

    public Reason reason() { return reason; }

    public PatientProfile duplicate() { return duplicate; }
  }
}
