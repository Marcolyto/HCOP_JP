package ar.com.hexium.hcop.infusion.infrastructure.persistence;

import ar.com.hexium.hcop.infusion.application.port.in.ApplicationWorkflowUseCase.AdministrationCompleteCommand;
import ar.com.hexium.hcop.infusion.application.port.in.ApplicationWorkflowUseCase.AdministrationInterruptCommand;
import ar.com.hexium.hcop.infusion.application.port.in.ApplicationWorkflowUseCase.AdministrationResolveCommand;
import ar.com.hexium.hcop.infusion.application.port.in.ApplicationWorkflowUseCase.AdministrationStartCommand;
import ar.com.hexium.hcop.infusion.application.port.in.ApplicationWorkflowUseCase.BasicCommand;
import ar.com.hexium.hcop.infusion.application.port.in.ApplicationWorkflowUseCase.ClinicalAuthorizationCommand;
import ar.com.hexium.hcop.infusion.application.port.in.ApplicationWorkflowUseCase.CommandResult;
import ar.com.hexium.hcop.infusion.application.port.in.ApplicationWorkflowUseCase.PharmacyValidationCommand;
import ar.com.hexium.hcop.infusion.application.port.in.ApplicationWorkflowUseCase.PreparationCompleteCommand;
import ar.com.hexium.hcop.infusion.application.port.in.ApplicationWorkflowUseCase.PreparationInput;
import ar.com.hexium.hcop.infusion.application.port.in.ApplicationWorkflowUseCase.StockComponentInput;
import ar.com.hexium.hcop.infusion.application.port.in.ApplicationWorkflowUseCase.StockReservationCommand;
import ar.com.hexium.hcop.infusion.application.port.out.ApplicationWorkflowStore;
import ar.com.hexium.hcop.infusion.application.service.InfusionFailure;
import ar.com.hexium.hcop.infusion.domain.ApplicationWorkflowPolicy;
import ar.com.hexium.hcop.infusion.domain.ApplicationWorkflowPolicy.Violation;
import ar.com.hexium.hcop.patient.application.port.in.PatientDocumentUseCase;
import ar.com.hexium.hcop.patient.application.service.PatientFailure;
import ar.com.hexium.hcop.patient.domain.EvolutionAppend;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Circuito auditable por aplicación — fusiona lo que antes eran
 * {@code ApplicationWorkflowRepository} (SQL) y {@code ApplicationWorkflowService}
 * (orquestación) en un único adapter, mismo criterio que {@code treatment.PostgresTreatmentStore}
 * cuando la complejidad real está entrelazada con SQL. La validación pura vive en
 * {@code domain.ApplicationWorkflowPolicy} — este adapter la invoca y traduce cada
 * {@link Violation} a {@code InfusionFailure} en el borde (infraestructura, no application).
 */
@Repository
public class PostgresApplicationWorkflowStore implements ApplicationWorkflowStore {
  private static final Set<String> QUEUES =
      Set.of("applications", "pharmacy", "triage", "preparation", "administration");

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final PatientDocumentUseCase documents;

