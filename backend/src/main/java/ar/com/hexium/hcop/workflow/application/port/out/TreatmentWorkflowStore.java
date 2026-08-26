package ar.com.hexium.hcop.workflow.application.port.out;

import ar.com.hexium.hcop.workflow.domain.ManagementState;
import ar.com.hexium.hcop.workflow.domain.TreatmentWorkflowSummary;
import ar.com.hexium.hcop.workflow.domain.WorkflowRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface TreatmentWorkflowStore {

  boolean treatmentExists(long patientId, String treatmentId);

  Optional<TreatmentWorkflowSummary> treatment(long patientId, String treatmentId);

  ManagementState upsertManagement(
      long patientId, String treatmentId, String status, Integer cycle, String reason,
      LocalDate resumeDate, boolean prescriptionRequired, long actorId);

  Optional<ManagementState> management(long patientId, String treatmentId);

  /**
   * @throws DuplicateRequestException si ya existe una solicitud pendiente para el mismo
   *     tratamiento y ciclo — traducido por el adapter desde {@code DataIntegrityViolationException}.
   */
  long insertRequest(
      String type, long patientId, String treatmentId, int cycle, long requestedBy,
      long assignedTo, String message, Map<String, String> context);

  Optional<WorkflowRequest> request(long id);

  List<WorkflowRequest> inbox(long userId);

  Optional<WorkflowRequest> markSeen(long id, long userId, Instant now);

  Optional<WorkflowRequest> resolve(
      long id, long assignedUserId, String resolution, String reason, LocalDate resumeDate,
      Instant now);

  void updatePrescriptionState(long patientId, String treatmentId, int cycle, String state, long actorId);

  String prescriptionState(long patientId, String treatmentId, int cycle);

  /** {@code event} es el mismo árbol de evolución opaco que devuelve {@code PatientEvolutionPort}. */
  void insertEvent(
      Long requestId, long patientId, String treatmentId, Integer cycle, String eventType,
      long actorId, Object event);

  final class DuplicateRequestException extends RuntimeException {
    public DuplicateRequestException(String message) {
      super(message);
    }
  }
}
