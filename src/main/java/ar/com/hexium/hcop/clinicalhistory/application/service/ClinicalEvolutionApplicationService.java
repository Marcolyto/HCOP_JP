package ar.com.hexium.hcop.clinicalhistory.application.service;

import ar.com.hexium.hcop.clinicalhistory.application.port.in.ClinicalEvolutionUseCase;
import ar.com.hexium.hcop.clinicalhistory.application.port.out.ClinicalEvolutionPort;
import java.time.Clock;

/** Regla de agregado inmutable sin dependencia de HTTP, JSON o JDBC. */
public final class ClinicalEvolutionApplicationService implements ClinicalEvolutionUseCase {
  private final ClinicalEvolutionPort store;
  private final Clock clock;

  public ClinicalEvolutionApplicationService(ClinicalEvolutionPort store, Clock clock) {
    this.store = store;
    this.clock = clock;
  }

  @Override
  public AppendResult append(AppendCommand command) {
    if (command.patientId() < 1) throw new ClinicalEvolutionFailure("Paciente inválido.");
    if (command.immutableEvolutionJson() == null || command.immutableEvolutionJson().isBlank()) {
      throw new ClinicalEvolutionFailure("La evolución no puede estar vacía.");
    }
    long revision = store.append(
        command.patientId(), command.evolutionId(), command.immutableEvolutionJson(),
        command.actorId(), clock.instant()).orElseThrow(() ->
            new ClinicalEvolutionFailure("La historia clínica no está disponible."));
    return new AppendResult(revision);
  }

  public static final class ClinicalEvolutionFailure extends RuntimeException {
    public ClinicalEvolutionFailure(String message) {
      super(message);
    }
  }
}
