package ar.com.hexium.hcop.infusion.application.port.out;

import ar.com.hexium.hcop.infusion.domain.Infusion;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Único adapter real: {@code infrastructure.persistence.PostgresInfusionOperationsStore} — mismo
 * criterio "sin lógica propia" que {@link ApplicationWorkflowStore}.
 */
public interface InfusionOperationsStore {

  List<Map<String, Object>> list(Long patientId, LocalDate date);

  List<Map<String, Object>> candidates(String query, boolean includeScheduled, boolean onlySchedulingEligible);

  Map<String, Object> create(Object input, long actorId, String actorDisplayName);

  Map<String, Object> update(long id, Object input, long actorId, String actorDisplayName);

  Map<String, Object> updateLogistics(
      long patientId, String treatmentId, int cycleNumber, int applicationDay, Object input,
      long actorId, String actorDisplayName);

  Map<String, Object> view(Infusion infusion);
}