  public PostgresApplicationWorkflowStore(
      JdbcTemplate jdbc, ObjectMapper mapper, Clock clock, PatientDocumentUseCase documents) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.clock = clock;
    this.documents = documents;
  }

  // ---------- puerto público (antes ApplicationWorkflowService) ----------

  @Override
  @Transactional
  public List<Map<String, Object>> list(
      String queue, LocalDate date, String query, String medicationSource) {
    String normalizedQueue = queue == null ? "" : queue.trim().toLowerCase();
    if (!QUEUES.contains(normalizedQueue)) {
      throw new InfusionFailure(InfusionFailure.Type.INVALID, "La cola operativa es inválida.");
    }
    String source = normalizeSource(medicationSource, true);
    ensureWorkflowRows();
    LocalDate effectiveDate = "pharmacy".equals(normalizedQueue)
        ? date
        : date == null ? LocalDate.now(clock) : date;
    return listApplications(normalizedQueue, effectiveDate, query, source).stream()
        .map(item -> view(item, false))
        .toList();
  }

  @Override
  @Transactional
  public Map<String, Object> get(long patientId, String treatmentId, int cycle, int day) {
    Key key = key(patientId, treatmentId, cycle, day);
    ensureWorkflowRows();
    return view(require(key), true);
  }

  @Override
  @Transactional
  public String preparationLabel(long patientId, String treatmentId, int cycle, int day) {
    Key key = key(patientId, treatmentId, cycle, day);
    ensureWorkflowRows();
    Application application = require(key);
    if (!Set.of("prepared", "released").contains(application.preparationStatus())
        && !"completed".equals(application.administrationStatus())) {
      throw new InfusionFailure(
          InfusionFailure.Type.CONFLICT,
          "La etiqueta se habilita después de registrar la preparación.",
          "PREPARATION_NOT_READY");
    }
    var lots = preparationLots(key);
    if (lots.isEmpty()) {
      throw new InfusionFailure(
          InfusionFailure.Type.CONFLICT,
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

  @Override
  @Transactional
  public CommandResult pharmacyValidation(
      long patientId, String treatmentId, int cycle, int day, PharmacyValidationCommand command,
      long actorId, String actorDisplayName) {
    requireBody(command);
    requireCommand(command.expectedRevision(), command.idempotencyKey());
    if (command.validated() == null) {
      throw badRequest("Indique si Farmacia valida o rechaza la orden.");
    }
    String action = command.validated() ? "pharmacy_validation_approved" : "pharmacy_validation_rejected";
    return execute(key(patientId, treatmentId, cycle, day), action,
        command.expectedRevision(), command.idempotencyKey(), command, actorId,
        current -> {
          check(ApplicationWorkflowPolicy.pharmacyValidation(current.policyState(), command.validated()));
          if (command.validated()) {
            validatePrescriptionDrugs(current.applicationDrugs());
          }
          String source = normalizeSource(command.medicationSource(), false);
          if (source.isBlank()) source = current.medicationSource();
          check(ApplicationWorkflowPolicy.supplySource(current.policyState(), source));
          String notes = trim(command.notes());
          if (!command.validated() && notes.length() < 3) {
            throw badRequest("Indique el motivo del rechazo farmacéutico.");
          }
          Instant now = clock.instant();
          changed(updatePharmacyValidation(
              current.key(), current.revision(), command.validated(), source, notes,
              actorId, now));
          synchronizeLogisticsSource(current.key(), source, actorId, now);
        });
  }

  @Override
  @Transactional
  public CommandResult stockReservation(
      long patientId, String treatmentId, int cycle, int day, StockReservationCommand command,
      long actorId, String actorDisplayName) {
    requireBody(command);
    requireCommand(command.expectedRevision(), command.idempotencyKey());
    if (command.reserved() == null) {
      throw badRequest("Indique si desea reservar o liberar el stock.");
    }
    String action = command.reserved() ? "stock_reserved" : "stock_released";
    return execute(key(patientId, treatmentId, cycle, day), action,
        command.expectedRevision(), command.idempotencyKey(), command, actorId,
        current -> {
          if (command.reserved()) {
            reserve(current, command, actorId);
          } else {
            check(ApplicationWorkflowPolicy.releaseStock(current.policyState()));
            Instant now = clock.instant();
            releaseReservations(current.key(), actorId, now);
            changed(updateReservationStatus(
                current.key(), current.revision(), "released", trim(command.notes()),
                actorId, now));
          }
        });
  }

  @Override
  @Transactional
  public CommandResult clinicalAuthorization(
      long patientId, String treatmentId, int cycle, int day, ClinicalAuthorizationCommand command,
      long actorId, String actorDisplayName) {
    requireBody(command);
    requireCommand(command.expectedRevision(), command.idempotencyKey());
    String decision = trim(command.decision()).toUpperCase();
    if (!Set.of("PASS", "FAIL").contains(decision)) {
      throw badRequest("La decisión clínica debe ser PASS o FAIL.");
    }
    boolean passed = "PASS".equals(decision);
    JsonNode laboratory = (JsonNode) command.laboratory();
    JsonNode vitalSigns = (JsonNode) command.vitalSigns();
    JsonNode toxicity = (JsonNode) command.toxicity();
    if (!isObject(laboratory) || laboratory.isEmpty()
        || !isObject(vitalSigns) || vitalSigns.isEmpty()
        || !isObject(toxicity) || toxicity.isEmpty()) {
      throw badRequest("Complete laboratorio, signos vitales y evaluación de toxicidad.");
    }
    validateTriage(laboratory, vitalSigns, passed);
    String reason = trim(command.reason());
    if (!passed && reason.length() < 3) {
      throw badRequest("Indique el motivo clínico de la postergación.");
    }
    if (!passed && command.rescheduledDate() != null
        && command.rescheduledDate().isBefore(LocalDate.now(clock))) {
      throw badRequest("La nueva fecha propuesta no puede estar en el pasado.");
    }
    List<String> clinicalAlerts = passed
        ? triageSafetyAlerts(laboratory, vitalSigns, toxicity) : List.of();
    if (passed && !clinicalAlerts.isEmpty() && reason.length() < 10) {
      throw new InfusionFailure(
          InfusionFailure.Type.CONFLICT,
          "Hay alertas clínicas (" + String.join(", ", clinicalAlerts)
              + "). Revise los datos y documente una justificación para emitir PASS.",
          "CLINICAL_OVERRIDE_REQUIRED");
    }
    CommandResult result = execute(key(patientId, treatmentId, cycle, day),
        passed ? "clinical_pass" : "clinical_fail",
        command.expectedRevision(), command.idempotencyKey(), command, actorId,
        current -> {
          check(ApplicationWorkflowPolicy.clinicalAuthorization(
              current.policyState(), passed,
              current.hasConfirmedAppointmentOn(LocalDate.now(clock), clock.getZone())));
          ObjectNode assessment = mapper.createObjectNode();
          assessment.put("decision", decision);
          assessment.set("laboratory", laboratory.deepCopy());
          assessment.set("vitalSigns", vitalSigns.deepCopy());
          assessment.set("toxicity", toxicity.deepCopy());
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
            releaseReservations(current.key(), actorId, now);
          }
          changed(updateClinicalAuthorization(
              current.key(), current.revision(), passed, reason, assessment,
              command.rescheduledDate(), actorId, now));
          if (!passed) {
            synchronizeSessionAfterClinicalFail(current.key(), reason, actorId, now);
          }
        });
    if (!passed && !result.idempotentReplay()) {
      EvolutionAppend evolution = appendEvolution(
          patientId, treatmentId, cycle, day,
          "application-triage-fail-" + command.idempotencyKey(),
          "Postergación de aplicación",
          "Aplicación postergada por triaje clínico.\n"
              + "Esquema: " + ((JsonNode) result.workflow()).path("scheme").asText("") + "\n"
              + "Ciclo " + cycle + " · Día " + day + "\n"
              + "Motivo: " + reason
              + (command.rescheduledDate() == null
                  ? "" : "\nNueva fecha sugerida: " + command.rescheduledDate())
              + "\nLa reserva y el turno fueron liberados para reprogramación.",
          actorId, actorDisplayName);
      return withEvolution(result, evolution);
    }
    return result;
  }

  @Override
  @Transactional
  public CommandResult preparationStart(
      long patientId, String treatmentId, int cycle, int day, BasicCommand command,
      long actorId, String actorDisplayName) {
    requireBody(command);
    requireCommand(command.expectedRevision(), command.idempotencyKey());
    return execute(key(patientId, treatmentId, cycle, day), "preparation_started",
        command.expectedRevision(), command.idempotencyKey(), command, actorId,
        current -> {
          check(ApplicationWorkflowPolicy.startPreparation(current.policyState()));
          ObjectNode data = mapper.createObjectNode();
          data.put("notes", trim(command.notes()));
          Instant now = clock.instant();
          if ("cancelled".equals(current.preparationStatus())) {
            changed(resetCancelledPreparation(current.key(), current.revision(), actorId, now));
          }
          changed(updatePreparation(
              current.key(), current.revision(), "in_preparation", data,
              null, null, actorId, now));
          synchronizeSessionPreparation(current.key(), "in_preparation", actorId, now);
        });
  }

  @Override
  @Transactional
  public CommandResult preparationComplete(
      long patientId, String treatmentId, int cycle, int day, PreparationCompleteCommand command,
      long actorId, String actorDisplayName) {
    requireBody(command);
    requireCommand(command.expectedRevision(), command.idempotencyKey());
    if (command.preparations() == null || command.preparations().isEmpty()) {
      throw badRequest("Registre al menos una preparación con lote y estabilidad.");
    }
    return execute(key(patientId, treatmentId, cycle, day), "preparation_completed",
        command.expectedRevision(), command.idempotencyKey(), command, actorId,
        current -> completePreparation(current, command, actorId, actorDisplayName));
  }

  @Override
  @Transactional
  public CommandResult preparationRelease(
      long patientId, String treatmentId, int cycle, int day, BasicCommand command,
      long actorId, String actorDisplayName) {
    requireBody(command);
    requireCommand(command.expectedRevision(), command.idempotencyKey());
    return execute(key(patientId, treatmentId, cycle, day), "preparation_released",
        command.expectedRevision(), command.idempotencyKey(), command, actorId,
        current -> {
          check(ApplicationWorkflowPolicy.releasePreparation(current.policyState()));
          Instant now = clock.instant();
          if (current.preparationExpiresAt() == null
              || !current.preparationExpiresAt().isAfter(now)) {
            throw new InfusionFailure(
                InfusionFailure.Type.CONFLICT, "La preparación venció y no puede liberarse.", "PREPARATION_EXPIRED");
          }
          ObjectNode data = current.preparationData().isObject()
              ? (ObjectNode) current.preparationData().deepCopy()
              : mapper.createObjectNode();
          data.put("releaseNotes", trim(command.notes()));
          changed(updatePreparation(
              current.key(), current.revision(), "released", data,
              current.preparationExpiresAt(), null, actorId, now));
          synchronizeSessionPreparation(current.key(), "released", actorId, now);
        });
  }

  @Override
  @Transactional
  public CommandResult preparationRestart(
      long patientId, String treatmentId, int cycle, int day, BasicCommand command,
      long actorId, String actorDisplayName) {
    requireBody(command);
    requireCommand(command.expectedRevision(), command.idempotencyKey());
    String notes = trim(command.notes());
    check(ApplicationWorkflowPolicy.preparationRestartReason(notes));
    return execute(key(patientId, treatmentId, cycle, day), "preparation_restarted",
        command.expectedRevision(), command.idempotencyKey(), command, actorId,
        current -> {
          check(ApplicationWorkflowPolicy.restartPreparation(current.policyState()));
          Instant now = clock.instant();
          changed(restartPreparation(current.key(), current.revision(), notes, actorId, now));
          String replacementSource = switch (current.medicationSource()) {
            case "patient_has_medication" -> "patient_to_bring";
            case "received_center" -> "pending_supplier";
            default -> current.medicationSource();
          };
          synchronizeLogisticsSource(current.key(), replacementSource, actorId, now);
          synchronizeSessionPreparation(current.key(), "pending", actorId, now);
        });
  }

  @Override
  @Transactional
  public CommandResult administrationStart(
      long patientId, String treatmentId, int cycle, int day, AdministrationStartCommand command,
      long actorId, String actorDisplayName) {
    requireBody(command);
    requireCommand(command.expectedRevision(), command.idempotencyKey());
    if (!Boolean.TRUE.equals(command.patientVerified())
        || !Boolean.TRUE.equals(command.labelVerified())) {
      throw badRequest("Confirme paciente y etiqueta antes de iniciar.");
    }
    Long checker = resolveEnabledUser(
            trim(command.doubleCheckBy()), "application.administration.manage")
        .orElseThrow(() -> badRequest(
            "Seleccione un segundo profesional habilitado para el doble control."));
    if (checker == actorId) {
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
        command.expectedRevision(), command.idempotencyKey(), command, actorId,
        current -> {
          boolean hasTraceablePreparation =
              current.preparationExpiresAt() != null
                  && !preparationLots(current.key()).isEmpty();
          check(ApplicationWorkflowPolicy.startAdministration(
              current.policyState(),
              current.hasConfirmedAppointmentOn(LocalDate.now(clock), clock.getZone()),
              hasTraceablePreparation));
          if (current.preparationExpiresAt() == null
              || !current.preparationExpiresAt().isAfter(clock.instant())) {
            throw new InfusionFailure(
                InfusionFailure.Type.CONFLICT,
                "La preparación está vencida y debe rehacerse antes de administrar.",
                "PREPARATION_EXPIRED");
          }
          ObjectNode data = mapper.createObjectNode();
          data.put("patientVerified", true);
          data.put("labelVerified", true);
          data.put("doubleCheckByUserId", checkerId);
          data.put("doubleCheckDisplayName", userDisplayName(checkerId));
          data.put("startedAt", startedAt.toString());
          data.put("notes", trim(command.notes()));
          Instant now = clock.instant();
          changed(updateAdministration(
              current.key(), current.revision(), "in_progress", data,
              checkerId, startedAt, actorId, now));
          synchronizeSessionAdministration(current.key(), "in_progress", "in_progress", actorId, now);
        });
  }

  @Override
  @Transactional
  public CommandResult administrationComplete(
      long patientId, String treatmentId, int cycle, int day, AdministrationCompleteCommand command,
      long actorId, String actorDisplayName) {
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
        command.expectedRevision(), command.idempotencyKey(), command, actorId,
        current -> {
          check(ApplicationWorkflowPolicy.completeAdministration(current.policyState()));
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
          data.put("reactionDerivedFromInterruptions", recordedReaction.derivedFromInterruptions());
          data.put("reactionInterruptionCount", recordedReaction.interruptionCount());
          data.put("observation", observation);
          Instant now = clock.instant();
          changed(updateAdministration(
              current.key(), current.revision(), "completed", data,
              null, completedAt, actorId, now));
          synchronizeSessionAdministration(current.key(), "completed", "completed", actorId, now);
        });
    if (!result.idempotentReplay()) {
      JsonNode workflow = (JsonNode) result.workflow();
      JsonNode administration = workflow.path("administrationData");
      String secondChecker = administration.path("doubleCheckDisplayName").asText(
          "Usuario " + administration.path("doubleCheckByUserId").asText(""));
      boolean recordedReaction = administration.path("reactionOccurred").asBoolean(false);
      String recordedReactionDescription =
          administration.path("reactionDescription").asText("").trim();
      EvolutionAppend evolution = appendEvolution(
          patientId, treatmentId, cycle, day,
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
              + "Administró: " + actorDisplayName + "\n"
              + "Segundo control: " + secondChecker,
          actorId, actorDisplayName);
      return withEvolution(result, evolution);
    }
    return result;
  }

  @Override
  @Transactional
  public CommandResult administrationInterrupt(
      long patientId, String treatmentId, int cycle, int day, AdministrationInterruptCommand command,
      long actorId, String actorDisplayName) {
    requireBody(command);
    requireCommand(command.expectedRevision(), command.idempotencyKey());
    String reason = trim(command.reason());
    String actualDose = trim(command.actualDose());
    String measures = trim(command.measures());
    String patientCondition = trim(command.patientCondition());
    String disposition = trim(command.disposition()).toLowerCase();
    if (reason.length() < 3) throw badRequest("Describa el motivo de la interrupción.");
    if (actualDose.length() < 2) throw badRequest("Registre la dosis administrada hasta la interrupción.");
    if (measures.length() < 3) throw badRequest("Registre las medidas adoptadas.");
    if (patientCondition.length() < 3) throw badRequest("Registre la condición actual del paciente.");
    if (!Set.of("observation", "medical_review", "emergency_transfer").contains(disposition)) {
      throw badRequest("Seleccione el destino clínico posterior a la interrupción.");
    }
    Instant interruptedAt = command.interruptedAt() == null ? clock.instant() : command.interruptedAt();
    CommandResult result = execute(
        key(patientId, treatmentId, cycle, day), "administration_interrupted",
        command.expectedRevision(), command.idempotencyKey(), command, actorId,
        current -> {
          check(ApplicationWorkflowPolicy.interruptAdministration(current.policyState()));
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
          interruption.put("recordedByUserId", actorId);
          interruption.put("recordedByDisplayName", actorDisplayName);
          data.withArray("interruptions").add(interruption);
          data.put("interruptionPending", true);
          data.put("interruptedAt", interruptedAt.toString());
          data.put("interruptionReason", reason);
          data.put("actualDoseAtInterruption", actualDose);
          data.put("interruptionMeasures", measures);
          data.put("interruptionPatientCondition", patientCondition);
          data.put("interruptionDisposition", disposition);
          Instant now = clock.instant();
          changed(interruptAdministration(current.key(), current.revision(), data, actorId, now));
          synchronizeSessionAdministration(current.key(), "withheld", "paused", actorId, now);
        });
    if (!result.idempotentReplay()) {
      EvolutionAppend evolution = appendEvolution(
          patientId, treatmentId, cycle, day,
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
              + "Registró: " + actorDisplayName,
          actorId, actorDisplayName);
      return withEvolution(result, evolution);
    }
    return result;
  }

  @Override
  @Transactional
  public CommandResult administrationResolve(
      long patientId, String treatmentId, int cycle, int day, AdministrationResolveCommand command,
      long actorId, String actorDisplayName) {
    requireBody(command);
    requireCommand(command.expectedRevision(), command.idempotencyKey());
    String decision = trim(command.decision()).toLowerCase();
    String notes = trim(command.notes());
    String actualDose = trim(command.actualDose());
    String patientCondition = trim(command.patientCondition());
    if (!Set.of("resume", "terminate").contains(decision)) {
      throw badRequest("Seleccione si corresponde reanudar o cerrar la administración.");
    }
    if (notes.length() < 3) throw badRequest("Documente la decisión clínica.");
    if (patientCondition.length() < 3) throw badRequest("Registre la condición del paciente al resolver.");
    if ("terminate".equals(decision) && actualDose.length() < 2) {
      throw badRequest("Registre la dosis total administrada antes del cierre.");
    }
    Instant resolvedAt = command.resolvedAt() == null ? clock.instant() : command.resolvedAt();
    String action = "resume".equals(decision) ? "administration_resumed" : "administration_terminated";
    CommandResult result = execute(
        key(patientId, treatmentId, cycle, day), action,
        command.expectedRevision(), command.idempotencyKey(), command, actorId,
        current -> {
          check(ApplicationWorkflowPolicy.resolveAdministration(
              current.policyState(), "resume".equals(decision),
              current.preparationExpiresAt() != null
                  && current.preparationExpiresAt().isAfter(clock.instant())));
          ObjectNode data = administrationData(current);
          if (!data.path("interruptionPending").asBoolean(false)) {
            throw new InfusionFailure(
                InfusionFailure.Type.CONFLICT, "La interrupción ya fue resuelta.", "INTERRUPTION_ALREADY_RESOLVED");
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
          resolution.put("recordedByUserId", actorId);
          resolution.put("recordedByDisplayName", actorDisplayName);
          data.withArray("interruptionResolutions").add(resolution);
          data.put("interruptionPending", false);
          data.put("interruptionResolution", decision);
          data.put("interruptionResolutionAt", resolvedAt.toString());
          data.put("interruptionResolutionNotes", notes);
          data.put("interruptionResolutionPatientCondition", patientCondition);
          if (!actualDose.isBlank()) data.put("actualDose", actualDose);
          Instant now = clock.instant();
          boolean resume = "resume".equals(decision);
          changed(resolveAdministration(
              current.key(), current.revision(), resume, data, resolvedAt, actorId, now));
          synchronizeSessionAdministration(
              current.key(), resume ? "in_progress" : "withheld",
              resume ? "in_progress" : "cancelled", actorId, now);
        });
    if (!result.idempotentReplay()) {
      String decisionText = "resume".equals(decision)
          ? "Administración reanudada" : "Administración cerrada sin completar";
      EvolutionAppend evolution = appendEvolution(
          patientId, treatmentId, cycle, day,
          "application-administration-resolution-" + command.idempotencyKey(),
          "Resolución de interrupción",
          decisionText + ".\n"
              + "Ciclo " + cycle + " · Día " + day + "\n"
              + "Hora: " + resolvedAt + "\n"
              + "Decisión: " + notes + "\n"
              + "Dosis total registrada: " + (actualDose.isBlank() ? "Continúa en curso" : actualDose) + "\n"
              + "Condición del paciente: " + patientCondition + "\n"
              + "Registró: " + actorDisplayName,
          actorId, actorDisplayName);
      return withEvolution(result, evolution);
    }
    return result;
  }

  // ---------- usado por PostgresInfusionOperationsStore (infra-a-infra) ----------

  public void ensureWorkflowRows() {
    jdbc.update("""
        INSERT INTO treatment_application_workflows (
          patient_id, treatment_id, cycle_number, application_day,
          workflow_status, medication_source, pharmacy_validation_status,
          stock_reservation_status, clinical_authorization_status,
          preparation_status, administration_status,
          updated_by, created_at, updated_at
        )
        SELECT l.patient_id, l.treatment_id, l.cycle_number, l.application_day,
               CASE
                 WHEN session.administration_status = 'completed'
                   OR session.clinical_status = 'completed' THEN 'completed'
                 WHEN session.administration_status = 'in_progress' THEN 'in_administration'
                 WHEN session.pharmacy_status = 'in_preparation' THEN 'in_preparation'
                 WHEN session.pharmacy_status IN ('ready','released')
                   AND session.clinical_status IN ('ready','in_progress','observation')
                   THEN 'clinically_authorized'
                 WHEN session.pharmacy_status IN ('ready','released') THEN 'scheduled'
                 WHEN session.id IS NOT NULL THEN 'scheduled'
                 ELSE 'prescribed'
               END,
               CASE l.medication_state
                 WHEN 'with_patient' THEN 'patient_has_medication'
                 WHEN 'received' THEN 'received_center'
                 ELSE 'pending_supplier'
               END,
               'pending',
               CASE WHEN l.medication_state IN ('with_patient','received')
                    THEN 'not_applicable' ELSE 'none' END,
               CASE
                 WHEN session.administration_status IN ('in_progress','completed')
                   OR session.clinical_status IN ('ready','in_progress','observation','completed')
                   THEN 'passed'
                 ELSE 'pending'
               END,
               CASE session.pharmacy_status
                 WHEN 'in_preparation' THEN 'in_preparation'
                 WHEN 'ready' THEN CASE
                   WHEN session.administration_status IN ('in_progress','completed')
                     OR session.clinical_status = 'completed' THEN 'prepared'
                   ELSE 'not_started'
                 END
                 WHEN 'released' THEN CASE
                   WHEN session.administration_status IN ('in_progress','completed')
                     OR session.clinical_status = 'completed' THEN 'released'
                   ELSE 'not_started'
                 END
                 WHEN 'cancelled' THEN 'cancelled'
                 ELSE 'not_started'
               END,
               COALESCE(session.administration_status, 'not_started'),
               l.updated_by, l.created_at, l.updated_at
          FROM treatment_application_logistics l
          LEFT JOIN LATERAL (
            SELECT s.id, s.clinical_status, s.pharmacy_status, s.administration_status
              FROM unified_infusion_sessions s
             WHERE s.patient_id = l.patient_id
               AND s.treatment_id = l.treatment_id
               AND s.cycle_number = l.cycle_number
               AND s.application_day = l.application_day
               AND s.clinical_status <> 'cancelled'
             ORDER BY s.scheduled_at DESC NULLS LAST, s.id DESC
             LIMIT 1
          ) session ON true
        ON CONFLICT DO NOTHING
        """);
  }

  public Optional<Application> lock(Key key) {
    List<Application> locked = jdbc.query(
        selectSql() + keyWhere() + " FOR UPDATE OF w",
        this::mapApplication,
        key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay());
    return locked.stream().findFirst();
  }

  public Optional<Application> find(Key key) {
    return jdbc.query(
        selectSql() + keyWhere(),
        this::mapApplication,
        key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay())
        .stream().findFirst();
  }

  public Optional<ScheduleGate> scheduleGate(Key key) {
    return jdbc.query("""
        SELECT w.pharmacy_validation_status, w.medication_source, w.stock_reservation_status,
               l.prescription_state,
               CASE
                 WHEN m.effective_from_cycle IS NULL OR w.cycle_number >= m.effective_from_cycle
                   THEN COALESCE(m.continuity_status, 'active')
                 ELSE 'active'
               END AS continuity_status,
               CASE
                 WHEN m.effective_from_cycle IS NULL OR w.cycle_number >= m.effective_from_cycle
                   THEN COALESCE(m.prescription_required, false)
                 ELSE false
               END AS prescription_required,
               clinical_authorization_status, clinical_authorization_reason,
               clinical_assessment::text, preparation_status, administration_status,
               workflow_status, w.revision
          FROM treatment_application_workflows w
          JOIN treatment_application_logistics l
            ON l.patient_id = w.patient_id AND l.treatment_id = w.treatment_id
           AND l.cycle_number = w.cycle_number AND l.application_day = w.application_day
          LEFT JOIN treatment_management_states m
            ON m.patient_id = w.patient_id AND m.treatment_id = w.treatment_id
         WHERE w.patient_id = ? AND w.treatment_id = ? AND w.cycle_number = ?
           AND w.application_day = ?
         FOR UPDATE OF w
        """, (result, row) -> new ScheduleGate(
            text(result, "prescription_state"),
            text(result, "continuity_status"),
            result.getBoolean("prescription_required"),
            text(result, "pharmacy_validation_status"),
            text(result, "medication_source"),
            text(result, "stock_reservation_status"),
            text(result, "clinical_authorization_status"),
            text(result, "clinical_authorization_reason"),
            mapper.readTree(result.getString("clinical_assessment")),
            text(result, "preparation_status"),
            text(result, "administration_status"),
            text(result, "workflow_status"),
            result.getLong("revision")),
        key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay())
        .stream().findFirst();
  }

  public boolean markAppointmentScheduled(Key key, long expectedRevision, long actorId, Instant now) {
    return jdbc.update("""
        UPDATE treatment_application_workflows
           SET workflow_status = 'triage_pending',
               clinical_authorization_status = 'pending',
               clinical_authorization_reason = NULL,
               clinical_assessment = '{}'::jsonb,
               clinically_authorized_by = NULL,
               clinically_authorized_at = NULL,
               revision = revision + 1,
               updated_by = ?,
               updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND revision = ?
           AND clinical_authorization_status IN ('pending','failed')
           AND preparation_status IN ('not_started','cancelled')
           AND administration_status = 'not_started'
        """, actorId, Timestamp.from(now), key.patientId(), key.treatmentId(),
        key.cycleNumber(), key.applicationDay(), expectedRevision) == 1;
  }

  public boolean markAppointmentRemoved(Key key, long expectedRevision, long actorId, Instant now) {
    return jdbc.update("""
        UPDATE treatment_application_workflows
           SET workflow_status = CASE
                 WHEN medication_source IN ('patient_has_medication','received_center')
                   OR (medication_source = 'center_stock'
                     AND stock_reservation_status = 'reserved')
                   THEN 'medication_ready'
                 ELSE 'medication_pending'
               END,
               revision = revision + 1,
               updated_by = ?,
               updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ?
           AND clinical_authorization_status IN ('pending','failed')
           AND preparation_status IN ('not_started','cancelled')
           AND administration_status = 'not_started'
           AND revision = ?
        """, actorId, Timestamp.from(now), key.patientId(), key.treatmentId(),
        key.cycleNumber(), key.applicationDay(), expectedRevision) == 1;
  }

  public void insertEvent(
      Key key, String action, String idempotencyKey, long actorId,
      long expectedRevision, long resultingRevision, JsonNode command,
      JsonNode before, JsonNode after, Instant now) {
    jdbc.update("""
        INSERT INTO treatment_application_workflow_events (
          patient_id, treatment_id, cycle_number, application_day,
          action, idempotency_key, actor_user_id, expected_revision,
          resulting_revision, command_json, before_json, after_json, occurred_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb),
                  CAST(? AS jsonb), CAST(? AS jsonb), ?)
        """, key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay(),
        action, idempotencyKey, actorId, expectedRevision, resultingRevision,
        command.toString(), before.toString(), after.toString(), Timestamp.from(now));
  }

  public String userDisplayName(long userId) {
    return jdbc.query("""
        SELECT COALESCE(NULLIF(display_name, ''), username)
          FROM local_users
         WHERE id = ?
        """, (result, row) -> result.getString(1), userId)
        .stream().findFirst().orElse("Usuario " + userId);
  }

  public Optional<Long> resolveEnabledUser(String identifier, String requiredPermission) {
    if (identifier == null || identifier.isBlank()) return Optional.empty();
    try {
      long id = Long.parseLong(identifier.trim());
      return jdbc.query("""
          SELECT u.id
            FROM local_users u
           WHERE u.id = ? AND u.enabled = true
             AND EXISTS (
               SELECT 1
                 FROM local_user_roles ur
                 JOIN local_role_permissions rp ON rp.role_id = ur.role_id
                 JOIN local_permissions permission ON permission.id = rp.permission_id
                 JOIN local_roles role ON role.id = ur.role_id
                WHERE ur.user_id = u.id AND role.enabled = true
                  AND permission.permission_key = ?
             )
          """, (result, row) -> result.getLong("id"), id, requiredPermission)
          .stream().findFirst();
    } catch (NumberFormatException ignored) {
      String value = identifier.trim();
      return jdbc.query("""
          SELECT u.id
            FROM local_users u
           WHERE u.enabled = true
             AND (lower(u.username) = lower(?) OR lower(COALESCE(u.display_name, '')) = lower(?))
             AND EXISTS (
               SELECT 1
                 FROM local_user_roles ur
                 JOIN local_role_permissions rp ON rp.role_id = ur.role_id
                 JOIN local_permissions permission ON permission.id = rp.permission_id
                 JOIN local_roles role ON role.id = ur.role_id
                WHERE ur.user_id = u.id AND role.enabled = true
                  AND permission.permission_key = ?
             )
           ORDER BY u.id LIMIT 1
          """, (result, row) -> result.getLong("id"), value, value, requiredPermission)
          .stream().findFirst();
    }
  }

  // ---------- privados (antes ApplicationWorkflowRepository) ----------

  List<Application> listApplications(
      String queue, LocalDate date, String query, String medicationSource) {
    StringBuilder where = new StringBuilder(
        Set.of("applications", "administration").contains(queue)
            ? " WHERE TRUE"
            : " WHERE w.administration_status <> 'completed'");
    List<Object> parameters = new ArrayList<>();
    switch (queue) {
      case "applications" -> where.append(" AND s.id IS NOT NULL");
      case "pharmacy" -> where.append("""
           AND w.workflow_status <> 'cancelled'
          """);
      case "triage" -> where.append("""
           AND s.id IS NOT NULL
           AND w.preparation_status IN ('not_started','cancelled')
          """);
      case "preparation" -> where.append("""
           AND s.id IS NOT NULL
           AND w.clinical_authorization_status = 'passed'
           AND w.preparation_status IN ('not_started','in_preparation','prepared','released')
          """);
      case "administration" -> where.append("""
           AND s.id IS NOT NULL
           AND w.clinical_authorization_status = 'passed'
           AND w.preparation_status = 'released'
          """);
      default -> throw new IllegalArgumentException("Unknown workflow queue");
    }
    if (date != null) {
      if ("pharmacy".equals(queue)) {
        where.append(" AND l.planned_date = ?");
        parameters.add(Date.valueOf(date));
      } else {
        Instant from = date.atStartOfDay(clock.getZone()).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        where.append(" AND s.scheduled_at >= ? AND s.scheduled_at < ?");
        parameters.add(Timestamp.from(from));
        parameters.add(Timestamp.from(to));
      }
    }
    String normalizedSource = medicationSource == null ? "" : medicationSource.trim();
    if (!normalizedSource.isBlank()) {
      where.append(" AND w.medication_source = ?");
      parameters.add(normalizedSource);
    }
    String normalizedQuery = normalizeSearch(query);
    if (!normalizedQuery.isBlank()) {
      String searchable = """
          translate(lower(concat_ws(' ',
            p.last_name, p.first_name, p.document_number, p.medical_record_number,
            t.scheme_name, t.diagnosis, l.drug_summary, s.chair,
            'ciclo', l.cycle_number, 'dia', l.application_day,
            CAST(l.planned_date AS text), to_char(l.planned_date, 'DD/MM/YYYY')
          )), 'áéíóúüñ', 'aeiouun')
          """;
      for (String token : normalizedQuery.split("\\s+")) {
        if (token.isBlank()) continue;
        where.append(" AND ").append(searchable).append(" LIKE ?");
        parameters.add("%" + token + "%");
      }
    }
    String order = "pharmacy".equals(queue)
        ? " ORDER BY l.planned_date NULLS LAST, p.last_name, p.first_name, l.cycle_number, l.application_day"
        : " ORDER BY s.scheduled_at NULLS LAST, s.chair, p.last_name, p.first_name";
    return jdbc.query(selectSql() + where + order + " LIMIT 2000",
        this::mapApplication, parameters.toArray());
  }

  private boolean updatePharmacyValidation(
      Key key, long expectedRevision, boolean approved, String source, String notes,
      long actorId, Instant now) {
    String validation = approved ? "approved" : "rejected";
    String workflow = !approved
        ? "pharmacy_rejected"
        : Set.of("patient_has_medication", "received_center").contains(source)
            ? "medication_ready"
            : "medication_pending";
    String stock = switch (source) {
      case "patient_has_medication", "received_center" -> "not_applicable";
      default -> "none";
    };
    return jdbc.update("""
        UPDATE treatment_application_workflows
           SET pharmacy_validation_status = ?,
               pharmacy_validation_notes = NULLIF(?, ''),
               pharmacy_validated_by = ?,
               pharmacy_validated_at = ?,
               medication_source = ?,
               stock_reservation_status = CASE
                 WHEN stock_reservation_status = 'reserved'
                   AND medication_source = ?
                   AND ? = 'center_stock'
                 THEN 'reserved'
                 ELSE ?
               END,
               workflow_status = CASE
                 WHEN stock_reservation_status = 'reserved'
                   AND medication_source = ?
                   AND ? = 'center_stock'
                 THEN 'medication_ready'
                 ELSE ?
               END,
               revision = revision + 1,
               updated_by = ?,
               updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND revision = ?
        """, validation, notes, actorId, Timestamp.from(now),
        source, source, source, stock, source, source, workflow,
        actorId, Timestamp.from(now), key.patientId(), key.treatmentId(),
        key.cycleNumber(), key.applicationDay(), expectedRevision) == 1;
  }

  private boolean updateReservationStatus(
      Key key, long expectedRevision, String status, String notes, long actorId, Instant now) {
    boolean reserved = "reserved".equals(status);
    return jdbc.update("""
        UPDATE treatment_application_workflows
           SET stock_reservation_status = ?,
               stock_reservation_notes = NULLIF(?, ''),
               stock_reserved_by = CASE WHEN ? THEN ? ELSE stock_reserved_by END,
               stock_reserved_at = CASE WHEN ? THEN ? ELSE stock_reserved_at END,
               stock_released_by = CASE WHEN ? THEN stock_released_by ELSE ? END,
               stock_released_at = CASE WHEN ? THEN stock_released_at ELSE ? END,
               workflow_status = CASE
                 WHEN ? THEN 'medication_ready'
                 WHEN clinical_authorization_status = 'failed' THEN 'postponed'
                 ELSE 'medication_pending'
               END,
               revision = revision + 1,
               updated_by = ?,
               updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND revision = ?
        """, status, notes, reserved, actorId, reserved, Timestamp.from(now),
        reserved, actorId, reserved, Timestamp.from(now), reserved,
        actorId, Timestamp.from(now), key.patientId(), key.treatmentId(),
        key.cycleNumber(), key.applicationDay(), expectedRevision) == 1;
  }

  private boolean updateClinicalAuthorization(
      Key key, long expectedRevision, boolean passed, String reason, JsonNode assessment,
      LocalDate rescheduledDate, long actorId, Instant now) {
    int changed = jdbc.update("""
        UPDATE treatment_application_workflows
           SET clinical_authorization_status = ?,
               clinical_authorization_reason = NULLIF(?, ''),
               clinical_assessment = CAST(? AS jsonb),
               clinically_authorized_by = ?,
               clinically_authorized_at = ?,
               workflow_status = ?,
               stock_reservation_status = CASE
                 WHEN ? THEN stock_reservation_status
                 WHEN stock_reservation_status = 'reserved' THEN 'released'
                 ELSE stock_reservation_status
               END,
               stock_released_by = CASE
                 WHEN NOT ? AND stock_reservation_status = 'reserved' THEN ?
                 ELSE stock_released_by
               END,
               stock_released_at = CASE
                 WHEN NOT ? AND stock_reservation_status = 'reserved' THEN ?
                 ELSE stock_released_at
               END,
               revision = revision + 1,
               updated_by = ?,
               updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND revision = ?
        """, passed ? "passed" : "failed", reason, assessment.toString(),
        actorId, Timestamp.from(now), passed ? "clinically_authorized" : "postponed",
        passed, passed, actorId, passed, Timestamp.from(now), actorId, Timestamp.from(now),
        key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay(),
        expectedRevision);
    if (changed == 1 && rescheduledDate != null) {
      jdbc.update("""
          UPDATE treatment_application_logistics
             SET planned_date = ?, revision = revision + 1,
                 updated_by = ?, updated_at = ?
           WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
             AND application_day = ?
          """, Date.valueOf(rescheduledDate), actorId, Timestamp.from(now),
          key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay());
    }
    return changed == 1;
  }

  boolean updatePreparation(
      Key key, long expectedRevision, String status, JsonNode data, Instant expiresAt,
      Long verifiedBy, long actorId, Instant now) {
    String workflow = switch (status) {
      case "in_preparation" -> "in_preparation";
      case "prepared" -> "prepared";
      case "released" -> "released";
      default -> "clinically_authorized";
    };
    return jdbc.update("""
        UPDATE treatment_application_workflows
           SET preparation_status = ?,
               preparation_data = CASE WHEN ? = '{}' THEN preparation_data ELSE CAST(? AS jsonb) END,
               preparation_started_by = CASE
                 WHEN ? IN ('in_preparation','prepared') THEN COALESCE(preparation_started_by, ?)
                 ELSE preparation_started_by END,
               preparation_started_at = CASE
                 WHEN ? IN ('in_preparation','prepared') THEN COALESCE(preparation_started_at, ?)
                 ELSE preparation_started_at END,
               prepared_by = CASE WHEN ? = 'prepared' THEN ? ELSE prepared_by END,
               preparation_verified_by = CASE
                 WHEN ? = 'prepared' THEN ? ELSE preparation_verified_by END,
               prepared_at = CASE WHEN ? = 'prepared' THEN ? ELSE prepared_at END,
               preparation_released_by = CASE WHEN ? = 'released' THEN ? ELSE preparation_released_by END,
               preparation_released_at = CASE WHEN ? = 'released' THEN ? ELSE preparation_released_at END,
               preparation_expires_at = COALESCE(?, preparation_expires_at),
               stock_reservation_status = CASE
                 WHEN ? = 'prepared' AND stock_reservation_status = 'reserved' THEN 'consumed'
                 ELSE stock_reservation_status
               END,
               workflow_status = ?,
               revision = revision + 1,
               updated_by = ?,
               updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND revision = ?
        """, status, data.toString(), data.toString(),
        status, actorId, status, Timestamp.from(now),
        status, actorId, status, verifiedBy, status, Timestamp.from(now),
        status, actorId, status, Timestamp.from(now),
        expiresAt == null ? null : Timestamp.from(expiresAt), status, workflow,
        actorId, Timestamp.from(now), key.patientId(), key.treatmentId(),
        key.cycleNumber(), key.applicationDay(), expectedRevision) == 1;
  }

  private boolean resetCancelledPreparation(Key key, long expectedRevision, long actorId, Instant now) {
    int changed = jdbc.update("""
        UPDATE treatment_application_workflows
           SET preparation_data = '{}'::jsonb,
               preparation_started_by = NULL,
               preparation_started_at = NULL,
               prepared_by = NULL,
               preparation_verified_by = NULL,
               prepared_at = NULL,
               preparation_released_by = NULL,
               preparation_released_at = NULL,
               preparation_expires_at = NULL
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND revision = ?
           AND preparation_status = 'cancelled'
        """, key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay(),
        expectedRevision);
    if (changed == 1) {
      jdbc.update("""
          UPDATE application_preparation_lots
             SET preparation_status = 'discarded',
                 discarded_by = COALESCE(discarded_by, ?),
                 discarded_at = COALESCE(discarded_at, ?),
                 discard_reason = COALESCE(
                   NULLIF(discard_reason, ''),
                   'Reinicio de una preparación cancelada')
           WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
             AND application_day = ? AND preparation_status = 'active'
          """, actorId, Timestamp.from(now), key.patientId(), key.treatmentId(),
          key.cycleNumber(), key.applicationDay());
    }
    return changed == 1;
  }

  private boolean updateAdministration(
      Key key, long expectedRevision, String status, JsonNode data,
      Long secondCheckerId, Instant actualAt, long actorId, Instant now) {
    boolean starting = "in_progress".equals(status);
    return jdbc.update("""
        UPDATE treatment_application_workflows
           SET administration_status = ?,
               administration_data = CAST(? AS jsonb),
               administration_started_by = CASE WHEN ? THEN ? ELSE administration_started_by END,
               administration_second_checker_by = CASE
                 WHEN ? THEN ? ELSE administration_second_checker_by END,
               administration_started_at = CASE WHEN ? THEN ? ELSE administration_started_at END,
               administration_completed_by = CASE WHEN ? THEN administration_completed_by ELSE ? END,
               administration_completed_at = CASE WHEN ? THEN administration_completed_at ELSE ? END,
               workflow_status = CASE WHEN ? THEN 'in_administration' ELSE 'completed' END,
               revision = revision + 1,
               updated_by = ?,
               updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND revision = ?
        """, status, data.toString(), starting, actorId, starting, secondCheckerId,
        starting, Timestamp.from(actualAt), starting, actorId, starting, Timestamp.from(actualAt),
        starting, actorId, Timestamp.from(now), key.patientId(), key.treatmentId(),
        key.cycleNumber(), key.applicationDay(), expectedRevision) == 1;
  }

  private boolean interruptAdministration(Key key, long expectedRevision, JsonNode data, long actorId, Instant now) {
    return jdbc.update("""
        UPDATE treatment_application_workflows
           SET administration_status = 'withheld',
               administration_data = CAST(? AS jsonb),
               workflow_status = 'in_administration',
               revision = revision + 1,
               updated_by = ?,
               updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND revision = ?
           AND administration_status = 'in_progress'
        """, data.toString(), actorId, Timestamp.from(now), key.patientId(),
        key.treatmentId(), key.cycleNumber(), key.applicationDay(),
        expectedRevision) == 1;
  }

  private boolean resolveAdministration(
      Key key, long expectedRevision, boolean resume, JsonNode data,
      Instant resolvedAt, long actorId, Instant now) {
    return jdbc.update("""
        UPDATE treatment_application_workflows
           SET administration_status = CASE WHEN ? THEN 'in_progress' ELSE 'withheld' END,
               administration_data = CAST(? AS jsonb),
               administration_completed_by = CASE
                 WHEN ? THEN administration_completed_by ELSE ? END,
               administration_completed_at = CASE
                 WHEN ? THEN administration_completed_at ELSE ? END,
               workflow_status = CASE WHEN ? THEN 'in_administration' ELSE 'cancelled' END,
               revision = revision + 1,
               updated_by = ?,
               updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND revision = ?
           AND administration_status = 'withheld'
        """, resume, data.toString(), resume, actorId, resume, Timestamp.from(resolvedAt),
        resume, actorId, Timestamp.from(now), key.patientId(), key.treatmentId(),
        key.cycleNumber(), key.applicationDay(), expectedRevision) == 1;
  }

  private void synchronizeLogisticsSource(Key key, String source, long actorId, Instant now) {
    String medicationState = switch (source) {
      case "patient_has_medication" -> "with_patient";
      case "received_center" -> "received";
      default -> "pending";
    };
    jdbc.update("""
        UPDATE treatment_application_logistics
           SET medication_state = ?, revision = revision + 1,
               updated_by = ?, updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ?
        """, medicationState, actorId, Timestamp.from(now), key.patientId(),
        key.treatmentId(), key.cycleNumber(), key.applicationDay());
    jdbc.update("""
        UPDATE unified_infusion_sessions
           SET source_ref = source_ref || jsonb_build_object(
                 'scheduler', COALESCE(source_ref -> 'scheduler', '{}'::jsonb)
                   || jsonb_build_object(
                     'medicationSource', ?,
                     'medicationReceived', ?,
                     'medicationWithPatient', ?
                   )
               ),
               updated_by = ?, updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND clinical_status <> 'cancelled'
        """, source, "received_center".equals(source),
        "patient_has_medication".equals(source), actorId, Timestamp.from(now),
        key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay());
  }

  boolean reserveInventory(long lotId, String drugId, String drugName, BigDecimal quantity, String unit,
      long actorId, Instant now) {
    return jdbc.update("""
        UPDATE pharmacy_inventory_lots
           SET quantity_reserved = quantity_reserved + ?,
               revision = revision + 1, updated_by = ?, updated_at = ?
         WHERE id = ? AND inventory_status = 'active'
           AND expiration_date >= ?
           AND lower(quantity_unit) = lower(?)
           AND (
             (NULLIF(?, '') IS NOT NULL AND drug_id = NULLIF(?, ''))
             OR (NULLIF(?, '') IS NULL AND lower(drug_name) = lower(?))
           )
           AND quantity_on_hand - quantity_reserved >= ?
        """, quantity, actorId, Timestamp.from(now), lotId,
        Date.valueOf(LocalDate.now(clock)), unit,
        drugId, drugId, drugId, drugName, quantity) == 1;
  }

  private void insertReservation(
      UUID id, Key key, String componentKey, String drugId, String drugName,
      BigDecimal requestedQuantity, String requestedText, String unit,
      String source, String status, String verificationMethod, Long inventoryLotId,
      String notes, long actorId, Instant now) {
    jdbc.update("""
        INSERT INTO application_stock_reservations (
          id, patient_id, treatment_id, cycle_number, application_day,
          component_key, drug_id, drug_name, requested_quantity,
          requested_quantity_text, reserved_quantity, quantity_unit,
          medication_source, reservation_status, verification_method,
          inventory_lot_id, verification_notes, verified_by, verified_at,
          created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, NULLIF(?, ''), ?, ?, NULLIF(?, ''),
                  ?, NULLIF(?, ''), ?, ?, ?, ?, NULLIF(?, ''), ?, ?, ?, ?)
        """, id, key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay(),
        componentKey, drugId, drugName, requestedQuantity, requestedText,
        "reserved".equals(status) ? requestedQuantity : null, unit,
        source, status, verificationMethod, inventoryLotId, notes,
        actorId, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
  }

  private List<Reservation> activeReservations(Key key) {
    return reservations(key, true);
  }

  private List<Reservation> reservations(Key key, boolean activeOnly) {
    return jdbc.query("""
        SELECT id, component_key, drug_id, drug_name, requested_quantity,
               requested_quantity_text, reserved_quantity, quantity_unit,
               medication_source, reservation_status, verification_method,
               inventory_lot_id, verification_notes
          FROM application_stock_reservations
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ?
        """ + (activeOnly ? " AND reservation_status IN ('pending','reserved')" : "") + """
         ORDER BY created_at, drug_name
        """, this::mapReservation, key.patientId(), key.treatmentId(),
        key.cycleNumber(), key.applicationDay());
  }

  private void releaseReservations(Key key, long actorId, Instant now) {
    List<Reservation> rows = activeReservations(key);
    for (Reservation row : rows) {
      if ("reserved".equals(row.status())
          && row.inventoryLotId() != null
          && row.reservedQuantity() != null) {
        jdbc.update("""
            UPDATE pharmacy_inventory_lots
               SET quantity_reserved = GREATEST(0, quantity_reserved - ?),
                   revision = revision + 1, updated_by = ?, updated_at = ?
             WHERE id = ?
            """, row.reservedQuantity(), actorId, Timestamp.from(now), row.inventoryLotId());
      }
    }
    jdbc.update("""
        UPDATE application_stock_reservations
           SET reservation_status = 'released', released_by = ?, released_at = ?,
               updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND reservation_status IN ('pending','reserved')
        """, actorId, Timestamp.from(now), Timestamp.from(now), key.patientId(),
        key.treatmentId(), key.cycleNumber(), key.applicationDay());
  }

  private void consumeReservations(Key key, long actorId, Instant now) {
    List<Reservation> rows = activeReservations(key);
    for (Reservation row : rows) {
      if (!"reserved".equals(row.status())) continue;
      if (row.inventoryLotId() != null && row.reservedQuantity() != null) {
        int changed = jdbc.update("""
            UPDATE pharmacy_inventory_lots
               SET quantity_on_hand = quantity_on_hand - ?,
                   quantity_reserved = quantity_reserved - ?,
                   inventory_status = CASE
                     WHEN quantity_on_hand - ? = 0 THEN 'depleted'
                     ELSE inventory_status
                   END,
                   revision = revision + 1, updated_by = ?, updated_at = ?
             WHERE id = ? AND quantity_on_hand >= ? AND quantity_reserved >= ?
            """, row.reservedQuantity(), row.reservedQuantity(), row.reservedQuantity(),
            actorId, Timestamp.from(now), row.inventoryLotId(),
            row.reservedQuantity(), row.reservedQuantity());
        if (changed != 1) {
          throw new IllegalStateException("Reserved inventory changed unexpectedly");
        }
      }
    }
    jdbc.update("""
        UPDATE application_stock_reservations
           SET reservation_status = 'consumed', consumed_at = ?, updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND reservation_status = 'reserved'
        """, Timestamp.from(now), Timestamp.from(now), key.patientId(),
        key.treatmentId(), key.cycleNumber(), key.applicationDay());
  }

  void insertPreparationLot(
      UUID id, Key key, String componentKey, UUID reservationId,
      Long inventoryLotId, String drugName,
      String lot, LocalDate expiryDate, BigDecimal quantity, String quantityText,
      String unit, String diluent, String finalVolume, String concentration,
      int ttlMinutes, long actorId, long verifiedBy, Instant now) {
    jdbc.update("""
        INSERT INTO application_preparation_lots (
          id, patient_id, treatment_id, cycle_number, application_day,
          component_key, stock_reservation_id, inventory_lot_id, drug_name, lot_number,
          expiration_date, quantity, quantity_text, quantity_unit, diluent,
          final_volume, concentration, ttl_minutes, prepared_by, verified_by, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULLIF(?, ''), NULLIF(?, ''),
                  NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), ?, ?, ?, ?)
        """, id, key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay(),
        componentKey, reservationId, inventoryLotId, drugName, lot, Date.valueOf(expiryDate),
        quantity, quantityText, unit, diluent, finalVolume, concentration,
        ttlMinutes, actorId, verifiedBy, Timestamp.from(now));
  }

  private Optional<InventoryLot> inventoryLot(long id) {
    return jdbc.query("""
        SELECT id, drug_id, drug_name, lot_number, expiration_date,
               quantity_on_hand, quantity_reserved, quantity_unit, inventory_status
          FROM pharmacy_inventory_lots
         WHERE id = ?
        """, (result, row) -> new InventoryLot(
            result.getLong("id"), text(result, "drug_id"), text(result, "drug_name"),
            text(result, "lot_number"), result.getDate("expiration_date").toLocalDate(),
            result.getBigDecimal("quantity_on_hand"), result.getBigDecimal("quantity_reserved"),
            text(result, "quantity_unit"), text(result, "inventory_status")),
        id).stream().findFirst();
  }

  private List<PreparationLot> preparationLots(Key key) {
    return jdbc.query("""
        SELECT lot.id, lot.component_key, lot.drug_name, lot.lot_number, lot.expiration_date,
               lot.quantity, lot.quantity_text, lot.quantity_unit, lot.diluent,
               lot.final_volume, lot.concentration, lot.ttl_minutes,
               lot.preparation_status, lot.created_at
          FROM application_preparation_lots lot
         WHERE lot.patient_id = ? AND lot.treatment_id = ? AND lot.cycle_number = ?
           AND lot.application_day = ? AND lot.preparation_status = 'active'
         ORDER BY lot.created_at, lot.drug_name
        """, (result, row) -> new PreparationLot(
            result.getObject("id", UUID.class), text(result, "component_key"),
            text(result, "drug_name"),
            text(result, "lot_number"), result.getDate("expiration_date").toLocalDate(),
            result.getBigDecimal("quantity"), text(result, "quantity_text"),
            text(result, "quantity_unit"), text(result, "diluent"),
            text(result, "final_volume"), text(result, "concentration"),
            result.getInt("ttl_minutes"), text(result, "preparation_status"),
            result.getTimestamp("created_at").toInstant()),
        key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay());
  }

  boolean restartPreparation(Key key, long expectedRevision, String notes, long actorId, Instant now) {
    int changed = jdbc.update("""
        UPDATE treatment_application_workflows
           SET medication_source = CASE medication_source
                 WHEN 'patient_has_medication' THEN 'patient_to_bring'
                 WHEN 'received_center' THEN 'pending_supplier'
                 ELSE medication_source
               END,
               stock_reservation_status = CASE
                 WHEN medication_source IN ('patient_has_medication','received_center')
                   THEN 'none'
                 WHEN medication_source = 'center_stock' THEN 'none'
                 ELSE stock_reservation_status
               END,
               clinical_authorization_status = 'pending',
               clinical_authorization_reason = NULL,
               clinical_assessment = '{}'::jsonb,
               clinically_authorized_by = NULL,
               clinically_authorized_at = NULL,
               preparation_status = 'not_started',
               preparation_data = jsonb_build_object(
                 'restartReason', NULLIF(?, ''),
                 'previousPreparationDiscardedAt', CAST(? AS text)
               ),
               preparation_started_by = NULL,
               preparation_started_at = NULL,
               prepared_by = NULL,
               preparation_verified_by = NULL,
               prepared_at = NULL,
               preparation_released_by = NULL,
               preparation_released_at = NULL,
               preparation_expires_at = NULL,
               preparation_restart_count = preparation_restart_count + 1,
               workflow_status = 'medication_pending',
               revision = revision + 1,
               updated_by = ?,
               updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND revision = ?
        """, notes, Timestamp.from(now), actorId, Timestamp.from(now),
        key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay(),
        expectedRevision);
    if (changed == 1) {
      jdbc.update("""
          UPDATE application_preparation_lots
             SET preparation_status = 'discarded', discarded_by = ?,
                 discarded_at = ?, discard_reason = NULLIF(?, '')
           WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
             AND application_day = ? AND preparation_status = 'active'
          """, actorId, Timestamp.from(now), notes, key.patientId(), key.treatmentId(),
          key.cycleNumber(), key.applicationDay());
    }
    return changed == 1;
  }

  private void synchronizeSessionAfterClinicalFail(Key key, String reason, long actorId, Instant now) {
    jdbc.update("""
        UPDATE unified_infusion_sessions
           SET scheduled_at = NULL, chair = NULL, clinical_status = 'cancelled',
               pharmacy_status = CASE
                 WHEN pharmacy_status IN ('in_preparation','ready','released')
                   THEN pharmacy_status ELSE 'cancelled' END,
               administration_status = 'withheld',
               notes = concat_ws(E'\n', NULLIF(notes, ''), NULLIF(?, '')),
               revision = revision + 1, updated_by = ?, updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND clinical_status <> 'cancelled'
        """, "Triaje FAIL: " + reason, actorId, Timestamp.from(now), key.patientId(),
        key.treatmentId(), key.cycleNumber(), key.applicationDay());
  }

  private void synchronizeSessionPreparation(Key key, String pharmacyStatus, long actorId, Instant now) {
    jdbc.update("""
        UPDATE unified_infusion_sessions
           SET pharmacy_status = ?, revision = revision + 1,
               updated_by = ?, updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND clinical_status <> 'cancelled'
        """, pharmacyStatus, actorId, Timestamp.from(now), key.patientId(),
        key.treatmentId(), key.cycleNumber(), key.applicationDay());
    jdbc.update("""
        UPDATE unified_infusion_medications m
           SET preparation_status = ?, revision = m.revision + 1,
               updated_by = ?, updated_at = ?
          FROM unified_infusion_sessions s
         WHERE m.infusion_session_id = s.id
           AND s.patient_id = ? AND s.treatment_id = ? AND s.cycle_number = ?
           AND s.application_day = ? AND s.clinical_status <> 'cancelled'
        """, pharmacyStatus, actorId, Timestamp.from(now), key.patientId(),
        key.treatmentId(), key.cycleNumber(), key.applicationDay());
  }

  private void synchronizeSessionAdministration(
      Key key, String administrationStatus, String clinicalStatus, long actorId, Instant now) {
    jdbc.update("""
        UPDATE unified_infusion_sessions
           SET administration_status = ?, clinical_status = ?,
               revision = revision + 1, updated_by = ?, updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND clinical_status <> 'cancelled'
        """, administrationStatus, clinicalStatus, actorId, Timestamp.from(now),
        key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay());
    jdbc.update("""
        UPDATE unified_infusion_medications m
           SET administration_status = ?, revision = m.revision + 1,
               updated_by = ?, updated_at = ?
          FROM unified_infusion_sessions s
         WHERE m.infusion_session_id = s.id
           AND s.patient_id = ? AND s.treatment_id = ? AND s.cycle_number = ?
           AND s.application_day = ? AND s.clinical_status <> 'cancelled'
        """, administrationStatus, actorId, Timestamp.from(now), key.patientId(),
        key.treatmentId(), key.cycleNumber(), key.applicationDay());
  }

  private Optional<WorkflowEvent> event(Key key, String idempotencyKey) {
    return jdbc.query("""
        SELECT event.id, event.action, event.expected_revision,
               event.resulting_revision, event.occurred_at, event.after_json::text,
               COALESCE(NULLIF(actor.display_name, ''), actor.username, 'Sistema') AS actor_name
          FROM treatment_application_workflow_events event
          LEFT JOIN local_users actor ON actor.id = event.actor_user_id
         WHERE event.patient_id = ? AND event.treatment_id = ? AND event.cycle_number = ?
           AND event.application_day = ? AND event.idempotency_key = ?
        """, (result, row) -> new WorkflowEvent(
            result.getLong("id"), result.getString("action"),
            result.getLong("expected_revision"), result.getLong("resulting_revision"),
            result.getTimestamp("occurred_at").toInstant(),
            result.getString("actor_name"),
            mapper.readTree(result.getString("after_json"))),
        key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay(),
        idempotencyKey).stream().findFirst();
  }

  private List<AuditEvent> events(Key key) {
    return jdbc.query("""
        SELECT event.id, event.action, event.expected_revision, event.resulting_revision,
               event.occurred_at,
               COALESCE(NULLIF(actor.display_name, ''), actor.username, 'Sistema') AS actor_name
          FROM treatment_application_workflow_events event
          LEFT JOIN local_users actor ON actor.id = event.actor_user_id
         WHERE event.patient_id = ? AND event.treatment_id = ? AND event.cycle_number = ?
           AND event.application_day = ?
         ORDER BY event.occurred_at, event.id
        """, (result, row) -> new AuditEvent(
            result.getLong("id"), result.getString("action"),
            result.getLong("expected_revision"), result.getLong("resulting_revision"),
            result.getTimestamp("occurred_at").toInstant(),
            result.getString("actor_name")),
        key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay());
  }

  private void updateEventAfter(Key key, String idempotencyKey, JsonNode after) {
    jdbc.update("""
        UPDATE treatment_application_workflow_events
           SET after_json = CAST(? AS jsonb)
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND idempotency_key = ?
        """, after.toString(), key.patientId(), key.treatmentId(), key.cycleNumber(),
        key.applicationDay(), idempotencyKey);
  }

  private String selectSql() {
    return """
        SELECT w.patient_id, w.treatment_id, w.cycle_number, w.application_day,
               w.workflow_status, w.medication_source,
               w.pharmacy_validation_status, w.pharmacy_validation_notes,
               w.pharmacy_validated_at, w.stock_reservation_status,
               w.stock_reservation_notes, w.stock_reserved_at, w.stock_released_at,
               w.clinical_authorization_status, w.clinical_authorization_reason,
               w.clinical_assessment::text, w.clinically_authorized_at,
               w.preparation_status, w.preparation_data::text,
               w.preparation_started_at, w.prepared_by,
               COALESCE(NULLIF(preparer.display_name, ''), preparer.username, '') AS prepared_by_name,
               w.preparation_verified_by,
               COALESCE(NULLIF(verifier.display_name, ''), verifier.username, '') AS preparation_verified_by_name,
               w.prepared_at, w.preparation_released_at,
               w.preparation_expires_at, w.preparation_restart_count,
               w.administration_status,
               w.administration_data::text, w.administration_started_at,
               w.administration_completed_at, w.revision, w.updated_at,
               l.planned_date, l.prescription_state, l.duration_minutes,
               l.duration_source, l.drug_summary, l.application_drugs::text,
               p.document_number, p.medical_record_number, p.first_name, p.last_name,
               p.health_insurance, p.health_insurance_number,
               t.diagnosis, t.scheme_name, t.treatment_type, t.cycle_count,
               s.id AS session_id, s.scheduled_at, s.chair,
               s.clinical_status AS session_clinical_status,
               s.pharmacy_status AS session_pharmacy_status,
               s.administration_status AS session_administration_status,
               s.appointment_confirmed
          FROM treatment_application_workflows w
          JOIN treatment_application_logistics l
            ON l.patient_id = w.patient_id AND l.treatment_id = w.treatment_id
           AND l.cycle_number = w.cycle_number AND l.application_day = w.application_day
          JOIN clinical_treatments t
            ON t.patient_id = w.patient_id AND t.id = w.treatment_id
          JOIN patients p ON p.source_id = w.patient_id
          LEFT JOIN local_users preparer ON preparer.id = w.prepared_by
          LEFT JOIN local_users verifier ON verifier.id = w.preparation_verified_by
          LEFT JOIN LATERAL (
            SELECT candidate.id, candidate.scheduled_at, candidate.chair,
                   candidate.clinical_status, candidate.pharmacy_status,
                   candidate.administration_status, candidate.appointment_confirmed
              FROM unified_infusion_sessions candidate
             WHERE candidate.patient_id = w.patient_id
               AND candidate.treatment_id = w.treatment_id
               AND candidate.cycle_number = w.cycle_number
               AND candidate.application_day = w.application_day
               AND candidate.clinical_status <> 'cancelled'
             ORDER BY candidate.scheduled_at NULLS LAST, candidate.id DESC
             LIMIT 1
          ) s ON true
        """;
  }

  private String keyWhere() {
    return """
         WHERE w.patient_id = ? AND w.treatment_id = ? AND w.cycle_number = ?
           AND w.application_day = ?
        """;
  }

  private Application mapApplication(ResultSet result, int row) throws SQLException {
    Date planned = result.getDate("planned_date");
    Long sessionId = nullableLong(result, "session_id");
    return new Application(
        new Key(result.getLong("patient_id"), result.getString("treatment_id"),
            result.getInt("cycle_number"), result.getInt("application_day")),
        text(result, "workflow_status"), text(result, "prescription_state"),
        text(result, "medication_source"), text(result, "pharmacy_validation_status"),
        text(result, "pharmacy_validation_notes"), instant(result, "pharmacy_validated_at"),
        text(result, "stock_reservation_status"), text(result, "stock_reservation_notes"),
        instant(result, "stock_reserved_at"), instant(result, "stock_released_at"),
        text(result, "clinical_authorization_status"),
        text(result, "clinical_authorization_reason"),
        mapper.readTree(result.getString("clinical_assessment")),
        instant(result, "clinically_authorized_at"),
        text(result, "preparation_status"),
        mapper.readTree(result.getString("preparation_data")),
        instant(result, "preparation_started_at"),
        nullableLong(result, "prepared_by"), text(result, "prepared_by_name"),
        nullableLong(result, "preparation_verified_by"),
        text(result, "preparation_verified_by_name"),
        instant(result, "prepared_at"),
        instant(result, "preparation_released_at"), instant(result, "preparation_expires_at"),
        result.getInt("preparation_restart_count"),
        text(result, "administration_status"),
        mapper.readTree(result.getString("administration_data")),
        instant(result, "administration_started_at"),
        instant(result, "administration_completed_at"),
        result.getLong("revision"), result.getTimestamp("updated_at").toInstant(),
        planned == null ? null : planned.toLocalDate(), result.getInt("duration_minutes"),
        text(result, "duration_source"), text(result, "drug_summary"),
        mapper.readTree(result.getString("application_drugs")),
        text(result, "document_number"), text(result, "medical_record_number"),
        text(result, "first_name"), text(result, "last_name"),
        text(result, "health_insurance"), text(result, "health_insurance_number"),
        text(result, "diagnosis"), text(result, "scheme_name"),
        text(result, "treatment_type"), result.getInt("cycle_count"),
        sessionId, instant(result, "scheduled_at"), text(result, "chair"),
        text(result, "session_clinical_status"), text(result, "session_pharmacy_status"),
        text(result, "session_administration_status"),
        sessionId != null && result.getBoolean("appointment_confirmed"));
  }

  private Reservation mapReservation(ResultSet result, int row) throws SQLException {
    return new Reservation(
        result.getObject("id", UUID.class), text(result, "component_key"),
        text(result, "drug_id"), text(result, "drug_name"),
        result.getBigDecimal("requested_quantity"), text(result, "requested_quantity_text"),
        result.getBigDecimal("reserved_quantity"), text(result, "quantity_unit"),
        text(result, "medication_source"), text(result, "reservation_status"),
        text(result, "verification_method"), nullableLong(result, "inventory_lot_id"),
        text(result, "verification_notes"));
  }

  private String text(ResultSet result, String column) throws SQLException {
    String value = result.getString(column);
    return value == null ? "" : value;
  }

  private Long nullableLong(ResultSet result, String column) throws SQLException {
    Object value = result.getObject(column);
    return value == null ? null : result.getLong(column);
  }

  private Instant instant(ResultSet result, String column) throws SQLException {
    Timestamp value = result.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }

  private String normalizeSearch(String value) {
    if (value == null || value.isBlank()) return "";
    return Normalizer.normalize(value.trim().toLowerCase(), Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .replaceAll("[^a-z0-9/.-]+", " ")
        .trim();
  }

  // ---------- orquestación (antes ApplicationWorkflowService) ----------

  private ObjectNode administrationData(Application current) {
    return current.administrationData().isObject()
        ? (ObjectNode) current.administrationData().deepCopy()
        : mapper.createObjectNode();
  }

  static AdministrationReaction resolveAdministrationReaction(
      JsonNode administrationData, boolean reportedReaction, String reportedDescription) {
    String description = reportedDescription == null ? "" : reportedDescription.trim();
    JsonNode interruptions = administrationData == null ? null : administrationData.path("interruptions");
    int interruptionCount = interruptions != null && interruptions.isArray() ? interruptions.size() : 0;
    if (interruptionCount == 0) {
      return new AdministrationReaction(reportedReaction, reportedReaction ? description : "", false, 0);
    }
    List<String> summaries = new ArrayList<>();
    for (int index = 0; index < interruptionCount; index++) {
      JsonNode interruption = interruptions.get(index);
      List<String> details = new ArrayList<>();
      addReactionDetail(details, "Motivo", interruption.path("reason").asText(""));
      addReactionDetail(details, "Medidas", interruption.path("measures").asText(""));
      addReactionDetail(details, "Condición", interruption.path("patientCondition").asText(""));
      addReactionDetail(
          details, "Destino", interruptionDispositionLabel(interruption.path("disposition").asText("")));
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
        ? history : description + "\nAntecedentes de la administración: " + history;
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

  private void reserve(Application current, StockReservationCommand command, long actorId) {
    check(ApplicationWorkflowPolicy.reserveStock(current.policyState()));
    String source = normalizeSource(command.medicationSource(), false);
    if (source.isBlank()) source = current.medicationSource();
    if (!source.equals(current.medicationSource())) {
      throw new InfusionFailure(
          InfusionFailure.Type.CONFLICT,
          "La fuente de medicación cambió; vuelva a validar la orden en Farmacia.",
          "MEDICATION_SOURCE_CHANGED");
    }
    String method = trim(command.verificationMethod()).toLowerCase();
    List<StockComponentInput> components = command.components() == null
        ? List.of() : command.components();
    if (method.isBlank()) method = components.isEmpty() ? "manual" : "inventory";
    if (!Set.of("manual", "inventory").contains(method)) {
      throw badRequest("El método de verificación de stock es inválido.");
    }
    String notes = trim(command.notes());
    if ("manual".equals(method) && notes.length() < 10) {
      throw badRequest("La constatación manual exige una nota de al menos 10 caracteres.");
    }
    if (components.isEmpty()) {
      components = ApplicationComponentValidator.componentsFromDrugs(current.applicationDrugs());
    }
    if (components.isEmpty()) {
      throw badRequest("El protocolo no contiene componentes reservables.");
    }
    ApplicationComponentValidator.validateStockComponents(current.applicationDrugs(), components);
    Instant now = clock.instant();
    for (StockComponentInput component : components) {
      String drugName = trim(component.drugName());
      if (drugName.isBlank()) throw badRequest("Cada componente debe indicar la droga.");
      String componentKey = trim(component.componentKey());
      BigDecimal quantity = component.requestedQuantity();
      String unit = trim(component.unit());
      if (quantity != null && quantity.signum() <= 0) {
        throw badRequest("La cantidad solicitada debe ser mayor que cero.");
      }
      if ("manual".equals(method) && (quantity == null || unit.isBlank())) {
        throw badRequest("La constatación manual exige cantidad y unidad para " + drugName + ".");
      }
      if ("inventory".equals(method)) {
        if (component.inventoryLotId() == null || quantity == null || unit.isBlank()) {
          throw badRequest("Una reserva de inventario exige lote, cantidad numérica y unidad.");
        }
        boolean available = reserveInventory(
            component.inventoryLotId(), trim(component.drugId()), drugName,
            quantity, unit, actorId, now);
        if (!available) {
          throw new InfusionFailure(
              InfusionFailure.Type.CONFLICT,
              "El lote no existe, está vencido o no posee cantidad disponible para " + drugName + ".",
              "INSUFFICIENT_STOCK");
        }
      }
      insertReservation(
          UUID.randomUUID(), current.key(), componentKey, trim(component.drugId()),
          drugName, quantity, trim(component.requestedQuantityText()), unit,
          "center_stock", "reserved", method, component.inventoryLotId(),
          notes, actorId, now);
    }
    changed(updateReservationStatus(current.key(), current.revision(), "reserved", notes, actorId, now));
  }

  private void completePreparation(
      Application current, PreparationCompleteCommand command, long actorId, String actorDisplayName) {
    check(ApplicationWorkflowPolicy.completePreparation(current.policyState()));
    long verifiedBy = resolveEnabledUser(trim(command.verifiedBy()), "application.preparation.manage")
        .orElseThrow(() -> badRequest(
            "Seleccione un segundo profesional habilitado para verificar la preparación."));
    if (verifiedBy == actorId) {
      throw badRequest("La preparación debe ser verificada por otro profesional.");
    }
    List<Reservation> reservations = activeReservations(current.key()).stream()
        .filter(item -> "reserved".equals(item.status()))
        .toList();
    if ("center_stock".equals(current.medicationSource()) && reservations.isEmpty()) {
      throw new InfusionFailure(
          InfusionFailure.Type.CONFLICT,
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
      PreparationInput item = resolvedPreparation.preparation();
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
      if (ttl < 1 || ttl > 10080) throw badRequest("El TTL debe estar entre 1 minuto y 7 días.");
      if (item.quantity() != null && item.quantity().signum() <= 0) {
        throw badRequest("La cantidad preparada debe ser mayor que cero.");
      }
      Reservation reservation = matchReservation(componentKey, item, reservations, usedReservations);
      if ("center_stock".equals(current.medicationSource()) && reservation == null) {
        throw new InfusionFailure(
            InfusionFailure.Type.CONFLICT,
            "La preparación de " + drugName + " no coincide con una reserva activa.",
            "PREPARATION_WITHOUT_RESERVATION");
      }
      if (reservation != null
          && reservation.inventoryLotId() != null
          && item.inventoryLotId() != null
          && !reservation.inventoryLotId().equals(item.inventoryLotId())) {
        throw badRequest("El lote preparado no coincide con el lote reservado.");
      }
      if (reservation != null) validateReservationPreparation(componentKey, reservation, item);
      UUID reservationId = reservation == null ? null : reservation.id();
      Long inventoryLotId = reservation != null && reservation.inventoryLotId() != null
          ? reservation.inventoryLotId() : item.inventoryLotId();
      if (inventoryLotId != null) validateInventoryPreparation(inventoryLotId, reservation, item);
      insertPreparationLot(
          UUID.randomUUID(), current.key(), componentKey, reservationId, inventoryLotId,
          drugName, lot, item.expiryDate(), item.quantity(), trim(item.quantityText()),
          unit, diluent, finalVolume, concentration, ttl, actorId, verifiedBy, now);
      if (reservationId != null) usedReservations.add(reservationId);
      minimumTtl = Math.min(minimumTtl, ttl);
      ObjectNode traceItem = mapper.valueToTree(item);
      traceItem.put("componentKey", componentKey);
      trace.add(traceItem);
    }
    if ("center_stock".equals(current.medicationSource())
        && usedReservations.size() != reservations.size()) {
      throw new InfusionFailure(
          InfusionFailure.Type.CONFLICT,
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
    data.put("preparedByUserId", actorId);
    data.put("preparedByDisplayName", actorDisplayName);
    data.put("verifiedByUserId", verifiedBy);
    data.put("verifiedByDisplayName", userDisplayName(verifiedBy));
    consumeReservations(current.key(), actorId, now);
    changed(updatePreparation(
        current.key(), current.revision(), "prepared", data, expiresAt, verifiedBy, actorId, now));
    synchronizeSessionPreparation(current.key(), "ready", actorId, now);
  }

  private Reservation matchReservation(
      String componentKey, PreparationInput item, List<Reservation> reservations, Set<UUID> used) {
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
      String componentKey, Reservation reservation, PreparationInput preparation) {
    if (!componentKey.equals(reservation.componentKey())
        || !reservation.drugName().equalsIgnoreCase(trim(preparation.drugName()))) {
      throw new InfusionFailure(
          InfusionFailure.Type.CONFLICT,
          "La preparación no coincide con la droga reservada para " + componentKey + ".",
          "PREPARATION_COMPONENT_MISMATCH");
    }
    if (reservation.requestedQuantity() == null
        || preparation.quantity() == null
        || preparation.quantity().compareTo(reservation.requestedQuantity()) != 0) {
      throw new InfusionFailure(
          InfusionFailure.Type.CONFLICT,
          "La cantidad preparada no coincide con la cantidad reservada para " + componentKey + ".",
          "PREPARATION_COMPONENT_MISMATCH");
    }
    if (!trim(reservation.unit()).equalsIgnoreCase(trim(preparation.unit()))) {
      throw new InfusionFailure(
          InfusionFailure.Type.CONFLICT,
          "La unidad preparada no coincide con la unidad reservada para " + componentKey + ".",
          "PREPARATION_COMPONENT_MISMATCH");
    }
  }

  private void validateInventoryPreparation(
      long inventoryLotId, Reservation reservation, PreparationInput preparation) {
    InventoryLot lot = inventoryLot(inventoryLotId)
        .orElseThrow(() -> new InfusionFailure(
            InfusionFailure.Type.CONFLICT, "El lote de inventario vinculado ya no existe.", "INVENTORY_LOT_MISMATCH"));
    if (reservation == null || reservation.inventoryLotId() == null
        || reservation.inventoryLotId() != inventoryLotId) {
      throw new InfusionFailure(
          InfusionFailure.Type.CONFLICT,
          "El lote preparado no está respaldado por la reserva de esta aplicación.",
          "INVENTORY_LOT_MISMATCH");
    }
    if (!lot.lotNumber().equalsIgnoreCase(trim(preparation.lot()))
        || !lot.expirationDate().equals(preparation.expiryDate())
        || !lot.unit().equalsIgnoreCase(trim(preparation.unit()))
        || !lot.drugName().equalsIgnoreCase(trim(preparation.drugName()))) {
      throw new InfusionFailure(
          InfusionFailure.Type.CONFLICT,
          "Droga, lote, vencimiento y unidad deben coincidir con el inventario reservado.",
          "INVENTORY_LOT_MISMATCH");
    }
    if (preparation.quantity() == null || reservation.reservedQuantity() == null
        || preparation.quantity().compareTo(reservation.reservedQuantity()) != 0
        || lot.quantityReserved().compareTo(reservation.reservedQuantity()) < 0) {
      throw new InfusionFailure(
          InfusionFailure.Type.CONFLICT,
          "La cantidad preparada debe coincidir con la cantidad reservada.",
          "INVENTORY_QUANTITY_MISMATCH");
    }
  }

  private CommandResult execute(
      Key key, String action, long expectedRevision, String idempotencyKey,
      Object command, long actorId, Mutation mutation) {
    ensureWorkflowRows();
    Application current = lock(key)
        .orElseThrow(() -> new InfusionFailure(InfusionFailure.Type.NOT_FOUND, "No existe esa aplicación del tratamiento."));
    var previous = event(key, idempotencyKey).orElse(null);
    if (previous != null) {
      if (!action.equals(previous.action())) {
        throw new InfusionFailure(
            InfusionFailure.Type.CONFLICT,
            "La clave de idempotencia ya fue utilizada para otra acción.",
            "IDEMPOTENCY_KEY_REUSED");
      }
      JsonNode replay = withOwnAuditEvent(previous);
      updateEventAfter(key, idempotencyKey, replay);
      return new CommandResult(replay, true, null, null);
    }
    if (current.revision() != expectedRevision) {
      throw new InfusionFailure(
          InfusionFailure.Type.CONFLICT, "La aplicación fue modificada por otro usuario.", "VERSION_CONFLICT");
    }
    JsonNode before = mapper.valueToTree(view(current, true));
    mutation.apply(current);
    Application updated = require(key);
    JsonNode after = mapper.valueToTree(view(updated, true));
    Instant eventAt = clock.instant();
    insertEvent(
        key, action, idempotencyKey, actorId, expectedRevision,
        updated.revision(), mapper.valueToTree(command), before, after, eventAt);
    WorkflowEvent stored = event(key, idempotencyKey).orElseThrow();
    JsonNode auditedAfter = withOwnAuditEvent(stored);
    updateEventAfter(key, idempotencyKey, auditedAfter);
    return new CommandResult(auditedAfter, false, null, null);
  }

  private CommandResult withEvolution(CommandResult result, EvolutionAppend appended) {
    return new CommandResult(
        result.workflow(), result.idempotentReplay(), appended.evolution(), appended.revision());
  }

  private JsonNode withOwnAuditEvent(WorkflowEvent event) {
    ObjectNode snapshot = event.after().isObject()
        ? (ObjectNode) event.after().deepCopy()
        : mapper.createObjectNode();
    JsonNode existing = snapshot.get("auditTrail");
    ArrayNode audit = existing != null && existing.isArray() ? (ArrayNode) existing : mapper.createArrayNode();
    if (existing == null || !existing.isArray()) snapshot.set("auditTrail", audit);
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
      long patientId, String treatmentId, int cycle, int day, String event, String reason,
      String text, long actorId, String actorDisplayName) {
    Instant now = clock.instant();
    ObjectNode evolution = mapper.createObjectNode();
    evolution.put("id", event + "-" + patientId + "-" + treatmentId + "-" + cycle + "-" + day);
    evolution.put("date", LocalDate.now(clock).toString());
    evolution.put("datePrecision", "day");
    evolution.put("author", actorDisplayName);
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
    try {
      return documents.appendImmutableEvolution(patientId, evolution, actorId);
    } catch (PatientFailure failure) {
      throw new InfusionFailure(
          failure.type() == PatientFailure.Type.CONFLICT
              ? InfusionFailure.Type.CONFLICT : InfusionFailure.Type.NOT_FOUND,
          failure.getMessage());
    }
  }

  private Application require(Key key) {
    return find(key)
        .orElseThrow(() -> new InfusionFailure(InfusionFailure.Type.NOT_FOUND, "No existe esa aplicación del tratamiento."));
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
    result.put("patientMustBringMedication", "patient_to_bring".equals(item.medicationSource()));
    result.put("medicationReady", ApplicationWorkflowPolicy.medicationReady(item.policyState()));
    result.put("pharmacyValidationStatus", item.pharmacyValidationStatus());
    result.put("pharmacyValidationNotes", item.pharmacyValidationNotes());
    result.put("pharmacyValidatedAt", value(item.pharmacyValidatedAt()));
    result.put("pharmacyValidationTraceable",
        item.pharmacyValidatedAt() != null && "approved".equals(item.pharmacyValidationStatus()));
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
    appointment.put("readyToday", item.hasConfirmedAppointmentOn(LocalDate.now(clock), clock.getZone()));
    result.put("appointment", appointment);
    if (includeReservations) {
      result.put("stockReservations", reservations(item.key(), false).stream().map(this::reservationView).toList());
      result.put("auditTrail", events(item.key()).stream().map(event -> Map.of(
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
        || !ar.com.hexium.hcop.treatment.domain.DayHospitalApplicationPolicy.isValidApplicationDay(day)) {
      throw badRequest("La identificación de la aplicación es inválida.");
    }
    return new Key(patientId, treatmentId.trim(), cycle, day);
  }

  private void requireBody(Object command) {
    if (command == null) throw badRequest("El cuerpo JSON de la solicitud es obligatorio.");
  }

  private void requireCommand(Long expectedRevision, String idempotencyKey) {
    if (expectedRevision == null || expectedRevision < 1) {
      throw badRequest("Informe expectedRevision para proteger cambios concurrentes.");
    }
    String key = trim(idempotencyKey);
    if (key.length() < 8 || key.length() > 128 || !key.matches("[A-Za-z0-9._:-]+")) {
      throw badRequest("idempotencyKey debe tener entre 8 y 128 caracteres seguros.");
    }
  }

  private boolean isObject(JsonNode value) {
    return value != null && value.isObject();
  }

  private void validateTriage(JsonNode laboratory, JsonNode vitalSigns, boolean passed) {
    validateNumber(vitalSigns, 1, 500, "weightKg", "pesoKg", "weight");
    validateNumber(vitalSigns, 30, 45, "temperatureC", "temperature", "temperatura");
    validateNumber(vitalSigns, 20, 250, "heartRate", "pulse", "frecuenciaCardiaca");
    validateNumber(vitalSigns, 50, 300, "systolic", "systolicPressure", "presionSistolica");
    validateNumber(vitalSigns, 30, 200, "diastolic", "diastolicPressure", "presionDiastolica");
    validateNumber(vitalSigns, 50, 100, "oxygenSaturation", "spo2", "saturacion");
    String laboratoryDate = firstText(laboratory, "date", "sampleDate", "laboratoryDate", "fecha");
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
    requireNumber(laboratory, 0, 100000, "neutrophils");
    requireNumber(laboratory, 0, 2000000, "platelets");
    requireNumber(laboratory, 0.01, 50, "creatinine");
    requireNumber(vitalSigns, 1, 500, "weightKg");
    requireNumber(vitalSigns, 30, 45, "temperatureC");
    String pressure = firstText(vitalSigns, "bloodPressure", "pressure", "presionArterial");
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

  private List<String> triageSafetyAlerts(JsonNode laboratory, JsonNode vitalSigns, JsonNode toxicity) {
    List<String> alerts = new ArrayList<>();
    double neutrophils = requireNumber(laboratory, 0, 100000, "neutrophils");
    double platelets = requireNumber(laboratory, 0, 2000000, "platelets");
    double temperature = requireNumber(vitalSigns, 30, 45, "temperatureC");
    double toxicityGrade = requireNumber(toxicity, 0, 5, "grade");
    if (neutrophils < 1000) alerts.add("neutrófilos < 1.000/mm3");
    if (platelets < 75000) alerts.add("plaquetas < 75.000/mm3");
    if (temperature >= 38) alerts.add("temperatura ≥ 38 °C");
    if (toxicityGrade >= 3) alerts.add("toxicidad grado ≥ 3");
    JsonNode saturation = vitalSigns.path("oxygenSaturation");
    if (!saturation.isMissingNode() && !saturation.isNull() && !saturation.asText("").isBlank()) {
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
      throw new InfusionFailure(
          InfusionFailure.Type.CONFLICT, "La orden no contiene drogas para esta aplicación.", "INCOMPLETE_PHARMACY_ORDER");
    }
    List<String> incomplete = new ArrayList<>();
    for (JsonNode drug : drugs) {
      String name = firstText(drug, "drugName", "name", "droga", "genericName");
      String dose = firstText(
          drug, "prescribedDoseText", "calculatedDoseText", "totalDoseText",
          "dose", "dosis", "dosisDiaria", "calculatedDose");
      String unit = firstText(drug, "doseUnit", "unidad", "unidadDosis");
      String route = firstText(drug, "route", "via", "viaAdministracion");
      String method = normalized(firstText(drug, "calculationMethod", "calculoDosis", "doseCalculation"));
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
      throw new InfusionFailure(
          InfusionFailure.Type.CONFLICT,
          "Complete dosis, unidad y vía antes de validar: " + String.join(", ", incomplete)
              + ". Corrija el protocolo desde Configuración > Protocolos.",
          "INCOMPLETE_PHARMACY_ORDER");
    }
  }

  private void validateNumber(JsonNode object, double minimum, double maximum, String... fields) {
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

  private double requireNumber(JsonNode object, double minimum, double maximum, String field) {
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
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;");
  }

  private String value(Instant value) {
    return value == null ? null : value.toString();
  }

  private void changed(boolean changed) {
    if (!changed) {
      throw new InfusionFailure(InfusionFailure.Type.CONFLICT, "La aplicación fue modificada por otro usuario.", "VERSION_CONFLICT");
    }
  }

  private InfusionFailure badRequest(String message) {
    return new InfusionFailure(InfusionFailure.Type.INVALID, message);
  }

  private void check(Optional<Violation> violation) {
    violation.ifPresent(v -> {
      InfusionFailure.Type type = v.type() == Violation.Type.CONFLICT
          ? InfusionFailure.Type.CONFLICT : InfusionFailure.Type.INVALID;
      throw v.code() == null
          ? new InfusionFailure(type, v.message())
          : new InfusionFailure(type, v.message(), v.code());
    });
  }

  @FunctionalInterface
  private interface Mutation {
    void apply(Application current);
  }

  record AdministrationReaction(
      boolean occurred, String description, boolean derivedFromInterruptions, int interruptionCount) {
  }

  // ---------- registros de infraestructura (antes ApplicationWorkflowRepository) ----------

  public record Key(long patientId, String treatmentId, int cycleNumber, int applicationDay) {
  }

  public record Application(
      Key key,
      String workflowStatus,
      String prescriptionStatus,
      String medicationSource,
      String pharmacyValidationStatus,
      String pharmacyValidationNotes,
      Instant pharmacyValidatedAt,
      String stockReservationStatus,
      String stockReservationNotes,
      Instant stockReservedAt,
      Instant stockReleasedAt,
      String clinicalAuthorizationStatus,
      String clinicalAuthorizationReason,
      JsonNode clinicalAssessment,
      Instant clinicallyAuthorizedAt,
      String preparationStatus,
      JsonNode preparationData,
      Instant preparationStartedAt,
      Long preparedBy,
      String preparedByName,
      Long preparationVerifiedBy,
      String preparationVerifiedByName,
      Instant preparedAt,
      Instant preparationReleasedAt,
      Instant preparationExpiresAt,
      int preparationRestartCount,
      String administrationStatus,
      JsonNode administrationData,
      Instant administrationStartedAt,
      Instant administrationCompletedAt,
      long revision,
      Instant updatedAt,
      LocalDate plannedDate,
      int durationMinutes,
      String durationSource,
      String drugSummary,
      JsonNode applicationDrugs,
      String patientDni,
      String medicalRecord,
      String firstName,
      String lastName,
      String insurance,
      String affiliateNumber,
      String diagnosis,
      String scheme,
      String treatmentType,
      int totalCycles,
      Long sessionId,
      Instant scheduledAt,
      String chair,
      String sessionClinicalStatus,
      String sessionPharmacyStatus,
      String sessionAdministrationStatus,
      boolean appointmentConfirmed) {
    public String patientName() {
      return (lastName + ", " + firstName).replaceAll("(^[, ]+|[, ]+$)", "");
    }

    public boolean hasActiveAppointment() {
      return sessionId != null;
    }

    public boolean hasConfirmedAppointmentOn(LocalDate date, ZoneId zone) {
      return sessionId != null
          && appointmentConfirmed
          && scheduledAt != null
          && LocalDate.ofInstant(scheduledAt, zone).equals(date);
    }

    public ApplicationWorkflowPolicy.State policyState() {
      return new ApplicationWorkflowPolicy.State(
          workflowStatus, prescriptionStatus, medicationSource,
          pharmacyValidationStatus, stockReservationStatus,
          clinicalAuthorizationStatus, preparationStatus, administrationStatus);
    }
  }

  public record Reservation(
      UUID id, String componentKey, String drugId, String drugName, BigDecimal requestedQuantity,
      String requestedQuantityText, BigDecimal reservedQuantity, String unit, String source,
      String status, String verificationMethod, Long inventoryLotId, String notes) {
  }

  public record InventoryLot(
      long id, String drugId, String drugName, String lotNumber, LocalDate expirationDate,
      BigDecimal quantityOnHand, BigDecimal quantityReserved, String unit, String status) {
  }

  public record PreparationLot(
      UUID id, String componentKey, String drugName, String lotNumber, LocalDate expirationDate,
      BigDecimal quantity, String quantityText, String unit, String diluent, String finalVolume,
      String concentration, int ttlMinutes, String status, Instant createdAt) {
  }

  public record WorkflowEvent(
      long id, String action, long expectedRevision, long resultingRevision, Instant occurredAt,
      String actorName, JsonNode after) {
  }

  public record AuditEvent(
      long id, String action, long expectedRevision, long resultingRevision, Instant occurredAt,
      String actorName) {
  }

  public record ScheduleGate(
      String prescriptionStatus, String continuityStatus, boolean prescriptionRequired,
      String pharmacyValidationStatus, String medicationSource, String stockReservationStatus,
      String clinicalAuthorizationStatus, String clinicalAuthorizationReason,
      JsonNode clinicalAssessment, String preparationStatus, String administrationStatus,
      String workflowStatus, long revision) {
  }
}
