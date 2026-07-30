package ar.com.hexium.hcop.workflow;

import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.patient.PatientDocumentService;
import ar.com.hexium.hcop.patient.PatientDocumentService.EvolutionAppend;
import ar.com.hexium.hcop.workflow.TreatmentWorkflowRepository.ManagementState;
import ar.com.hexium.hcop.workflow.TreatmentWorkflowRepository.Request;
import ar.com.hexium.hcop.workflow.TreatmentWorkflowRepository.TreatmentSummary;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class TreatmentWorkflowService {
  private final TreatmentWorkflowRepository workflows;
  private final PatientDocumentService documents;
  private final ObjectMapper mapper;
  private final Clock clock;

  public TreatmentWorkflowService(
      TreatmentWorkflowRepository workflows,
      PatientDocumentService documents,
      ObjectMapper mapper,
      Clock clock) {
    this.workflows = workflows;
    this.documents = documents;
    this.mapper = mapper;
    this.clock = clock;
  }

  @Transactional
  public Map<String, Object> suspend(
      long patientId, String treatmentId, JsonNode body, SessionPrincipal actor) {
    TreatmentSummary treatment = requireTreatment(patientId, treatmentId);
    String kind = text(body, "kind");
    String status = "definitive".equals(kind) ? "discontinued" : "temporary_hold";
    String reason = text(body, "reason");
    if (reason.length() < 3) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Indique el motivo de la suspensión.");
    }
    int cycle = treatmentCycle(treatment, body.path("cycleNumber").asInt(treatment.initialCycle()));
    LocalDate resumeDate = "temporary_hold".equals(status) ? date(body, "resumeDate") : null;
    ManagementState state = workflows.upsertManagement(
        patientId, treatmentId, status, cycle, reason, resumeDate, true, actor.userId());
    workflows.updatePrescriptionState(patientId, treatmentId, cycle, "required", actor.userId());
    String label = "discontinued".equals(status) ? "Suspensión definitiva" : "Suspensión transitoria";
    return evolutionResult(
        treatment, actor, "treatment-" + status, label,
        label + " del tratamiento.\nEsquema: " + treatment.scheme() +
            "\nCiclo: " + cycle + "\nMotivo: " + reason +
            (resumeDate == null ? "" : "\nFecha prevista de revisión: " + resumeDate),
        null, cycle, stateView(state));
  }

  @Transactional
  public Map<String, Object> resume(
      long patientId, String treatmentId, JsonNode body, SessionPrincipal actor) {
    TreatmentSummary treatment = requireTreatment(patientId, treatmentId);
    ManagementState current = workflows.management(patientId, treatmentId)
        .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "El tratamiento no está suspendido."));
    if ("discontinued".equals(current.status())) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "Una suspensión definitiva requiere una nueva indicación clínica; no se reactiva como si fuera transitoria.");
    }
    if (!"temporary_hold".equals(current.status())) {
      throw new ApiException(HttpStatus.CONFLICT, "El tratamiento ya está activo.");
    }
    if (!"confirmed".equals(
        workflows.prescriptionState(patientId, treatmentId, current.effectiveFromCycle()))) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "Confirme primero la nueva prescripción del ciclo antes de reanudar el tratamiento.");
    }
    String reason = text(body, "reason");
    if (reason.length() < 3) throw new ApiException(HttpStatus.BAD_REQUEST, "Indique el motivo para reanudar.");
    ManagementState state = workflows.upsertManagement(
        patientId, treatmentId, "active", current.effectiveFromCycle(), reason, null, false, actor.userId());
    return evolutionResult(
        treatment, actor, "treatment-resumed", "Tratamiento reanudado",
        "Tratamiento reanudado.\nEsquema: " + treatment.scheme() +
            "\nDesde ciclo: " + current.effectiveFromCycle() + "\nFundamento: " + reason,
        null, current.effectiveFromCycle(), stateView(state));
  }

  @Transactional
  public Map<String, Object> createRequest(JsonNode body, SessionPrincipal actor) {
    String type = text(body, "type");
    if (!Set.of("prescription_request", "continuity_request").contains(type)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Tipo de solicitud inválido.");
    }
    long patientId = positiveLong(body, "patientId");
    String treatmentId = text(body, "treatmentId");
    int requestedCycle = boundedCycle(body.path("cycleNumber").asInt(1));
    long assignedTo = positiveLong(body, "assignedToUserId");
    String message = text(body, "message");
    TreatmentSummary treatment = requireTreatment(patientId, treatmentId);
    int cycle = treatmentCycle(treatment, requestedCycle);
    ObjectNode context = mapper.createObjectNode();
    context.put("patientName", treatment.patientName());
    context.put("patientDni", treatment.patientDni());
    context.put("scheme", treatment.scheme());
    context.put("diagnosis", treatment.diagnosis());
    long requestId;
    try {
      requestId = workflows.insertRequest(
          type, patientId, treatmentId, cycle, actor.userId(), assignedTo, message, context);
    } catch (DataIntegrityViolationException duplicate) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "Ya existe una solicitud pendiente para este tratamiento y ciclo.");
    }
    if ("prescription_request".equals(type)) {
      workflows.updatePrescriptionState(patientId, treatmentId, cycle, "requested", actor.userId());
    }
    String label = "prescription_request".equals(type)
        ? "Solicitud de prescripción" : "Solicitud de continuidad";
    Map<String, Object> result = evolutionResult(
        treatment, actor, "workflow-request-" + requestId, label,
        label + " enviada.\nEsquema: " + treatment.scheme() + "\nCiclo: " + cycle +
            (message.isBlank() ? "" : "\nMensaje: " + message),
        requestId, cycle, requestView(workflows.request(requestId).orElseThrow()));
    return result;
  }

  public List<Map<String, Object>> inbox(SessionPrincipal actor) {
    return workflows.inbox(actor.userId()).stream().map(this::requestView).toList();
  }

  @Transactional
  public Map<String, Object> seen(long id, SessionPrincipal actor) {
    Request request = workflows.markSeen(id, actor.userId(), clock.instant())
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Solicitud no encontrada."));
    return Map.of("ok", true, "item", requestView(request));
  }

  @Transactional
  public Map<String, Object> resolve(long id, JsonNode body, SessionPrincipal actor) {
    Request current = workflows.request(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Solicitud no encontrada."));
    String requiredPermission = "prescription_request".equals(current.type())
        ? "workflow.resolve-prescription"
        : "workflow.resolve-continuity";
    if (!actor.hasPermission(requiredPermission)) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "No tiene permiso para resolver este tipo de solicitud.");
    }
    if (current.assignedTo() != actor.userId() || !"pending".equals(current.status())) {
      throw new ApiException(HttpStatus.CONFLICT, "La solicitud ya no está disponible.");
    }
    String resolution = text(body, "resolution");
    Set<String> allowed = "prescription_request".equals(current.type())
        ? Set.of("prescription_confirmed", "prescription_rejected")
        : Set.of("continue", "temporary_hold", "discontinued");
    if (!allowed.contains(resolution)) throw new ApiException(HttpStatus.BAD_REQUEST, "Decisión inválida.");
    String reason = text(body, "reason");
    if (Set.of("prescription_rejected", "temporary_hold", "discontinued").contains(resolution)
        && reason.length() < 3) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "La causa es obligatoria.");
    }
    LocalDate resumeDate = "temporary_hold".equals(resolution) ? date(body, "resumeDate") : null;
    Request resolved = workflows.resolve(
        id, actor.userId(), resolution, reason, resumeDate, clock.instant())
        .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "La solicitud ya fue resuelta."));
    if ("prescription_confirmed".equals(resolution)) {
      workflows.updatePrescriptionState(
          current.patientId(), current.treatmentId(), current.cycleNumber(), "confirmed", actor.userId());
      ManagementState state = workflows.management(current.patientId(), current.treatmentId()).orElse(null);
      if (state != null && "temporary_hold".equals(state.status())) {
        workflows.upsertManagement(
            current.patientId(), current.treatmentId(), state.status(), state.effectiveFromCycle(),
            state.reason(), state.resumeDate(), false, actor.userId());
      }
    } else if ("prescription_rejected".equals(resolution)) {
      workflows.updatePrescriptionState(
          current.patientId(), current.treatmentId(), current.cycleNumber(), "rejected", actor.userId());
    } else {
      String state = "continue".equals(resolution) ? "active" : resolution;
      workflows.upsertManagement(
          current.patientId(), current.treatmentId(), state, current.cycleNumber(), reason,
          resumeDate, !"active".equals(state), actor.userId());
      if (!"active".equals(state)) {
        workflows.updatePrescriptionState(
            current.patientId(), current.treatmentId(), current.cycleNumber(), "required", actor.userId());
      }
    }
    TreatmentSummary treatment = requireTreatment(current.patientId(), current.treatmentId());
    String label = switch (resolution) {
      case "prescription_confirmed" -> "Prescripción confirmada";
      case "prescription_rejected" -> "Prescripción rechazada";
      case "continue" -> "Continuidad confirmada";
      case "temporary_hold" -> "Suspensión transitoria";
      default -> "Suspensión definitiva";
    };
    return evolutionResult(
        treatment, actor, "workflow-resolution-" + id, label,
        label + ".\nEsquema: " + treatment.scheme() + "\nCiclo: " + current.cycleNumber() +
            (reason.isBlank() ? "" : "\nFundamento: " + reason),
        id, current.cycleNumber(), requestView(resolved));
  }

  private Map<String, Object> evolutionResult(
      TreatmentSummary treatment, SessionPrincipal actor, String id, String reason,
      String text, Long requestId, Integer cycle, Map<String, Object> item) {
    ObjectNode evolution = mapper.createObjectNode();
    evolution.put("id", id);
    evolution.put("date", LocalDate.now(clock).toString());
    evolution.put("datePrecision", "day");
    evolution.put("author", actor.displayName());
    evolution.put("reason", reason);
    evolution.put("specialty", "Oncología / Hospital de día");
    evolution.put("text", text);
    evolution.put("highlighted", true);
    evolution.put("createdAt", clock.instant().toString());
    evolution.put("updatedAt", clock.instant().toString());
    evolution.set("attachments", mapper.createArrayNode());
    evolution.set("linkedStudyIds", mapper.createArrayNode());
    ObjectNode source = evolution.putObject("sourceRef");
    source.put("kind", "treatment-workflow");
    source.put("treatmentId", treatment.treatmentId());
    if (requestId != null) source.put("requestId", requestId);
    if (cycle != null) source.put("cycleNumber", cycle);
    EvolutionAppend appended = documents.appendImmutableEvolution(
        treatment.patientId(), evolution, actor.userId());
    workflows.insertEvent(
        requestId, treatment.patientId(), treatment.treatmentId(), cycle,
        id, actor.userId(), evolution);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("ok", true);
    result.put("item", item);
    result.put("evolution", appended.evolution());
    result.put("documentRevision", appended.revision());
    return result;
  }

  private Map<String, Object> requestView(Request item) {
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

  private Map<String, Object> stateView(ManagementState state) {
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

  private TreatmentSummary requireTreatment(long patientId, String treatmentId) {
    if (patientId < 1 || treatmentId == null || treatmentId.isBlank()
        || !workflows.treatmentExists(patientId, treatmentId)) {
      throw new ApiException(HttpStatus.NOT_FOUND, "Tratamiento no encontrado.");
    }
    return workflows.treatment(patientId, treatmentId);
  }

  private int boundedCycle(int cycle) {
    if (cycle < 1 || cycle > 500) throw new ApiException(HttpStatus.BAD_REQUEST, "Ciclo inválido.");
    return cycle;
  }

  private int treatmentCycle(TreatmentSummary treatment, int cycle) {
    int validCycle = boundedCycle(cycle);
    long lastCycle = (long) treatment.initialCycle() + treatment.cycleCount() - 1L;
    if (validCycle < treatment.initialCycle() || validCycle > lastCycle) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "El ciclo indicado no pertenece al tratamiento. Rango válido: "
              + treatment.initialCycle() + " a " + lastCycle + ".");
    }
    return validCycle;
  }

  private long positiveLong(JsonNode body, String key) {
    try {
      long value = Long.parseLong(body.path(key).asText(""));
      if (value < 1) throw new NumberFormatException();
      return value;
    } catch (NumberFormatException invalid) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Identificador inválido.");
    }
  }

  private LocalDate date(JsonNode body, String key) {
    String value = text(body, key);
    if (value.isBlank()) return null;
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException invalid) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Fecha inválida.");
    }
  }

  private String text(JsonNode body, String key) {
    return body.path(key).asText("").trim();
  }
}
