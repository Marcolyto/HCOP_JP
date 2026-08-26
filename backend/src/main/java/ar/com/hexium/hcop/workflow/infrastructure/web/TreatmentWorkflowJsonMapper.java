package ar.com.hexium.hcop.workflow.infrastructure.web;

import ar.com.hexium.hcop.workflow.domain.ManagementState;
import ar.com.hexium.hcop.workflow.domain.WorkflowRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TreatmentWorkflowJsonMapper {

  public Map<String, Object> stateView(ManagementState state) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("patientId", Long.toString(state.patientId()));
    result.put("treatmentId", state.treatmentId());
    result.put("continuityStatus", state.status());
    result.put("workflowStatus", state.status());
    result.put("effectiveFromCycle", state.effectiveFromCycle());
    result.put("suspensionReason", state.reason());
    result.put("resumeDate", state.resumeDate() == null ? null : state.resumeDate().toString());
    result.put("prescriptionRequired", state.prescriptionRequired());
    result.put("revision", state.revision());
    return result;
  }

  public Map<String, Object> requestView(WorkflowRequest item) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", Long.toString(item.id()));
    result.put("type", item.type());
    result.put("requestType", item.type());
    result.put("status", item.status());
    result.put("patientId", Long.toString(item.patientId()));
    result.put("treatmentId", item.treatmentId());
    result.put("cycleNumber", item.cycleNumber());
    result.put("message", item.message());
    result.put("context", item.context());
    result.put("resolution", item.resolution());
    result.put("resolutionReason", item.resolutionReason());
    result.put("resumeDate", item.resumeDate() == null ? null : item.resumeDate().toString());
    result.put("seen", item.seenAt() != null);
    result.put("seenAt", item.seenAt() == null ? null : item.seenAt().toString());
    result.put("createdAt", item.createdAt().toString());
    result.put("patientName", item.patientName());
    result.put("patientDni", item.patientDni());
    result.put("scheme", item.scheme());
    result.put("diagnosis", item.diagnosis());
    result.put("requestedByDisplayName", item.requestedByName());
    result.put("assignedToDisplayName", item.assignedToName());
    return result;
  }

  public Map<String, Object> actionResult(Object item, Object evolution, long documentRevision) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("ok", true);
    result.put("item", item);
    result.put("evolution", evolution);
    result.put("documentRevision", documentRevision);
    return result;
  }
}
