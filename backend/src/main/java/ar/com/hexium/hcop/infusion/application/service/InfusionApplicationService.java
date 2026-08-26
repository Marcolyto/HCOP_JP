package ar.com.hexium.hcop.infusion.application.service;

import ar.com.hexium.hcop.infusion.application.port.in.InfusionUseCase;
import ar.com.hexium.hcop.infusion.application.port.out.InfusionOperationsStore;
import ar.com.hexium.hcop.infusion.domain.Infusion;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Sin lógica propia — ver {@link InfusionOperationsStore}. */
public final class InfusionApplicationService implements InfusionUseCase {
  private final InfusionOperationsStore store;

  public InfusionApplicationService(InfusionOperationsStore store) {
    this.store = store;
  }

  @Override
  public List<Map<String, Object>> list(Long patientId, LocalDate date) {
    return store.list(patientId, date);
  }

  @Override
  public List<Map<String, Object>> candidates(
      String query, boolean includeScheduled, boolean onlySchedulingEligible) {
    return store.candidates(query, includeScheduled, onlySchedulingEligible);
  }

  @Override
  public Map<String, Object> create(Object input, long actorId, String actorDisplayName) {
    return store.create(input, actorId, actorDisplayName);
  }

  @Override
  public Map<String, Object> update(long id, Object input, long actorId, String actorDisplayName) {
    return store.update(id, input, actorId, actorDisplayName);
  }

  @Override
  public Map<String, Object> updateLogistics(
      long patientId, String treatmentId, int cycleNumber, int applicationDay, Object input,
      long actorId, String actorDisplayName) {
    return store.updateLogistics(patientId, treatmentId, cycleNumber, applicationDay, input, actorId, actorDisplayName);
  }

  @Override
  public Map<String, Object> view(Infusion infusion) {
    return store.view(infusion);
  }
}
