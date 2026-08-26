package ar.com.hexium.hcop.infusion.application.port.in;

import ar.com.hexium.hcop.infusion.domain.Infusion;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Turnos de Hospital de día (fuera del circuito auditable por aplicación, ver
 * {@link ApplicationWorkflowUseCase}). {@code input} en {@code create}/{@code update}/
 * {@code updateLogistics} es el árbol JSON opaco del body — el adapter de persistencia hace
 * TODA la extracción/validación de campos (mismo criterio que {@code treatment.TreatmentUseCase}
 * con el detalle del tratamiento).
 */
public interface InfusionUseCase {

  List<Map<String, Object>> list(Long patientId, LocalDate date);

  List<Map<String, Object>> candidates(String query, boolean includeScheduled, boolean onlySchedulingEligible);

  Map<String, Object> create(Object input, long actorId, String actorDisplayName);

  Map<String, Object> update(long id, Object input, long actorId, String actorDisplayName);

  Map<String, Object> updateLogistics(
      long patientId, String treatmentId, int cycleNumber, int applicationDay, Object input,
      long actorId, String actorDisplayName);

  Map<String, Object> view(Infusion infusion);
}
