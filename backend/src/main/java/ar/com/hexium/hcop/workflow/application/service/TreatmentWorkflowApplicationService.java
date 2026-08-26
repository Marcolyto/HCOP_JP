package ar.com.hexium.hcop.workflow.application.service;

import ar.com.hexium.hcop.workflow.application.port.in.TreatmentWorkflowUseCase;
import ar.com.hexium.hcop.workflow.application.port.out.PatientEvolutionPort;
import ar.com.hexium.hcop.workflow.application.port.out.PatientEvolutionPort.AppendedEvolution;
import ar.com.hexium.hcop.workflow.application.port.out.TreatmentWorkflowStore;
import ar.com.hexium.hcop.workflow.application.port.out.TreatmentWorkflowStore.DuplicateRequestException;
import ar.com.hexium.hcop.workflow.domain.EvolutionDraft;
import ar.com.hexium.hcop.workflow.domain.ManagementState;
import ar.com.hexium.hcop.workflow.domain.TreatmentWorkflowSummary;
import ar.com.hexium.hcop.workflow.domain.WorkflowRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TreatmentWorkflowApplicationService implements TreatmentWorkflowUseCase {
  private final TreatmentWorkflowStore store;
  private final PatientEvolutionPort evolutions;
  private final Clock clock;

  public TreatmentWorkflowApplicationService(
      TreatmentWorkflowStore store, PatientEvolutionPort evolutions, Clock clock) {
    this.store = store;
    this.evolutions = evolutions;
    this.clock = clock;
  }

  @Override
  public ManagementActionResult suspend(SuspendCommand command) {
    TreatmentWorkflowSummary treatment = requireTreatment(command.patientId(), command.treatmentId());
    String status = "definitive".equals(command.kind()) ? "discontinued" : "temporary_hold";
    String reason = orEmpty(command.reason());
    if (reason.length() < 3) invalid("Indique el motivo de la suspensión.");
    int requested = command.cycleNumber() == -1 ? treatment.initialCycle() : command.cycleNumber();
    int cycle = treatmentCycle(treatment, requested);
    LocalDate resumeDate = "temporary_hold".equals(status) ? date(command.resumeDateRaw()) : null;
    ManagementState state = store.upsertManagement(
        command.patientId(), command.treatmentId(), status, cycle, reason, resumeDate, true,
        command.actorId());
    store.updatePrescriptionState(
        command.patientId(), command.treatmentId(), cycle, "required", command.actorId());
    String label = "discontinued".equals(status) ? "Suspensión definitiva" : "Suspensión transitoria";
    String text = label + " del tratamiento.\nEsquema: " + treatment.scheme() +
        "\nCiclo: " + cycle + "\nMotivo: " + reason +
        (resumeDate == null ? "" : "\nFecha prevista de revisión: " + resumeDate);
    AppendedEvolution appended = appendEvolution(
        treatment, "treatment-" + status, label, text, null, cycle,
        command.actorId(), command.actorDisplayName());
    return new ManagementActionResult(state, appended.evolution(), appended.revision());
  }

  @Override
  public ManagementActionResult resume(ResumeCommand command) {
    TreatmentWorkflowSummary treatment = requireTreatment(command.patientId(), command.treatmentId());
    ManagementState current = store.management(command.patientId(), command.treatmentId())
        .orElseThrow(() -> conflict("El tratamiento no está suspendido."));
    if ("discontinued".equals(current.status())) {
      throw conflict(
          "Una suspensión definitiva requiere una nueva indicación clínica; no se reactiva como "
              + "si fuera transitoria.");
    }
    if (!"temporary_hold".equals(current.status())) throw conflict("El tratamiento ya está activo.");
    if (!"confirmed".equals(store.prescriptionState(
        command.patientId(), command.treatmentId(), current.effectiveFromCycle()))) {
      throw conflict("Confirme primero la nueva prescripción del ciclo antes de reanudar el tratamiento.");
    }
    String reason = orEmpty(command.reason());
    if (reason.length() < 3) invalid("Indique el motivo para reanudar.");
    ManagementState state = store.upsertManagement(
        command.patientId(), command.treatmentId(), "active", current.effectiveFromCycle(), reason,
        null, false, command.actorId());
    String text = "Tratamiento reanudado.\nEsquema: " + treatment.scheme() +
        "\nDesde ciclo: " + current.effectiveFromCycle() + "\nFundamento: " + reason;
    AppendedEvolution appended = appendEvolution(
        treatment, "treatment-resumed", "Tratamiento reanudado", text, null,
        current.effectiveFromCycle(), command.actorId(), command.actorDisplayName());
    return new ManagementActionResult(state, appended.evolution(), appended.revision());
  }

  @Override
  public RequestActionResult createRequest(CreateRequestCommand command) {
    if (!Set.of("prescription_request", "continuity_request").contains(command.type())) {
      invalid("Tipo de solicitud inválido.");
    }
    long patientId = positiveLong(command.patientIdRaw());
    String treatmentId = orEmpty(command.treatmentId());
    int requestedCycle = boundedCycle(command.cycleNumber());
    long assignedTo = positiveLong(command.assignedToUserIdRaw());
    TreatmentWorkflowSummary treatment = requireTreatment(patientId, treatmentId);
    int cycle = treatmentCycle(treatment, requestedCycle);
    Map<String, String> context = Map.of(
        "patientName", treatment.patientName(), "patientDni", treatment.patientDni(),
        "scheme", treatment.scheme(), "diagnosis", treatment.diagnosis());
    long requestId;
    try {
      requestId = store.insertRequest(
          command.type(), patientId, treatmentId, cycle, command.actorId(),
          assignedTo, orEmpty(command.message()), context);
    } catch (DuplicateRequestException duplicate) {
      throw conflict("Ya existe una solicitud pendiente para este tratamiento y ciclo.");
    }
    if ("prescription_request".equals(command.type())) {
      store.updatePrescriptionState(patientId, treatmentId, cycle, "requested", command.actorId());
    }
    String label = "prescription_request".equals(command.type())
        ? "Solicitud de prescripción" : "Solicitud de continuidad";
    String message = orEmpty(command.message());
    String text = label + " enviada.\nEsquema: " + treatment.scheme() + "\nCiclo: " + cycle +
        (message.isBlank() ? "" : "\nMensaje: " + message);
    AppendedEvolution appended = appendEvolution(
        treatment, "workflow-request-" + requestId, label, text, requestId, cycle,
        command.actorId(), command.actorDisplayName());
    WorkflowRequest created = store.request(requestId).orElseThrow();
    return new RequestActionResult(created, appended.evolution(), appended.revision());
  }

  @Override
  public List<WorkflowRequest> inbox(long actorUserId) {
    return store.inbox(actorUserId);
  }

  @Override
  public WorkflowRequest seen(long id, long actorUserId) {
    return store.markSeen(id, actorUserId, clock.instant())
        .orElseThrow(() -> new WorkflowFailure(WorkflowFailure.Type.NOT_FOUND, "Solicitud no encontrada."));
  }

  @Override
  public RequestActionResult resolveRequest(ResolveCommand command) {
    WorkflowRequest current = store.request(command.requestId())
        .orElseThrow(() -> new WorkflowFailure(WorkflowFailure.Type.NOT_FOUND, "Solicitud no encontrada."));
    String requiredPermission = "prescription_request".equals(current.type())
        ? "workflow.resolve-prescription" : "workflow.resolve-continuity";
    if (!command.permission().hasPermission(requiredPermission)) {
      throw new WorkflowFailure(
          WorkflowFailure.Type.FORBIDDEN, "No tiene permiso para resolver este tipo de solicitud.");
    }
    if (current.assignedTo() != command.actorId() || !"pending".equals(current.status())) {
      throw conflict("La solicitud ya no está disponible.");
    }
    String resolution = orEmpty(command.resolution());
    Set<String> allowed = "prescription_request".equals(current.type())
        ? Set.of("prescription_confirmed", "prescription_rejected")
        : Set.of("continue", "temporary_hold", "discontinued");
    if (!allowed.contains(resolution)) invalid("Decisión inválida.");
    String reason = orEmpty(command.reason());
    if (Set.of("prescription_rejected", "temporary_hold", "discontinued").contains(resolution)
        && reason.length() < 3) {
      invalid("La causa es obligatoria.");
    }
    LocalDate resumeDate = "temporary_hold".equals(resolution) ? date(command.resumeDateRaw()) : null;
    WorkflowRequest resolved = store.resolve(
        command.requestId(), command.actorId(), resolution, reason, resumeDate, clock.instant())
        .orElseThrow(() -> conflict("La solicitud ya fue resuelta."));
    if ("prescription_confirmed".equals(resolution)) {
      store.updatePrescriptionState(
          current.patientId(), current.treatmentId(), current.cycleNumber(), "confirmed",
          command.actorId());
      store.management(current.patientId(), current.treatmentId())
          .filter(state -> "temporary_hold".equals(state.status()))
          .ifPresent(state -> store.upsertManagement(
              current.patientId(), current.treatmentId(), state.status(), state.effectiveFromCycle(),
              state.reason(), state.resumeDate(), false, command.actorId()));
    } else if ("prescription_rejected".equals(resolution)) {
      store.updatePrescriptionState(
          current.patientId(), current.treatmentId(), current.cycleNumber(), "rejected",
          command.actorId());
    } else {
      String state = "continue".equals(resolution) ? "active" : resolution;
      store.upsertManagement(
          current.patientId(), current.treatmentId(), state, current.cycleNumber(), reason,
          resumeDate, !"active".equals(state), command.actorId());
      if (!"active".equals(state)) {
        store.updatePrescriptionState(
            current.patientId(), current.treatmentId(), current.cycleNumber(), "required",
            command.actorId());
      }
    }
    TreatmentWorkflowSummary treatment = requireTreatment(current.patientId(), current.treatmentId());
    String label = switch (resolution) {
      case "prescription_confirmed" -> "Prescripción confirmada";
      case "prescription_rejected" -> "Prescripción rechazada";
      case "continue" -> "Continuidad confirmada";
      case "temporary_hold" -> "Suspensión transitoria";
      default -> "Suspensión definitiva";
    };
    String text = label + ".\nEsquema: " + treatment.scheme() + "\nCiclo: " + current.cycleNumber() +
        (reason.isBlank() ? "" : "\nFundamento: " + reason);
    AppendedEvolution appended = appendEvolution(
        treatment, "workflow-resolution-" + command.requestId(), label, text, command.requestId(),
        current.cycleNumber(), command.actorId(), command.actorDisplayName());
    return new RequestActionResult(resolved, appended.evolution(), appended.revision());
  }

  private AppendedEvolution appendEvolution(
      TreatmentWorkflowSummary treatment, String id, String reason, String text, Long requestId,
      Integer cycle, long actorId, String actorDisplayName) {
    EvolutionDraft draft = new EvolutionDraft(id, reason, text, treatment.treatmentId(), requestId, cycle);
    AppendedEvolution appended = evolutions.append(treatment.patientId(), draft, actorId, actorDisplayName);
    store.insertEvent(
        requestId, treatment.patientId(), treatment.treatmentId(), cycle, id, actorId,
        appended.evolution());
    return appended;
  }

  private TreatmentWorkflowSummary requireTreatment(long patientId, String treatmentId) {
    if (patientId < 1 || treatmentId == null || treatmentId.isBlank()
        || !store.treatmentExists(patientId, treatmentId)) {
      throw new WorkflowFailure(WorkflowFailure.Type.NOT_FOUND, "Tratamiento no encontrado.");
    }
    return store.treatment(patientId, treatmentId)
        .orElseThrow(() -> new WorkflowFailure(WorkflowFailure.Type.NOT_FOUND, "Tratamiento no encontrado."));
  }

  private int boundedCycle(int cycle) {
    if (cycle < 1 || cycle > 500) invalid("Ciclo inválido.");
    return cycle;
  }

  private int treatmentCycle(TreatmentWorkflowSummary treatment, int cycle) {
    int validCycle = boundedCycle(cycle);
    long lastCycle = (long) treatment.initialCycle() + treatment.cycleCount() - 1L;
    if (validCycle < treatment.initialCycle() || validCycle > lastCycle) {
      invalid("El ciclo indicado no pertenece al tratamiento. Rango válido: "
          + treatment.initialCycle() + " a " + lastCycle + ".");
    }
    return validCycle;
  }

  private LocalDate date(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return LocalDate.parse(raw.trim());
    } catch (java.time.format.DateTimeParseException invalid) {
      throw new WorkflowFailure(WorkflowFailure.Type.INVALID, "Fecha inválida.");
    }
  }

  private long positiveLong(String raw) {
    try {
      long value = Long.parseLong(raw == null ? "" : raw);
      if (value < 1) throw new NumberFormatException();
      return value;
    } catch (NumberFormatException invalid) {
      throw new WorkflowFailure(WorkflowFailure.Type.INVALID, "Identificador inválido.");
    }
  }

  private String orEmpty(String value) {
    return value == null ? "" : value.trim();
  }

  private void invalid(String message) {
    throw new WorkflowFailure(WorkflowFailure.Type.INVALID, message);
  }

  private WorkflowFailure conflict(String message) {
    return new WorkflowFailure(WorkflowFailure.Type.CONFLICT, message);
  }
}
