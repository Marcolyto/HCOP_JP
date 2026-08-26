package ar.com.hexium.hcop.workflow.application.port.in;

import ar.com.hexium.hcop.workflow.domain.ManagementState;
import ar.com.hexium.hcop.workflow.domain.WorkflowRequest;
import java.util.List;

public interface TreatmentWorkflowUseCase {

  ManagementActionResult suspend(SuspendCommand command);

  ManagementActionResult resume(ResumeCommand command);

  RequestActionResult createRequest(CreateRequestCommand command);

  List<WorkflowRequest> inbox(long actorUserId);

  WorkflowRequest seen(long id, long actorUserId);

  RequestActionResult resolveRequest(ResolveCommand command);

  /** {@code -1} en {@code cycleNumber} significa "no especificado" — el mismo sentinel que
   * antes resolvía {@code JsonNode.asInt(default)} en el borde web. */
  record SuspendCommand(
      long patientId, String treatmentId, String kind, String reason, int cycleNumber,
      String resumeDateRaw, long actorId, String actorDisplayName) {
  }

  record ResumeCommand(
      long patientId, String treatmentId, String reason, long actorId, String actorDisplayName) {
  }

  /** {@code patientIdRaw}/{@code assignedToUserIdRaw} llegan como texto crudo del body — el
   * mismo criterio que la validación original ({@code Long.parseLong} + rango, 400 si falla). */
  record CreateRequestCommand(
      String type, String patientIdRaw, String treatmentId, int cycleNumber,
      String assignedToUserIdRaw, String message, long actorId, String actorDisplayName) {
  }

  record ResolveCommand(
      long requestId, String resolution, String reason, String resumeDateRaw, long actorId,
      String actorDisplayName, PermissionChecker permission) {
  }

  record ManagementActionResult(ManagementState state, Object evolution, long documentRevision) {
  }

  record RequestActionResult(WorkflowRequest request, Object evolution, long documentRevision) {
  }

  /** Evita que la aplicación conozca {@code auth.SessionPrincipal} — el borde web pasa
   * {@code principal::hasPermission} como referencia de método. */
  interface PermissionChecker {
    boolean hasPermission(String permission);
  }
}
