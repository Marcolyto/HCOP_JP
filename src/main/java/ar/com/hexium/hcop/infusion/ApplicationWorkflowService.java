package ar.com.hexium.hcop.infusion;

import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowCommands.AdministrationComplete;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowCommands.AdministrationInterrupt;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowCommands.AdministrationResolve;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowCommands.AdministrationStart;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowCommands.Basic;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowCommands.ClinicalAuthorization;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowCommands.PharmacyValidation;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowCommands.Preparation;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowCommands.PreparationComplete;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowCommands.StockComponent;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowCommands.StockReservation;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowRepository.Application;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowRepository.InventoryLot;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowRepository.Key;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowRepository.Reservation;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowRepository.WorkflowEvent;
import ar.com.hexium.hcop.patient.PatientDocumentService;
import ar.com.hexium.hcop.patient.PatientDocumentService.EvolutionAppend;
import ar.com.hexium.hcop.treatment.DayHospitalApplicationPolicy;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class ApplicationWorkflowService {
  private static final Set<String> QUEUES =
      Set.of("pharmacy", "triage", "preparation", "administration");

  private final ApplicationWorkflowRepository workflows;
  private final TreatmentApplicationLogisticsService logistics;
  private final PatientDocumentService documents;
  private final ObjectMapper mapper;
  private final Clock clock;

  public ApplicationWorkflowService(
      ApplicationWorkflowRepository workflows,
      TreatmentApplicationLogisticsService logistics,
      PatientDocumentService documents,
      ObjectMapper mapper,
      Clock clock) {
    this.workflows = workflows;
    this.logistics = logistics;
    this.documents = documents;
    this.mapper = mapper;
    this.clock = clock;
  }

  @Transactional
  public List<Map<String, Object>> list(
      String queue, LocalDate date, String query, String medicationSource) {
    String normalizedQueue = queue == null ? "" : queue.trim().toLowerCase();
    if (!QUEUES.contains(normalizedQueue)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "La cola operativa es inválida.");
    }
    String source = normalizeSource(medicationSource, true);
    logistics.synchronizeExistingTreatments();
    workflows.ensureWorkflowRows();
    LocalDate effectiveDate = "pharmacy".equals(normalizedQueue)
        ? date
        : date == null ? LocalDate.now(clock) : date;
    return workflows.list(normalizedQueue, effectiveDate, query, source).stream()
        .map(item -> view(item, false))
        .toList();
  }

  @Transactional
  public Map<String, Object> get(long patientId, String treatmentId, int cycle, int day) {
    Key key = key(patientId, treatmentId, cycle, day);
    logistics.synchronizeTreatment(treatmentId);
    workflows.ensureWorkflowRows();
    return view(require(key), true);
  }

  @Transactional
  public String preparationLabel(
      long patientId, String treatmentId, int cycle, int day) {
    Key key = key(patientId, treatmentId, cycle, day);
    logistics.synchronizeTreatment(treatmentId);
    workflows.ensureWorkflowRows();
    Application application = require(key);
    if (!Set.of("prepared", "released").contains(application.preparationStatus())
        && !"completed".equals(application.administrationStatus())) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "La etiqueta se habilita después de registrar la preparación.",
          "PREPARATION_NOT_READY");
    }
    var lots = workflows.preparationLots(key);
    if (lots.isEmpty()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "La preparación no posee lotes activos para imprimir.",
          "PREPARATION_TRACE_REQUIRED");
    }
    StringBuilder rows = new StringBuilder();
    for (var lot : lots) {
      String quantity = lot.quantity() == null
          ? lot.quantityText()
          : lot.quantity().stripTrailingZeros().toPlainString()
              + (lot.unit().isBlank() ? "" : " " + lot.unit());
      rows.append("""
          <tr><td><strong>%s</strong></td><td>%s</td><td>%s</td><td>%s</td>
          <td>%s</td><td>%s</td><td>%s</td><td>%d min</td></tr>
          """.formatted(
          htmlEscape(lot.drugName()), htmlEscape(quantity),
          htmlEscape(lot.lotNumber()), htmlEscape(lot.expirationDate().toString()),
          htmlEscape(lot.diluent()), htmlEscape(lot.finalVolume()),
          htmlEscape(lot.concentration()), lot.ttlMinutes()));
    }
    String identifier = "APP-" + patientId + "-" + treatmentId + "-" + cycle + "-" + day;
    String qrUrl = "/api/clinical/patients/" + patientId + "/treatments/"
        + URLEncoder.encode(treatmentId, StandardCharsets.UTF_8)
        + "/documents/qr?cycle=" + cycle + "&applicationDay=" + day;
    boolean expired = application.preparationExpiresAt() == null
        || !application.preparationExpiresAt().isAfter(clock.instant());
    return """
        <!doctype html><html lang="es"><head><meta charset="utf-8">
        <title>Etiqueta de preparación · Ciclo %d Día %d</title>
        <style>
        *{box-sizing:border-box}body{margin:0;background:#eef2f5;color:#17212b;font:14px system-ui}
        main{width:96%%;max-width:980px;margin:18px auto;background:#fff;border:1px solid #9eabb6;padding:22px}
        header{display:flex;justify-content:space-between;gap:24px;border-bottom:2px solid #455b6c;padding-bottom:14px}
        h1{font-size:22px;margin:0 0 7px}p{margin:4px 0}.identifier{font:12px ui-monospace;overflow-wrap:anywhere}
        .status{padding:7px 10px;border:1px solid #455b6c;font-weight:700;align-self:flex-start}
        .expired{color:#a71919;border-color:#a71919}dl{display:grid;grid-template-columns:repeat(4,1fr);gap:10px;margin:15px 0}
        dl div{border:1px solid #d6dde2;padding:8px}dt{font-size:11px;color:#66737d}dd{margin:2px 0 0;font-weight:650}
        table{width:100%%;border-collapse:collapse;margin-top:14px}th,td{border:1px solid #aeb8c0;padding:7px;text-align:left}
        th{background:#edf2f5;font-size:11px}footer{display:flex;justify-content:space-between;gap:18px;margin-top:16px}
        a,button{padding:8px 12px;border:1px solid #526777;background:#fff;color:#17212b;text-decoration:none}
        @media print{body{background:#fff}main{width:100%%;margin:0;border:0}button{display:none}}
        </style></head><body><main>
        <header><div><h1>Hospital de día · Etiqueta de mezcla</h1>
        <p><strong>%s</strong> · DNI %s · HC %s</p>
        <p>%s · Ciclo %d · Día %d</p><p class="identifier">%s</p></div>
        <span class="status %s">%s</span></header>
        <dl><div><dt>Preparó</dt><dd>%s</dd></div><div><dt>Verificación registrada</dt><dd>%s</dd></div>
        <div><dt>Preparada</dt><dd>%s</dd></div><div><dt>Utilizar antes de</dt><dd>%s</dd></div></dl>
        <table><thead><tr><th>Droga</th><th>Dosis/cantidad</th><th>Lote</th><th>Vence lote</th>
        <th>Diluyente</th><th>Volumen final</th><th>Concentración</th><th>TTL</th></tr></thead>
        <tbody>%s</tbody></table>
        <footer><a href="%s" target="_blank" rel="noopener">Abrir QR de identificación</a>
        <button onclick="print()">Imprimir etiqueta</button></footer>
        </main></body></html>
        """.formatted(
        cycle, day, htmlEscape(application.patientName()), htmlEscape(application.patientDni()),
        htmlEscape(application.medicalRecord()), htmlEscape(application.scheme()), cycle, day,
        htmlEscape(identifier), expired ? "expired" : "", expired ? "VENCIDA" : "VIGENTE",
        htmlEscape(application.preparedByName()),
        htmlEscape(application.preparationVerifiedByName()),
        htmlEscape(value(application.preparedAt())),
        htmlEscape(value(application.preparationExpiresAt())),
        rows, htmlEscape(qrUrl));
  }

  @Transactional
  public CommandResult pharmacyValidation(
      long patientId, String treatmentId, int cycle, int day,
      PharmacyValidation command, SessionPrincipal actor) {
    requireBody(command);
    requireCommand(command.expectedRevision(), command.idempotencyKey());
    if (command.validated() == null) {
      throw badRequest("Indique si Farmacia valida o rechaza la orden.");
    }
    String action = command.validated() ? "pharmacy_validation_approved" : "pharmacy_validation_rejected";
    return execute(key(patientId, treatmentId, cycle, day), action,
        command.expectedRevision(), command.idempotencyKey(), command, actor,
        current -> {
          ApplicationWorkflowPolicy.pharmacyValidation(
              current.policyState(), command.validated());
          if (command.validated()) {
            validatePrescriptionDrugs(current.applicationDrugs());
          }
          String source = normalizeSource(command.medicationSource(), false);
          if (source.isBlank()) source = current.medicationSource();
          ApplicationWorkflowPolicy.supplySource(current.policyState(), source);
          String notes = trim(command.notes());
          if (!command.validated() && notes.length() < 3) {
            throw badRequest("Indique el motivo del rechazo farmacéutico.");
          }
          Instant now = clock.instant();
          changed(workflows.updatePharmacyValidation(
              current.key(), current.revision(), command.validated(), source, notes,
              actor.userId(), now));
          workflows.synchronizeLogisticsSource(current.key(), source, actor.userId(), now);
        });
  }

  @Transactional
  public CommandResult stockReservation(
      long patientId, String treatmentId, int cycle, int day,
      StockReservation command, SessionPrincipal actor) {
    requireBody(command);
    requireCommand(command.expectedRevision(), command.idempotencyKey());
    if (command.reserved() == null) {
      throw badRequest("Indique si desea reservar o liberar el stock.");
    }
    String action = command.reserved() ? "stock_reserved" : "stock_released";
    return execute(key(patientId, treatmentId, cycle, day), action,
        command.expectedRevision(), command.idempotencyKey(), command, actor,
        current -> {
          if (command.reserved()) {
            reserve(current, command, actor);
          } else {
            ApplicationWorkflowPolicy.releaseStock(current.policyState());
            Instant now = clock.instant();
            workflows.releaseReservations(current.key(), actor.userId(), now);
            changed(workflows.updateReservationStatus(
                current.key(), current.revision(), "released", trim(command.notes()),
                actor.userId(), now));
          }
        });
  }

  @Transactional
  public CommandResult clinicalAuthorization(
      long patientId, String treatmentId, int cycle, int day,
      ClinicalAuthorization command, SessionPrincipal actor) {
    requireBody(command);
    requireCommand(command.expectedRevision(), command.idempotencyKey());
    String decision = trim(command.decision()).toUpperCase();
    if (!Set.of("PASS", "FAIL").contains(decision)) {
      throw badRequest("La decisión clínica debe ser PASS o FAIL.");
    }
    boolean passed = "PASS".equals(decision);
    if (!isObject(command.laboratory()) || command.laboratory().isEmpty()
        || !isObject(command.vitalSigns()) || command.vitalSigns().isEmpty()
        || !isObject(command.toxicity()) || command.toxicity().isEmpty()) {
      throw badRequest("Complete laboratorio, signos vitales y evaluación de toxicidad.");
    }
    validateTriage(command, passed);
    String reason = trim(command.reason());
    if (!passed && reason.length() < 3) {
      throw badRequest("Indique el motivo clínico de la postergación.");
    }
    if (!passed && command.rescheduledDate() != null
        && command.rescheduledDate().isBefore(LocalDate.now(clock))) {
      throw badRequest("La nueva fecha propuesta no puede estar en el pasado.");
    }
    List<String> clinicalAlerts = passed ? triageSafetyAlerts(command) : List.of();
    if (passed && !clinicalAlerts.isEmpty() && reason.length() < 10) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "Hay alertas clínicas (" + String.join(", ", clinicalAlerts)
              + "). Revise los datos y documente una justificación para emitir PASS.",
          "CLINICAL_OVERRIDE_REQUIRED");
    }
    CommandResult result = execute(key(patientId, treatmentId, cycle, day),
        passed ? "clinical_pass" : "clinical_fail",
        command.expectedRevision(), command.idempotencyKey(), command, actor,
        current -> {
          ApplicationWorkflowPolicy.clinicalAuthorization(
              current.policyState(), passed,
              current.hasConfirmedAppointmentOn(LocalDate.now(clock), clock.getZone()));
          ObjectNode assessment = mapper.createObjectNode();
          assessment.put("decision", decision);
          assessment.set("laboratory", command.laboratory().deepCopy());
          assessment.set("vitalSigns", command.vitalSigns().deepCopy());
          assessment.set("toxicity", command.toxicity().deepCopy());
          if (!reason.isBlank()) assessment.put("reason", reason);
          if (!clinicalAlerts.isEmpty()) {
            ArrayNode alerts = assessment.putArray("safetyAlerts");
            clinicalAlerts.forEach(alerts::add);
            assessment.put("overrideDocumented", true);
          }
          if (command.rescheduledDate() != null) {
            assessment.put("rescheduledDate", command.rescheduledDate().toString());
          }
          Instant now = clock.instant();
          if (!passed && "reserved".equals(current.stockReservationStatus())) {
            workflows.releaseReservations(current.key(), actor.userId(), now);
          }
          changed(workflows.updateClinicalAuthorization(
              current.key(), current.revision(), passed, reason, assessment,
              command.rescheduledDate(), actor.userId(), now));
          if (!passed) {
            workflows.synchronizeSessionAfterClinicalFail(
              current.key(), reason, actor.userId(), now);
          }
        });
    if (!passed && !result.idempotentReplay()) {
      EvolutionAppend evolution = appendEvolution(
          patientId,
          treatmentId,
          cycle,
          day,
          "application-triage-fail-" + command.idempotencyKey(),
          "Postergación de aplicación",
          "Aplicación postergada por triaje clínico.\n"
              + "Esquema: " + result.workflow().path("scheme").asText("") + "\n"
              + "Ciclo " + cycle + " · Día " + day + "\n"
              + "Motivo: " + reason
              + (command.rescheduledDate() == null
                  ? "" : "\nNueva fecha sugerida: " + command.rescheduledDate())
              + "\nLa reserva y el turno fueron liberados para reprogramación.",
          actor);
      return result.withEvolution(evolution);
    }
    return result;
  }

  @Transactional
  public CommandResult preparationStart(
      long patientId, String treatmentId, int cycle, int day,
      Basic command, SessionPrincipal actor) {
    requireBody(command);
    requireCommand(command.expectedRevision(), command.idempotencyKey());
    return execute(key(patientId, treatmentId, cycle, day), "preparation_started",
        command.expectedRevision(), command.idempotencyKey(), command, actor,
        current -> {
          ApplicationWorkflowPolicy.startPreparation(current.policyState());
          ObjectNode data = mapper.createObjectNode();
          data.put("notes", trim(command.notes()));
          Instant now = clock.instant();
          if ("cancelled".equals(current.preparationStatus())) {
            changed(workflows.resetCancelledPreparation(
                current.key(), current.revision(), actor.userId(), now));
          }
          changed(workflows.updatePreparation(
              current.key(), current.revision(), "in_preparation", data,
              null, null, actor.userId(), now));
          workflows.synchronizeSessionPreparation(
              current.key(), "in_preparation", actor.userId(), now);
        });
  }

  @Transactional
  public CommandResult preparationComplete(
      long patientId, String treatmentId, int cycle, int day,
      PreparationComplete command, SessionPrincipal actor) {
    requireBody(command);
    requireCommand(command.expectedRevision(), command.idempotencyKey());
    if (command.preparations() == null || command.preparations().isEmpty()) {
      throw badRequest("Registre al menos una preparación con lote y estabilidad.");
    }
    return execute(key(patientId, treatmentId, cycle, day), "preparation_completed",
        command.expectedRevision(), command.idempotencyKey(), command, actor,
        current -> completePreparation(current, command, actor));
  }

  @Transactional
  public CommandResult preparationRelease(
      long patientId, String treatmentId, int cycle, int day,
      Basic command, SessionPrincipal actor) {
    requireBody(command);
    requireCommand(command.expectedRevision(), command.idempotencyKey());
    return execute(key(patientId, treatmentId, cycle, day), "preparation_released",
        command.expectedRevision(), command.idempotencyKey(), command, actor,
        current -> {
          ApplicationWorkflowPolicy.releasePreparation(current.policyState());
          Instant now = clock.instant();
          if (current.preparationExpiresAt() == null
              || !current.preparationExpiresAt().isAfter(now)) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "La preparación venció y no puede liberarse.",
                "PREPARATION_EXPIRED");
          }
          ObjectNode data = current.preparationData().isObject()
              ? (ObjectNode) current.preparationData().deepCopy()
              : mapper.createObjectNode();
          data.put("releaseNotes", trim(command.notes()));
          changed(workflows.updatePreparation(
              current.key(), current.revision(), "released", data,
              current.preparationExpiresAt(), null, actor.userId(), now));
          workflows.synchronizeSessionPreparation(
              current.key(), "released", actor.userId(), now);
        });
  }

  @Transactional
  public CommandResult preparationRestart(
      long patientId, String treatmentId, int cycle, int day,
      Basic command, SessionPrincipal actor) {
    requireBody(command);
    requireCommand(command.expectedRevision(), command.idempotencyKey());
    String notes = trim(command.notes());
    ApplicationWorkflowPolicy.preparationRestartReason(notes);
    return execute(key(patientId, treatmentId, cycle, day), "preparation_restarted",
        command.expectedRevision(), command.idempotencyKey(), command, actor,
        current -> {
          ApplicationWorkflowPolicy.restartPreparation(current.policyState());
          Instant now = clock.instant();
          changed(workflows.restartPreparation(
              current.key(), current.revision(), notes, actor.userId(), now));
          String replacementSource = switch (current.medicationSource()) {
            case "patient_has_medication" -> "patient_to_bring";
            case "received_center" -> "pending_supplier";
            default -> current.medicationSource();
          };
          workflows.synchronizeLogisticsSource(
              current.key(), replacementSource, actor.userId(), now);
          workflows.synchronizeSessionPreparation(
              current.key(), "pending", actor.userId(), now);
        });
  }

  @Transactional
  public CommandResult administrationStart(
      long patientId, String treatmentId, int cycle, int day,
      AdministrationStart command, SessionPrincipal actor) {
    requireBody(command);
    requireCommand(command.expectedRevision(), command.idempotencyKey());
    if (!Boolean.TRUE.equals(command.patientVerified())
        || !Boolean.TRUE.equals(command.labelVerified())) {
      throw badRequest("Confirme paciente y etiqueta antes de iniciar.");
    }
    Long checker = workflows.resolveEnabledUser(
            trim(command.doubleCheckBy()), "application.administration.manage")
        .orElseThrow(() -> badRequest(
            "Seleccione un segundo profesional habilitado para el doble control."));
    if (checker == actor.userId()) {
      throw badRequest("El doble control debe realizarlo otro profesional.");
    }
    Instant startedAt = command.startedAt() == null ? clock.instant() : command.startedAt();
    if (startedAt.isAfter(clock.instant().plusSeconds(300))) {
      throw badRequest("La hora de inicio no puede estar en el futuro.");
    }
    if (!LocalDate.ofInstant(startedAt, clock.getZone()).equals(LocalDate.now(clock))) {
      throw badRequest("La hora de inicio debe corresponder al día operativo actual.");
    }
    long checkerId = checker;
    return execute(key(patientId, treatmentId, cycle, day), "administration_started",
        command.expectedRevision(), command.idempotencyKey(), command, actor,
        current -> {
          boolean hasTraceablePreparation =
              current.preparationExpiresAt() != null
                  && !workflows.preparationLots(current.key()).isEmpty();
          ApplicationWorkflowPolicy.startAdministration(
              current.policyState(),
              current.hasConfirmedAppointmentOn(LocalDate.now(clock), clock.getZone()),
              hasTraceablePreparation);
          if (current.preparationExpiresAt() == null
              || !current.preparationExpiresAt().isAfter(clock.instant())) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "La preparación está vencida y debe rehacerse antes de administrar.",
                "PREPARATION_EXPIRED");
          }
          ObjectNode data = mapper.createObjectNode();
          data.put("patientVerified", true);
          data.put("labelVerified", true);
          data.put("doubleCheckByUserId", checkerId);
          data.put("doubleCheckDisplayName", workflows.userDisplayName(checkerId));
          data.put("startedAt", startedAt.toString());
          data.put("notes", trim(command.notes()));
          Instant now = clock.instant();
          changed(workflows.updateAdministration(
              current.key(), current.revision(), "in_progress", data,
              checkerId, startedAt, actor.userId(), now));
          workflows.synchronizeSessionAdministration(
              current.key(), "in_progress", "in_progress", actor.userId(), now);
        });
  }

  @Transactional
  public CommandResult administrationComplete(
      long patientId, String treatmentId, int cycle, int day,
      AdministrationComplete command, SessionPrincipal actor) {
    requireBody(command);
    requireCommand(command.expectedRevision(), command.idempotencyKey());
    String actualDose = trim(command.actualDose());
    String observation = trim(command.observation());
    boolean reaction = Boolean.TRUE.equals(command.reactionOccurred());
    String reactionDescription = trim(command.reactionDescription());
    if (actualDose.length() < 2) {
      throw badRequest("Registre la dosis efectivamente administrada.");
    }
    if (observation.length() < 3) {
      throw badRequest("Registre la condición del paciente al finalizar.");
    }
    Instant completedAt = command.completedAt() == null ? clock.instant() : command.completedAt();
    CommandResult result = execute(key(patientId, treatmentId, cycle, day), "administration_completed",
        command.expectedRevision(), command.idempotencyKey(), command, actor,
        current -> {
          ApplicationWorkflowPolicy.completeAdministration(current.policyState());
          if (current.administrationStartedAt() == null
              || completedAt.isBefore(current.administrationStartedAt())) {
            throw badRequest("La hora de finalización es anterior al inicio.");
          }
          if (completedAt.isAfter(clock.instant().plusSeconds(300))) {
            throw badRequest("La hora de finalización no puede estar en el futuro.");
          }
          ObjectNode data = current.administrationData().isObject()
              ? (ObjectNode) current.administrationData().deepCopy()
              : mapper.createObjectNode();
          AdministrationReaction recordedReaction =
              resolveAdministrationReaction(data, reaction, reactionDescription);
          if (recordedReaction.occurred() && recordedReaction.description().length() < 3) {
            throw badRequest("Describa la reacción y las medidas adoptadas.");
          }
          data.put("completedAt", completedAt.toString());
          data.put("actualDose", actualDose);
          data.put("reactionOccurred", recordedReaction.occurred());
          data.put("reactionDescription", recordedReaction.description());
          data.put(
              "reactionDerivedFromInterruptions",
              recordedReaction.derivedFromInterruptions());
          data.put("reactionInterruptionCount", recordedReaction.interruptionCount());
          data.put("observation", observation);
          Instant now = clock.instant();
          changed(workflows.updateAdministration(
              current.key(), current.revision(), "completed", data,
              null, completedAt, actor.userId(), now));
          workflows.synchronizeSessionAdministration(
              current.key(), "completed", "completed", actor.userId(), now);
        });
    if (!result.idempotentReplay()) {
      JsonNode workflow = result.workflow();
      JsonNode administration = workflow.path("administrationData");
      String secondChecker = administration.path("doubleCheckDisplayName").asText(
          "Usuario " + administration.path("doubleCheckByUserId").asText(""));
      boolean recordedReaction = administration.path("reactionOccurred").asBoolean(false);
      String recordedReactionDescription =
          administration.path("reactionDescription").asText("").trim();
      EvolutionAppend evolution = appendEvolution(
          patientId,
          treatmentId,
          cycle,
          day,
          "application-administration-complete-" + command.idempotencyKey(),
          "Administración de tratamiento",
          "Aplicación completada.\n"
              + "Esquema: " + workflow.path("scheme").asText("") + "\n"
              + "Ciclo " + cycle + " · Día " + day + "\n"
              + "Dosis administrada: " + actualDose + "\n"
              + "Inicio: " + workflow.path("administrationStartedAt").asText("") + "\n"
              + "Finalización: " + completedAt + "\n"
              + "Reacción/incidencia: "
              + (recordedReaction ? recordedReactionDescription : "No") + "\n"
              + "Observación: " + observation + "\n"
              + "Administró: " + actor.displayName() + "\n"
              + "Segundo control: " + secondChecker,
          actor);
      return result.withEvolution(evolution);
    }
    return result;
  }

  @Transactional
  public CommandResult administrationInterrupt(
      long patientId, String treatmentId, int cycle, int day,
      AdministrationInterrupt command, SessionPrincipal actor) {
    requireBody(command);
    requireCommand(command.expectedRevision(), command.idempotencyKey());
    String reason = trim(command.reason());
    String actualDose = trim(command.actualDose());
    String measures = trim(command.measures());
    String patientCondition = trim(command.patientCondition());
    String disposition = trim(command.disposition()).toLowerCase();
    if (reason.length() < 3) {
      throw badRequest("Describa el motivo de la interrupción.");
    }
    if (actualDose.length() < 2) {
      throw badRequest("Registre la dosis administrada hasta la interrupción.");
    }
    if (measures.length() < 3) {
      throw badRequest("Registre las medidas adoptadas.");
    }
    if (patientCondition.length() < 3) {
      throw badRequest("Registre la condición actual del paciente.");
    }
    if (!Set.of("observation", "medical_review", "emergency_transfer").contains(disposition)) {
      throw badRequest("Seleccione el destino clínico posterior a la interrupción.");
    }
    Instant interruptedAt =
        command.interruptedAt() == null ? clock.instant() : command.interruptedAt();
    CommandResult result = execute(
        key(patientId, treatmentId, cycle, day),
        "administration_interrupted",
        command.expectedRevision(),
        command.idempotencyKey(),
        command,
        actor,
        current -> {
          ApplicationWorkflowPolicy.interruptAdministration(current.policyState());
          if (current.administrationStartedAt() == null
              || interruptedAt.isBefore(current.administrationStartedAt())) {
            throw badRequest("La hora de interrupción es anterior al inicio.");
          }
          if (interruptedAt.isAfter(clock.instant().plusSeconds(300))) {
            throw badRequest("La hora de interrupción no puede estar en el futuro.");
          }
          ObjectNode data = administrationData(current);
          ObjectNode interruption = mapper.createObjectNode();
          interruption.put("interruptedAt", interruptedAt.toString());
          interruption.put("reason", reason);
          interruption.put("actualDose", actualDose);
          interruption.put("measures", measures);
          interruption.put("patientCondition", patientCondition);
          interruption.put("disposition", disposition);
          interruption.put("recordedByUserId", actor.userId());
          interruption.put("recordedByDisplayName", actor.displayName());
          data.withArray("interruptions").add(interruption);
          data.put("interruptionPending", true);
          data.put("interruptedAt", interruptedAt.toString());
          data.put("interruptionReason", reason);
          data.put("actualDoseAtInterruption", actualDose);
          data.put("interruptionMeasures", measures);
          data.put("interruptionPatientCondition", patientCondition);
          data.put("interruptionDisposition", disposition);
          Instant now = clock.instant();
          changed(workflows.interruptAdministration(
              current.key(), current.revision(), data, actor.userId(), now));
          workflows.synchronizeSessionAdministration(
              current.key(), "withheld", "paused", actor.userId(), now);
        });
    if (!result.idempotentReplay()) {
      EvolutionAppend evolution = appendEvolution(
          patientId,
          treatmentId,
          cycle,
          day,
          "application-administration-interrupted-" + command.idempotencyKey(),
          "Interrupción de administración",
          "Administración interrumpida.\n"
              + "Ciclo " + cycle + " · Día " + day + "\n"
              + "Hora: " + interruptedAt + "\n"
              + "Motivo: " + reason + "\n"
              + "Dosis hasta la interrupción: " + actualDose + "\n"
              + "Medidas: " + measures + "\n"
              + "Condición del paciente: " + patientCondition + "\n"
              + "Destino: " + dispositionLabel(disposition) + "\n"
              + "Registró: " + actor.displayName(),
          actor);
      return result.withEvolution(evolution);
    }
    return result;
  }

  @Transactional
  public CommandResult administrationResolve(
      long patientId, String treatmentId, int cycle, int day,
      AdministrationResolve command, SessionPrincipal actor) {
    requireBody(command);
    requireCommand(command.expectedRevision(), command.idempotencyKey());
    String decision = trim(command.decision()).toLowerCase();
    String notes = trim(command.notes());
    String actualDose = trim(command.actualDose());
    String patientCondition = trim(command.patientCondition());
    if (!Set.of("resume", "terminate").contains(decision)) {
      throw badRequest("Seleccione si corresponde reanudar o cerrar la administración.");
    }
    if (notes.length() < 3) {
      throw badRequest("Documente la decisión clínica.");
    }
    if (patientCondition.length() < 3) {
      throw badRequest("Registre la condición del paciente al resolver.");
    }
    if ("terminate".equals(decision) && actualDose.length() < 2) {
      throw badRequest("Registre la dosis total administrada antes del cierre.");
    }
    Instant resolvedAt = command.resolvedAt() == null ? clock.instant() : command.resolvedAt();
    String action = "resume".equals(decision)
        ? "administration_resumed" : "administration_terminated";
    CommandResult result = execute(
        key(patientId, treatmentId, cycle, day),
        action,
        command.expectedRevision(),
        command.idempotencyKey(),
        command,
        actor,
        current -> {
          ApplicationWorkflowPolicy.resolveAdministration(
              current.policyState(),
              "resume".equals(decision),
              current.preparationExpiresAt() != null
                  && current.preparationExpiresAt().isAfter(clock.instant()));
          ObjectNode data = administrationData(current);
          if (!data.path("interruptionPending").asBoolean(false)) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "La interrupción ya fue resuelta.",
                "INTERRUPTION_ALREADY_RESOLVED");
          }
          Instant interruptedAt = parseInstant(data.path("interruptedAt").asText(""));
          if (interruptedAt != null && resolvedAt.isBefore(interruptedAt)) {
            throw badRequest("La resolución no puede ser anterior a la interrupción.");
          }
          if (resolvedAt.isAfter(clock.instant().plusSeconds(300))) {
            throw badRequest("La hora de resolución no puede estar en el futuro.");
          }
          ObjectNode resolution = mapper.createObjectNode();
          resolution.put("resolvedAt", resolvedAt.toString());
          resolution.put("decision", decision);
          resolution.put("notes", notes);
          resolution.put("actualDose", actualDose);
          resolution.put("patientCondition", patientCondition);
          resolution.put("recordedByUserId", actor.userId());
          resolution.put("recordedByDisplayName", actor.displayName());
          data.withArray("interruptionResolutions").add(resolution);
          data.put("interruptionPending", false);
          data.put("interruptionResolution", decision);
          data.put("interruptionResolutionAt", resolvedAt.toString());
          data.put("interruptionResolutionNotes", notes);
          data.put("interruptionResolutionPatientCondition", patientCondition);
          if (!actualDose.isBlank()) data.put("actualDose", actualDose);
          Instant now = clock.instant();
          boolean resume = "resume".equals(decision);
          changed(workflows.resolveAdministration(
              current.key(), current.revision(), resume, data,
              resolvedAt, actor.userId(), now));
          workflows.synchronizeSessionAdministration(
              current.key(), resume ? "in_progress" : "withheld",
              resume ? "in_progress" : "cancelled",
              actor.userId(), now);
        });
    if (!result.idempotentReplay()) {
      String decisionText = "resume".equals(decision)
          ? "Administración reanudada" : "Administración cerrada sin completar";
      EvolutionAppend evolution = appendEvolution(
          patientId,
          treatmentId,
          cycle,
          day,
          "application-administration-resolution-" + command.idempotencyKey(),
          "Resolución de interrupción",
          decisionText + ".\n"
              + "Ciclo " + cycle + " · Día " + day + "\n"
              + "Hora: " + resolvedAt + "\n"
              + "Decisión: " + notes + "\n"
              + "Dosis total registrada: " + (actualDose.isBlank() ? "Continúa en curso" : actualDose) + "\n"
              + "Condición del paciente: " + patientCondition + "\n"
              + "Registró: " + actor.displayName(),
          actor);
      return result.withEvolution(evolution);
    }
    return result;
  }

  private ObjectNode administrationData(Application current) {
    return current.administrationData().isObject()
        ? (ObjectNode) current.administrationData().deepCopy()
        : mapper.createObjectNode();
  }

  static AdministrationReaction resolveAdministrationReaction(
      JsonNode administrationData,
      boolean reportedReaction,
      String reportedDescription) {
    String description = reportedDescription == null ? "" : reportedDescription.trim();
    JsonNode interruptions = administrationData == null
        ? null
        : administrationData.path("interruptions");
    int interruptionCount =
        interruptions != null && interruptions.isArray() ? interruptions.size() : 0;
    if (interruptionCount == 0) {
      return new AdministrationReaction(
          reportedReaction,
          reportedReaction ? description : "",
          false,
          0);
    }

    List<String> summaries = new ArrayList<>();
    for (int index = 0; index < interruptionCount; index++) {
      JsonNode interruption = interruptions.get(index);
      List<String> details = new ArrayList<>();
      addReactionDetail(details, "Motivo", interruption.path("reason").asText(""));
      addReactionDetail(details, "Medidas", interruption.path("measures").asText(""));
      addReactionDetail(
          details, "Condición", interruption.path("patientCondition").asText(""));
      addReactionDetail(
          details,
          "Destino",
          interruptionDispositionLabel(interruption.path("disposition").asText("")));
      String interruptedAt = interruption.path("interruptedAt").asText("").trim();
      String heading = "Interrupción " + (index + 1)
          + (interruptedAt.isBlank() ? "" : " (" + interruptedAt + ")");
      summaries.add(
          details.isEmpty()
              ? heading + ": incidencia registrada durante la administración."
              : heading + ": " + String.join("; ", details) + ".");
    }
    String history = String.join(" ", summaries);
    String effectiveDescription = description.isBlank()
        ? history
        : description + "\nAntecedentes de la administración: " + history;
    return new AdministrationReaction(true, effectiveDescription, true, interruptionCount);
  }

  private static void addReactionDetail(List<String> details, String label, String value) {
    String normalized = value == null ? "" : value.trim();
    if (!normalized.isBlank()) details.add(label + ": " + normalized);
  }

  private static String interruptionDispositionLabel(String disposition) {
    return switch (disposition == null ? "" : disposition.trim().toLowerCase()) {
      case "medical_review" -> "Evaluación médica inmediata";
      case "emergency_transfer" -> "Derivación a guardia/emergencia";
      case "observation" -> "Observación en Hospital de día";
      default -> "";
    };
  }

  private Instant parseInstant(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  private String dispositionLabel(String disposition) {
    return switch (disposition) {
      case "medical_review" -> "Evaluación médica inmediata";
      case "emergency_transfer" -> "Derivación a guardia/emergencia";
      default -> "Observación en Hospital de día";
    };
  }

  private void reserve(
      Application current, StockReservation command, SessionPrincipal actor) {
    ApplicationWorkflowPolicy.reserveStock(current.policyState());
    String source = normalizeSource(command.medicationSource(), false);
    if (source.isBlank()) source = current.medicationSource();
    if (!source.equals(current.medicationSource())) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "La fuente de medicación cambió; vuelva a validar la orden en Farmacia.",
          "MEDICATION_SOURCE_CHANGED");
    }
    String method = trim(command.verificationMethod()).toLowerCase();
    List<StockComponent> components = command.components() == null
        ? List.of() : command.components();
    if (method.isBlank()) method = components.isEmpty() ? "manual" : "inventory";
    if (!Set.of("manual", "inventory").contains(method)) {
      throw badRequest("El método de verificación de stock es inválido.");
    }
    String notes = trim(command.notes());
    if ("manual".equals(method) && notes.length() < 10) {
      throw badRequest(
          "La constatación manual exige una nota de al menos 10 caracteres.");
    }
    if (components.isEmpty()) components = componentsFromDrugs(current.applicationDrugs());
    if (components.isEmpty()) {
      throw badRequest("El protocolo no contiene componentes reservables.");
    }
    ApplicationComponentValidator.validateStockComponents(
        current.applicationDrugs(), components);
    Instant now = clock.instant();
    for (StockComponent component : components) {
      String drugName = trim(component.drugName());
      if (drugName.isBlank()) throw badRequest("Cada componente debe indicar la droga.");
      String componentKey = trim(component.componentKey());
      BigDecimal quantity = component.requestedQuantity();
      String unit = trim(component.unit());
      if (quantity != null && quantity.signum() <= 0) {
        throw badRequest("La cantidad solicitada debe ser mayor que cero.");
      }
      if ("manual".equals(method) && (quantity == null || unit.isBlank())) {
        throw badRequest(
            "La constatación manual exige cantidad y unidad para " + drugName + ".");
      }
      if ("inventory".equals(method)) {
        if (component.inventoryLotId() == null || quantity == null || unit.isBlank()) {
          throw badRequest(
              "Una reserva de inventario exige lote, cantidad numérica y unidad.");
        }
        boolean available = workflows.reserveInventory(
            component.inventoryLotId(), trim(component.drugId()), drugName,
            quantity, unit, actor.userId(), now);
        if (!available) {
          throw new ApiException(
              HttpStatus.CONFLICT,
              "El lote no existe, está vencido o no posee cantidad disponible para " + drugName + ".",
              "INSUFFICIENT_STOCK");
        }
      }
      workflows.insertReservation(
          UUID.randomUUID(), current.key(), componentKey, trim(component.drugId()),
          drugName, quantity, trim(component.requestedQuantityText()), unit,
          "center_stock", "reserved", method, component.inventoryLotId(),
          notes, actor.userId(), now);
    }
    changed(workflows.updateReservationStatus(
        current.key(), current.revision(), "reserved", notes,
        actor.userId(), now));
  }

  private void completePreparation(
      Application current, PreparationComplete command, SessionPrincipal actor) {
    ApplicationWorkflowPolicy.completePreparation(current.policyState());
    long verifiedBy = workflows.resolveEnabledUser(
            trim(command.verifiedBy()), "application.preparation.manage")
        .orElseThrow(() -> badRequest(
            "Seleccione un segundo profesional habilitado para verificar la preparación."));
    if (verifiedBy == actor.userId()) {
      throw badRequest("La preparación debe ser verificada por otro profesional.");
    }
    List<Reservation> reservations = workflows.activeReservations(current.key()).stream()
        .filter(item -> "reserved".equals(item.status()))
        .toList();
    if ("center_stock".equals(current.medicationSource()) && reservations.isEmpty()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "No existe una reserva activa que respalde la preparación.",
          "STOCK_RESERVATION_REQUIRED");
    }
    var resolvedPreparations = ApplicationComponentValidator.resolvePreparations(
        current.applicationDrugs(), command.preparations());
    Set<UUID> usedReservations = new HashSet<>();
    ArrayNode trace = mapper.createArrayNode();
    Instant now = clock.instant();
    int minimumTtl = Integer.MAX_VALUE;
    for (var resolvedPreparation : resolvedPreparations) {
      String componentKey = resolvedPreparation.componentKey();
      Preparation item = resolvedPreparation.preparation();
      String drugName = trim(item.drugName());
      String lot = trim(item.lot());
      String unit = trim(item.unit());
      String diluent = trim(item.diluent());
      String finalVolume = trim(item.finalVolume());
      String concentration = trim(item.concentration());
      if (drugName.isBlank() || lot.isBlank() || item.expiryDate() == null
          || item.quantity() == null || unit.isBlank() || diluent.isBlank()
          || finalVolume.isBlank() || concentration.isBlank()) {
        throw badRequest(
            "Cada preparación debe indicar droga, lote, vencimiento, cantidad, unidad, "
                + "diluyente, volumen y concentración.");
      }
      if (item.expiryDate().isBefore(LocalDate.now(clock))) {
        throw badRequest("No se puede utilizar un lote vencido.");
      }
      int ttl = item.ttlMinutes() == null ? 0 : item.ttlMinutes();
      if (ttl < 1 || ttl > 10080) {
        throw badRequest("El TTL debe estar entre 1 minuto y 7 días.");
      }
      if (item.quantity() != null && item.quantity().signum() <= 0) {
        throw badRequest("La cantidad preparada debe ser mayor que cero.");
      }
      Reservation reservation =
          matchReservation(componentKey, item, reservations, usedReservations);
      if ("center_stock".equals(current.medicationSource()) && reservation == null) {
        throw new ApiException(
            HttpStatus.CONFLICT,
            "La preparación de " + drugName + " no coincide con una reserva activa.",
            "PREPARATION_WITHOUT_RESERVATION");
      }
      if (reservation != null
          && reservation.inventoryLotId() != null
          && item.inventoryLotId() != null
          && !reservation.inventoryLotId().equals(item.inventoryLotId())) {
        throw badRequest("El lote preparado no coincide con el lote reservado.");
      }
      if (reservation != null) {
        validateReservationPreparation(componentKey, reservation, item);
      }
      UUID reservationId = reservation == null ? null : reservation.id();
      Long inventoryLotId = reservation != null && reservation.inventoryLotId() != null
          ? reservation.inventoryLotId() : item.inventoryLotId();
      if (inventoryLotId != null) {
        validateInventoryPreparation(inventoryLotId, reservation, item);
      }
      workflows.insertPreparationLot(
          UUID.randomUUID(), current.key(), componentKey, reservationId, inventoryLotId,
          drugName, lot, item.expiryDate(), item.quantity(), trim(item.quantityText()),
          unit, diluent, finalVolume, concentration, ttl, actor.userId(), verifiedBy, now);
      if (reservationId != null) usedReservations.add(reservationId);
      minimumTtl = Math.min(minimumTtl, ttl);
      ObjectNode traceItem = mapper.valueToTree(item);
      traceItem.put("componentKey", componentKey);
      trace.add(traceItem);
    }
    if ("center_stock".equals(current.medicationSource())
        && usedReservations.size() != reservations.size()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "Todas las drogas reservadas deben quedar vinculadas a la preparación.",
          "INCOMPLETE_PREPARATION_TRACE");
    }
    Instant expiresAt = now.plusSeconds((long) minimumTtl * 60);
    ObjectNode data = mapper.createObjectNode();
    data.set("preparations", trace);
    data.put("notes", trim(command.notes()));
    data.put("preparedAt", now.toString());
    data.put("expiresAt", expiresAt.toString());
    data.put("labelRequired", true);
    data.put("preparedByUserId", actor.userId());
    data.put("preparedByDisplayName", actor.displayName());
    data.put("verifiedByUserId", verifiedBy);
    data.put("verifiedByDisplayName", workflows.userDisplayName(verifiedBy));
    workflows.consumeReservations(current.key(), actor.userId(), now);
    changed(workflows.updatePreparation(
        current.key(), current.revision(), "prepared", data,
        expiresAt, verifiedBy, actor.userId(), now));
    workflows.synchronizeSessionPreparation(
        current.key(), "ready", actor.userId(), now);
  }

  private Reservation matchReservation(
      String componentKey,
      Preparation item,
      List<Reservation> reservations,
      Set<UUID> used) {
    String explicit = trim(item.reservationId());
    if (!explicit.isBlank()) {
      try {
        UUID id = UUID.fromString(explicit);
        return reservations.stream()
            .filter(row -> row.id().equals(id)
                && componentKey.equals(row.componentKey())
                && !used.contains(row.id()))
            .findFirst()
            .orElseThrow(() -> badRequest(
                "La reserva informada no corresponde al componente " + componentKey + "."));
      } catch (IllegalArgumentException invalid) {
        throw badRequest("El identificador de reserva es inválido.");
      }
    }
    return reservations.stream()
        .filter(row -> !used.contains(row.id()))
        .filter(row -> componentKey.equals(row.componentKey()))
        .findFirst()
        .orElse(null);
  }

  private void validateReservationPreparation(
      String componentKey, Reservation reservation, Preparation preparation) {
    if (!componentKey.equals(reservation.componentKey())
        || !reservation.drugName().equalsIgnoreCase(trim(preparation.drugName()))) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "La preparación no coincide con la droga reservada para " + componentKey + ".",
          "PREPARATION_COMPONENT_MISMATCH");
    }
    if (reservation.requestedQuantity() == null
        || preparation.quantity() == null
        || preparation.quantity().compareTo(reservation.requestedQuantity()) != 0) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "La cantidad preparada no coincide con la cantidad reservada para "
              + componentKey + ".",
          "PREPARATION_COMPONENT_MISMATCH");
    }
    if (!trim(reservation.unit()).equalsIgnoreCase(trim(preparation.unit()))) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "La unidad preparada no coincide con la unidad reservada para "
              + componentKey + ".",
          "PREPARATION_COMPONENT_MISMATCH");
    }
  }

  private void validateInventoryPreparation(
      long inventoryLotId, Reservation reservation, Preparation preparation) {
    InventoryLot lot = workflows.inventoryLot(inventoryLotId)
        .orElseThrow(() -> new ApiException(
            HttpStatus.CONFLICT,
            "El lote de inventario vinculado ya no existe.",
            "INVENTORY_LOT_MISMATCH"));
    if (reservation == null || reservation.inventoryLotId() == null
        || reservation.inventoryLotId() != inventoryLotId) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "El lote preparado no está respaldado por la reserva de esta aplicación.",
          "INVENTORY_LOT_MISMATCH");
    }
    if (!lot.lotNumber().equalsIgnoreCase(trim(preparation.lot()))
        || !lot.expirationDate().equals(preparation.expiryDate())
        || !lot.unit().equalsIgnoreCase(trim(preparation.unit()))
        || !lot.drugName().equalsIgnoreCase(trim(preparation.drugName()))) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "Droga, lote, vencimiento y unidad deben coincidir con el inventario reservado.",
          "INVENTORY_LOT_MISMATCH");
    }
    if (preparation.quantity() == null || reservation.reservedQuantity() == null
        || preparation.quantity().compareTo(reservation.reservedQuantity()) != 0
        || lot.quantityReserved().compareTo(reservation.reservedQuantity()) < 0) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "La cantidad preparada debe coincidir con la cantidad reservada.",
          "INVENTORY_QUANTITY_MISMATCH");
    }
  }

  private List<StockComponent> componentsFromDrugs(JsonNode drugs) {
    return ApplicationComponentValidator.componentsFromDrugs(drugs);
  }

  private CommandResult execute(
      Key key, String action, long expectedRevision, String idempotencyKey,
      Object command, SessionPrincipal actor, Mutation mutation) {
    logistics.synchronizeTreatment(key.treatmentId());
    workflows.ensureWorkflowRows();
    Application current = workflows.lock(key)
        .orElseThrow(() -> new ApiException(
            HttpStatus.NOT_FOUND,
            "No existe esa aplicación del tratamiento."));
    var previous = workflows.event(key, idempotencyKey).orElse(null);
    if (previous != null) {
      if (!action.equals(previous.action())) {
        throw new ApiException(
            HttpStatus.CONFLICT,
            "La clave de idempotencia ya fue utilizada para otra acción.",
            "IDEMPOTENCY_KEY_REUSED");
      }
      JsonNode replay = withOwnAuditEvent(previous);
      workflows.updateEventAfter(key, idempotencyKey, replay);
      return new CommandResult(replay, true, null, null);
    }
    if (current.revision() != expectedRevision) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "La aplicación fue modificada por otro usuario.",
          "VERSION_CONFLICT");
    }
    JsonNode before = mapper.valueToTree(view(current, true));
    mutation.apply(current);
    Application updated = require(key);
    JsonNode after = mapper.valueToTree(view(updated, true));
    Instant eventAt = clock.instant();
    workflows.insertEvent(
        key, action, idempotencyKey, actor.userId(), expectedRevision,
        updated.revision(), mapper.valueToTree(command), before, after, eventAt);
    WorkflowEvent stored = workflows.event(key, idempotencyKey).orElseThrow();
    JsonNode auditedAfter = withOwnAuditEvent(stored);
    workflows.updateEventAfter(key, idempotencyKey, auditedAfter);
    return new CommandResult(auditedAfter, false, null, null);
  }

  private JsonNode withOwnAuditEvent(WorkflowEvent event) {
    ObjectNode snapshot = event.after().isObject()
        ? (ObjectNode) event.after().deepCopy()
        : mapper.createObjectNode();
    JsonNode existing = snapshot.get("auditTrail");
    ArrayNode audit = existing != null && existing.isArray()
        ? (ArrayNode) existing
        : mapper.createArrayNode();
    if (existing == null || !existing.isArray()) {
      snapshot.set("auditTrail", audit);
    }
    boolean present = false;
    for (JsonNode item : audit) {
      if (Long.toString(event.id()).equals(item.path("id").asText())) {
        present = true;
        break;
      }
    }
    if (!present) {
      ObjectNode item = mapper.createObjectNode();
      item.put("id", Long.toString(event.id()));
      item.put("action", event.action());
      item.put("expectedRevision", event.expectedRevision());
      item.put("resultingRevision", event.resultingRevision());
      item.put("occurredAt", event.occurredAt().toString());
      item.put("actor", event.actorName());
      audit.add(item);
    }
    return snapshot;
  }

  private EvolutionAppend appendEvolution(
      long patientId,
      String treatmentId,
      int cycle,
      int day,
      String event,
      String reason,
      String text,
      SessionPrincipal actor) {
    Instant now = clock.instant();
    ObjectNode evolution = mapper.createObjectNode();
    evolution.put(
        "id",
        event + "-" + patientId + "-" + treatmentId + "-" + cycle + "-" + day);
    evolution.put("date", LocalDate.now(clock).toString());
    evolution.put("datePrecision", "day");
    evolution.put("author", actor.displayName());
    evolution.put("reason", reason);
    evolution.put("specialty", "Oncología / Hospital de día");
    evolution.put("text", text);
    evolution.put("highlighted", true);
    evolution.put("immutable", true);
    evolution.put("createdAt", now.toString());
    evolution.put("updatedAt", now.toString());
    evolution.set("attachments", mapper.createArrayNode());
    evolution.set("linkedStudyIds", mapper.createArrayNode());
    ObjectNode source = evolution.putObject("sourceRef");
    source.put("kind", "application-workflow");
    source.put("event", event);
    source.put("treatmentId", treatmentId);
    source.put("cycleNumber", cycle);
    source.put("applicationDay", day);
    return documents.appendImmutableEvolution(patientId, evolution, actor.userId());
  }

  private Application require(Key key) {
    return workflows.find(key)
        .orElseThrow(() -> new ApiException(
            HttpStatus.NOT_FOUND,
            "No existe esa aplicación del tratamiento."));
  }

  private Map<String, Object> view(Application item, boolean includeReservations) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("patientId", Long.toString(item.key().patientId()));
    result.put("treatmentId", item.key().treatmentId());
    result.put("cycleNumber", item.key().cycleNumber());
    result.put("applicationDay", item.key().applicationDay());
    result.put("patientName", item.patientName());
    result.put("patientDni", item.patientDni());
    result.put("dni", item.patientDni());
    result.put("medicalRecord", item.medicalRecord());
    result.put("insurance", item.insurance());
    result.put("affiliateNumber", item.affiliateNumber());
    result.put("diagnosis", item.diagnosis());
    result.put("scheme", item.scheme());
    result.put("treatmentType", item.treatmentType());
    result.put("totalCycles", item.totalCycles());
    result.put("plannedDate", item.plannedDate() == null ? null : item.plannedDate().toString());
    result.put("durationMinutes", item.durationMinutes());
    result.put("durationSource", item.durationSource());
    result.put("drugScheme", item.drugSummary());
    result.put("applicationDrugs", item.applicationDrugs());
    result.put("workflowStatus", item.workflowStatus());
    result.put("currentStep", currentStep(item));
    result.put("prescriptionStatus", item.prescriptionStatus());
    result.put("medicationSource", item.medicationSource());
    result.put("patientMustBringMedication",
        "patient_to_bring".equals(item.medicationSource()));
    result.put("medicationReady",
        ApplicationWorkflowPolicy.medicationReady(item.policyState()));
    result.put("pharmacyValidationStatus", item.pharmacyValidationStatus());
    result.put("pharmacyValidationNotes", item.pharmacyValidationNotes());
    result.put("pharmacyValidatedAt", value(item.pharmacyValidatedAt()));
    result.put("pharmacyValidationTraceable",
        item.pharmacyValidatedAt() != null
            && "approved".equals(item.pharmacyValidationStatus()));
    result.put("stockReservationStatus", item.stockReservationStatus());
    result.put("stockReservationNotes", item.stockReservationNotes());
    result.put("stockReservedAt", value(item.stockReservedAt()));
    result.put("stockReleasedAt", value(item.stockReleasedAt()));
    result.put("clinicalAuthorizationStatus", item.clinicalAuthorizationStatus());
    result.put("clinicalAuthorizationReason", item.clinicalAuthorizationReason());
    result.put("clinicalAssessment", item.clinicalAssessment());
    result.put("clinicallyAuthorizedAt", value(item.clinicallyAuthorizedAt()));
    result.put("preparationStatus", item.preparationStatus());
    result.put("preparationData", item.preparationData());
    result.put("preparationStartedAt", value(item.preparationStartedAt()));
    result.put("preparedByUserId", item.preparedBy());
    result.put("preparedByDisplayName", item.preparedByName());
    result.put("preparationVerifiedByUserId", item.preparationVerifiedBy());
    result.put("preparationVerifiedByDisplayName", item.preparationVerifiedByName());
    result.put("preparedAt", value(item.preparedAt()));
    result.put("preparationReleasedAt", value(item.preparationReleasedAt()));
    result.put("preparationExpiresAt", value(item.preparationExpiresAt()));
    result.put("preparationRestartCount", item.preparationRestartCount());
    result.put("administrationStatus", item.administrationStatus());
    result.put("administrationData", item.administrationData());
    result.put("administrationStartedAt", value(item.administrationStartedAt()));
    result.put("administrationCompletedAt", value(item.administrationCompletedAt()));
    Map<String, Object> appointment = new LinkedHashMap<>();
    appointment.put("id", item.sessionId() == null ? null : Long.toString(item.sessionId()));
    appointment.put("scheduledAt", value(item.scheduledAt()));
    appointment.put("chair", item.chair());
    appointment.put("confirmed", item.appointmentConfirmed());
    appointment.put("clinicalStatus", item.sessionClinicalStatus());
    appointment.put("pharmacyStatus", item.sessionPharmacyStatus());
    appointment.put("administrationStatus", item.sessionAdministrationStatus());
    appointment.put("readyToday",
        item.hasConfirmedAppointmentOn(LocalDate.now(clock), clock.getZone()));
    result.put("appointment", appointment);
    if (includeReservations) {
      result.put("stockReservations", workflows.reservations(item.key()).stream()
          .map(this::reservationView)
          .toList());
      result.put("auditTrail", workflows.events(item.key()).stream().map(event -> Map.of(
          "id", Long.toString(event.id()),
          "action", event.action(),
          "expectedRevision", event.expectedRevision(),
          "resultingRevision", event.resultingRevision(),
          "occurredAt", event.occurredAt().toString(),
          "actor", event.actorName())).toList());
    }
    result.put("revision", item.revision());
    result.put("updatedAt", item.updatedAt().toString());
    return result;
  }

  private Map<String, Object> reservationView(Reservation row) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", row.id().toString());
    result.put("componentKey", row.componentKey());
    result.put("drugId", row.drugId());
    result.put("drugName", row.drugName());
    result.put("requestedQuantity", row.requestedQuantity());
    result.put("requestedQuantityText", row.requestedQuantityText());
    result.put("reservedQuantity", row.reservedQuantity());
    result.put("unit", row.unit());
    result.put("medicationSource", row.source());
    result.put("status", row.status());
    result.put("verificationMethod", row.verificationMethod());
    result.put("inventoryLotId", row.inventoryLotId());
    result.put("notes", row.notes());
    return result;
  }

  private String currentStep(Application item) {
    if ("completed".equals(item.administrationStatus())) return "completed";
    if ("withheld".equals(item.administrationStatus())
        && item.administrationData().path("interruptionPending").asBoolean(false)) {
      return "interruption";
    }
    if ("cancelled".equals(item.workflowStatus())) return "cancelled";
    if ("in_progress".equals(item.administrationStatus())) return "administration";
    if ("released".equals(item.preparationStatus())) return "ready_to_administer";
    if (Set.of("in_preparation", "prepared").contains(item.preparationStatus())) return "preparation";
    if ("failed".equals(item.clinicalAuthorizationStatus())) return "reschedule";
    if ("passed".equals(item.clinicalAuthorizationStatus())) return "preparation";
    if (item.hasActiveAppointment()) return "triage";
    if (ApplicationWorkflowPolicy.medicationReady(item.policyState())) return "schedule";
    if ("approved".equals(item.pharmacyValidationStatus())
        && "patient_to_bring".equals(item.medicationSource())) return "schedule";
    if ("approved".equals(item.pharmacyValidationStatus())) return "medication";
    return "pharmacy_validation";
  }

  private String normalizeSource(String source, boolean allowEmpty) {
    String normalized = trim(source).toLowerCase();
    if (normalized.isBlank() && allowEmpty) return "";
    if (!normalized.isBlank() && !ApplicationWorkflowPolicy.MEDICATION_SOURCES.contains(normalized)) {
      throw badRequest("La fuente o custodia de la medicación es inválida.");
    }
    return normalized;
  }

  private Key key(long patientId, String treatmentId, int cycle, int day) {
    if (patientId < 1 || treatmentId == null || treatmentId.isBlank()
        || cycle < 1 || cycle > 500
        || !DayHospitalApplicationPolicy.isValidApplicationDay(day)) {
      throw badRequest("La identificación de la aplicación es inválida.");
    }
    return new Key(patientId, treatmentId.trim(), cycle, day);
  }

  private void requireBody(Object command) {
    if (command == null) {
      throw badRequest("El cuerpo JSON de la solicitud es obligatorio.");
    }
  }

  private void requireCommand(Long expectedRevision, String idempotencyKey) {
    if (expectedRevision == null || expectedRevision < 1) {
      throw badRequest("Informe expectedRevision para proteger cambios concurrentes.");
    }
    String key = trim(idempotencyKey);
    if (key.length() < 8 || key.length() > 128
        || !key.matches("[A-Za-z0-9._:-]+")) {
      throw badRequest(
          "idempotencyKey debe tener entre 8 y 128 caracteres seguros.");
    }
  }

  private boolean isObject(JsonNode value) {
    return value != null && value.isObject();
  }

  private void validateTriage(ClinicalAuthorization command, boolean passed) {
    validateNumber(command.vitalSigns(), 1, 500, "weightKg", "pesoKg", "weight");
    validateNumber(command.vitalSigns(), 30, 45, "temperatureC", "temperature", "temperatura");
    validateNumber(command.vitalSigns(), 20, 250, "heartRate", "pulse", "frecuenciaCardiaca");
    validateNumber(command.vitalSigns(), 50, 300, "systolic", "systolicPressure", "presionSistolica");
    validateNumber(command.vitalSigns(), 30, 200, "diastolic", "diastolicPressure", "presionDiastolica");
    validateNumber(command.vitalSigns(), 50, 100, "oxygenSaturation", "spo2", "saturacion");
    String laboratoryDate = firstText(
        command.laboratory(), "date", "sampleDate", "laboratoryDate", "fecha");
    if (passed && laboratoryDate.isBlank()) {
      throw badRequest("Informe la fecha del laboratorio para emitir PASS.");
    }
    if (!laboratoryDate.isBlank()) {
      try {
        LocalDate parsed = LocalDate.parse(laboratoryDate);
        if (parsed.isAfter(LocalDate.now(clock))) {
          throw badRequest("La fecha del laboratorio no puede estar en el futuro.");
        }
      } catch (DateTimeParseException invalid) {
        throw badRequest("La fecha del laboratorio debe tener formato AAAA-MM-DD.");
      }
    }
    if (!passed) return;
    requireNumber(command.laboratory(), 0, 100000, "neutrophils");
    requireNumber(command.laboratory(), 0, 2000000, "platelets");
    requireNumber(command.laboratory(), 0.01, 50, "creatinine");
    requireNumber(command.vitalSigns(), 1, 500, "weightKg");
    requireNumber(command.vitalSigns(), 30, 45, "temperatureC");
    requireNumber(command.toxicity(), 0, 5, "grade");
    String pressure = firstText(
        command.vitalSigns(), "bloodPressure", "pressure", "presionArterial");
    if (!pressure.matches("\\d{2,3}\\s*/\\s*\\d{2,3}")) {
      throw badRequest("Informe la presión arterial en formato sistólica/diastólica.");
    }
    String[] pressureParts = pressure.split("/");
    int systolic = Integer.parseInt(pressureParts[0].trim());
    int diastolic = Integer.parseInt(pressureParts[1].trim());
    if (systolic < 50 || systolic > 300 || diastolic < 30 || diastolic > 200) {
      throw badRequest("La presión arterial está fuera de rango.");
    }
  }

  private List<String> triageSafetyAlerts(ClinicalAuthorization command) {
    List<String> alerts = new ArrayList<>();
    double neutrophils = requireNumber(command.laboratory(), 0, 100000, "neutrophils");
    double platelets = requireNumber(command.laboratory(), 0, 2000000, "platelets");
    double temperature = requireNumber(command.vitalSigns(), 30, 45, "temperatureC");
    double toxicity = requireNumber(command.toxicity(), 0, 5, "grade");
    if (neutrophils < 1000) alerts.add("neutrófilos < 1.000/mm3");
    if (platelets < 75000) alerts.add("plaquetas < 75.000/mm3");
    if (temperature >= 38) alerts.add("temperatura ≥ 38 °C");
    if (toxicity >= 3) alerts.add("toxicidad grado ≥ 3");
    JsonNode saturation = command.vitalSigns().path("oxygenSaturation");
    if (!saturation.isMissingNode() && !saturation.isNull()
        && !saturation.asText("").isBlank()) {
      try {
        double value = Double.parseDouble(saturation.asText("").replace(',', '.'));
        if (value < 92) alerts.add("saturación < 92%");
      } catch (NumberFormatException ignored) {
        // validateTriage informa el error de formato antes de llegar a este punto.
      }
    }
    return alerts;
  }

  private void validatePrescriptionDrugs(JsonNode drugs) {
    if (drugs == null || !drugs.isArray() || drugs.isEmpty()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "La orden no contiene drogas para esta aplicación.",
          "INCOMPLETE_PHARMACY_ORDER");
    }
    List<String> incomplete = new ArrayList<>();
    for (JsonNode drug : drugs) {
      String name = firstText(drug, "drugName", "name", "droga", "genericName");
      String dose = firstText(
          drug, "prescribedDoseText", "calculatedDoseText", "totalDoseText",
          "dose", "dosis", "dosisDiaria", "calculatedDose");
      String unit = firstText(drug, "doseUnit", "unidad", "unidadDosis");
      String route = firstText(drug, "route", "via", "viaAdministracion");
      String method = normalized(firstText(
          drug, "calculationMethod", "calculoDosis", "doseCalculation"));
      String calculationStatus = firstText(drug, "doseCalculationStatus", "calculationStatus");
      boolean patientSpecific = method.contains("superficie")
          || method.equals("peso") || method.contains("calvert");
      if (name.isBlank() || dose.isBlank() || unit.isBlank() || route.isBlank()
          || (patientSpecific && !"calculated_from_patient".equals(calculationStatus))) {
        String label = name.isBlank() ? "droga sin identificar" : name;
        if (patientSpecific && !"calculated_from_patient".equals(calculationStatus)) {
          incomplete.add(label + " (dosis individual no calculada)");
        } else if (unit.isBlank()) {
          incomplete.add(label + " (unidad no configurada)");
        } else {
          incomplete.add(label);
        }
      }
    }
    if (!incomplete.isEmpty()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "Complete dosis, unidad y vía antes de validar: "
              + String.join(", ", incomplete)
              + ". Corrija el protocolo desde Configuración > Protocolos.",
          "INCOMPLETE_PHARMACY_ORDER");
    }
  }

  private void validateNumber(
      JsonNode object, double minimum, double maximum, String... fields) {
    for (String field : fields) {
      JsonNode value = object.path(field);
      if (value.isMissingNode() || value.isNull() || value.asText("").isBlank()) continue;
      try {
        double number = Double.parseDouble(value.asText("").replace(',', '.'));
        if (!Double.isFinite(number) || number < minimum || number > maximum) {
          throw new NumberFormatException();
        }
      } catch (NumberFormatException invalid) {
        throw badRequest("El valor de " + field + " está fuera de rango.");
      }
      return;
    }
  }

  private double requireNumber(
      JsonNode object, double minimum, double maximum, String field) {
    JsonNode value = object.path(field);
    if (value.isMissingNode() || value.isNull() || value.asText("").isBlank()) {
      throw badRequest("Falta completar " + field + " para emitir PASS.");
    }
    try {
      double number = Double.parseDouble(value.asText("").replace(',', '.'));
      if (!Double.isFinite(number) || number < minimum || number > maximum) {
        throw new NumberFormatException();
      }
      return number;
    } catch (NumberFormatException invalid) {
      throw badRequest("El valor de " + field + " está fuera de rango.");
    }
  }

  private String firstText(JsonNode node, String... fields) {
    for (String field : fields) {
      String value = node.path(field).asText("").trim();
      if (!value.isBlank()) return value;
    }
    return "";
  }

  private String normalized(String value) {
    return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
  }

  private String trim(String value) {
    return value == null ? "" : value.trim();
  }

  private String htmlEscape(String value) {
    return (value == null ? "" : value)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  private String value(Instant value) {
    return value == null ? null : value.toString();
  }

  private void changed(boolean changed) {
    if (!changed) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "La aplicación fue modificada por otro usuario.",
          "VERSION_CONFLICT");
    }
  }

  private ApiException badRequest(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, message);
  }

  @FunctionalInterface
  private interface Mutation {
    void apply(Application current);
  }

  public record CommandResult(
      JsonNode workflow,
      boolean idempotentReplay,
      JsonNode evolution,
      Long documentRevision) {
    CommandResult withEvolution(EvolutionAppend appended) {
      return new CommandResult(
          workflow, idempotentReplay, appended.evolution(), appended.revision());
    }
  }

  record AdministrationReaction(
      boolean occurred,
      String description,
      boolean derivedFromInterruptions,
      int interruptionCount) {
  }
}
