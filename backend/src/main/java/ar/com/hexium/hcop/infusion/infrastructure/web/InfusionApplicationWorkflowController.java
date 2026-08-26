package ar.com.hexium.hcop.infusion.infrastructure.web;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.infusion.application.port.in.ApplicationWorkflowUseCase;
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
import ar.com.hexium.hcop.infusion.infrastructure.web.ApplicationWorkflowCommands.AdministrationComplete;
import ar.com.hexium.hcop.infusion.infrastructure.web.ApplicationWorkflowCommands.AdministrationInterrupt;
import ar.com.hexium.hcop.infusion.infrastructure.web.ApplicationWorkflowCommands.AdministrationResolve;
import ar.com.hexium.hcop.infusion.infrastructure.web.ApplicationWorkflowCommands.AdministrationStart;
import ar.com.hexium.hcop.infusion.infrastructure.web.ApplicationWorkflowCommands.Basic;
import ar.com.hexium.hcop.infusion.infrastructure.web.ApplicationWorkflowCommands.ClinicalAuthorization;
import ar.com.hexium.hcop.infusion.infrastructure.web.ApplicationWorkflowCommands.PharmacyValidation;
import ar.com.hexium.hcop.infusion.infrastructure.web.ApplicationWorkflowCommands.Preparation;
import ar.com.hexium.hcop.infusion.infrastructure.web.ApplicationWorkflowCommands.PreparationComplete;
import ar.com.hexium.hcop.infusion.infrastructure.web.ApplicationWorkflowCommands.StockComponent;
import ar.com.hexium.hcop.infusion.infrastructure.web.ApplicationWorkflowCommands.StockReservation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(
    name = "Hospital de Día",
    description = "Circuito auditable por aplicación: Farmacia, triaje, preparación y administración.")
public class InfusionApplicationWorkflowController {
  private static final String ROOT =
      "/api/clinical/application-workflows/{patientId}/{treatmentId}/{cycleNumber}/{applicationDay}";

  private final ApplicationWorkflowUseCase workflows;
  private final AuthContext auth;

  public InfusionApplicationWorkflowController(ApplicationWorkflowUseCase workflows, AuthContext auth) {
    this.workflows = workflows;
    this.auth = auth;
  }

  @GetMapping("/api/clinical/application-workflows")
  @Operation(
      summary = "Listar una cola operativa por aplicación",
      description = "Devuelve las aplicaciones en la cola operativa indicada (Farmacia, "
          + "triaje, preparación o administración), filtrables por fecha, texto libre y "
          + "origen de la medicación.")
  Map<String, Object> list(
      @Parameter(description = "Cola operativa a listar: applications, pharmacy, "
          + "triage, preparation o administration")
      @RequestParam(defaultValue = "pharmacy") String queue,
      @Parameter(description = "Fecha a filtrar (aplica sólo a colas distintas de pharmacy)")
      @RequestParam(required = false) LocalDate date,
      @Parameter(description = "Texto libre para filtrar por paciente o droga")
      @RequestParam(defaultValue = "") String q,
      @Parameter(description = "Filtra por origen de la medicación")
      @RequestParam(defaultValue = "") String medicationSource,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.day-hospital.view");
    List<Map<String, Object>> items = workflows.list(queue, date, q, medicationSource);
    return Map.of("ok", true, "queue", queue, "items", items, "total", items.size());
  }

  @GetMapping(ROOT)
  @Operation(
      summary = "Abrir el circuito completo de una aplicación",
      description = "Devuelve el estado completo del circuito de una aplicación puntual "
          + "(paciente, tratamiento, ciclo y día): validación de Farmacia, reserva de "
          + "stock, triaje, preparación y administración.")
  Map<String, Object> get(
      @Parameter(description = "Id interno del paciente")
      @PathVariable long patientId, @Parameter(description = "Id del tratamiento")
      @PathVariable String treatmentId,
      @Parameter(description = "Número de ciclo del tratamiento")
      @PathVariable int cycleNumber, @Parameter(description = "Día de aplicación dentro del ciclo")
      @PathVariable int applicationDay,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.day-hospital.view");
    return Map.of("ok", true, "workflow", workflows.get(patientId, treatmentId, cycleNumber, applicationDay));
  }

  @PostMapping(ROOT + "/pharmacy-validation")
  @Operation(
      summary = "Validar la orden en Farmacia",
      description = "Aprueba o rechaza la orden desde Farmacia, registrando el origen de "
          + "la medicación. Idempotente por `idempotencyKey`; exige `expectedRevision` "
          + "para evitar pisar un cambio concurrente.")
  Map<String, Object> pharmacyValidation(
      @Parameter(description = "Id interno del paciente")
      @PathVariable long patientId, @Parameter(description = "Id del tratamiento")
      @PathVariable String treatmentId,
      @Parameter(description = "Número de ciclo del tratamiento")
      @PathVariable int cycleNumber, @Parameter(description = "Día de aplicación dentro del ciclo")
      @PathVariable int applicationDay,
      @RequestBody PharmacyValidation body, HttpServletRequest request) {
    auth.requirePermission(request, "application.pharmacy.manage");
    SessionPrincipal actor = auth.require(request);
    return response(workflows.pharmacyValidation(
        patientId, treatmentId, cycleNumber, applicationDay,
        new PharmacyValidationCommand(
            body.expectedRevision(), body.idempotencyKey(), body.validated(),
            body.medicationSource(), body.notes()),
        actor.userId(), actor.displayName()));
  }

  @PostMapping(ROOT + "/stock-reservation")
  @Operation(
      summary = "Reservar o liberar stock por componente",
      description = "Reserva o libera stock por cada componente/droga prescripto, contra "
          + "inventario electrónico o de forma manual. Exige correspondencia uno a uno "
          + "con la prescripción (sin faltantes, extras ni duplicados).")
  Map<String, Object> stockReservation(
      @Parameter(description = "Id interno del paciente")
      @PathVariable long patientId, @Parameter(description = "Id del tratamiento")
      @PathVariable String treatmentId,
      @Parameter(description = "Número de ciclo del tratamiento")
      @PathVariable int cycleNumber, @Parameter(description = "Día de aplicación dentro del ciclo")
      @PathVariable int applicationDay,
      @RequestBody StockReservation body, HttpServletRequest request) {
    auth.requirePermission(request, "application.pharmacy.manage");
    SessionPrincipal actor = auth.require(request);
    List<StockComponentInput> components = body.components() == null ? null
        : body.components().stream().map(this::toInput).toList();
    return response(workflows.stockReservation(
        patientId, treatmentId, cycleNumber, applicationDay,
        new StockReservationCommand(
            body.expectedRevision(), body.idempotencyKey(), body.reserved(), body.medicationSource(),
            body.verificationMethod(), body.notes(), components),
        actor.userId(), actor.displayName()));
  }

  @PostMapping(ROOT + "/clinical-authorization")
  @Operation(
      summary = "Registrar triaje y emitir PASS o FAIL",
      description = "Registra el triaje clínico (laboratorio, signos vitales, toxicidad) y "
          + "emite la autorización PASS/FAIL que habilita o bloquea la preparación. Un FAIL "
          + "puede reprogramar la fecha con motivo.")
  Map<String, Object> clinicalAuthorization(
      @Parameter(description = "Id interno del paciente")
      @PathVariable long patientId, @Parameter(description = "Id del tratamiento")
      @PathVariable String treatmentId,
      @Parameter(description = "Número de ciclo del tratamiento")
      @PathVariable int cycleNumber, @Parameter(description = "Día de aplicación dentro del ciclo")
      @PathVariable int applicationDay,
      @RequestBody ClinicalAuthorization body, HttpServletRequest request) {
    auth.requirePermission(request, "application.triage.manage");
    SessionPrincipal actor = auth.require(request);
    return response(workflows.clinicalAuthorization(
        patientId, treatmentId, cycleNumber, applicationDay,
        new ClinicalAuthorizationCommand(
            body.expectedRevision(), body.idempotencyKey(), body.decision(),
            body.laboratory(), body.vitalSigns(), body.toxicity(), body.reason(), body.rescheduledDate()),
        actor.userId(), actor.displayName()));
  }

  @PostMapping(ROOT + "/preparation/start")
  @Operation(
      summary = "Iniciar preparación estéril",
      description = "Marca el inicio de la preparación estéril de la mezcla, una vez que "
          + "Farmacia y triaje ya aprobaron la aplicación.")
  Map<String, Object> preparationStart(
      @Parameter(description = "Id interno del paciente")
      @PathVariable long patientId, @Parameter(description = "Id del tratamiento")
      @PathVariable String treatmentId,
      @Parameter(description = "Número de ciclo del tratamiento")
      @PathVariable int cycleNumber, @Parameter(description = "Día de aplicación dentro del ciclo")
      @PathVariable int applicationDay,
      @RequestBody Basic body, HttpServletRequest request) {
    auth.requirePermission(request, "application.preparation.manage");
    SessionPrincipal actor = auth.require(request);
    return response(workflows.preparationStart(
        patientId, treatmentId, cycleNumber, applicationDay, toBasic(body), actor.userId(), actor.displayName()));
  }

  @PostMapping(ROOT + "/preparation/complete")
  @Operation(
      summary = "Registrar mezcla, lotes, etiqueta y TTL",
      description = "Cierra la preparación registrando lote, vencimiento, volumen final y "
          + "TTL de cada componente mezclado, con el verificador que la controló. Exige "
          + "una traza por cada componente prescripto, incluidas drogas repetidas.")
  Map<String, Object> preparationComplete(
      @Parameter(description = "Id interno del paciente")
      @PathVariable long patientId, @Parameter(description = "Id del tratamiento")
      @PathVariable String treatmentId,
      @Parameter(description = "Número de ciclo del tratamiento")
      @PathVariable int cycleNumber, @Parameter(description = "Día de aplicación dentro del ciclo")
      @PathVariable int applicationDay,
      @RequestBody PreparationComplete body, HttpServletRequest request) {
    auth.requirePermission(request, "application.preparation.manage");
    SessionPrincipal actor = auth.require(request);
    List<PreparationInput> preparations = body.preparations() == null ? null
        : body.preparations().stream().map(this::toInput).toList();
    return response(workflows.preparationComplete(
        patientId, treatmentId, cycleNumber, applicationDay,
        new PreparationCompleteCommand(
            body.expectedRevision(), body.idempotencyKey(), body.verifiedBy(), preparations, body.notes()),
        actor.userId(), actor.displayName()));
  }

  @PostMapping(ROOT + "/preparation/release")
  @Operation(
      summary = "Liberar mezcla hacia la sala",
      description = "Libera la mezcla ya preparada para que pase a la sala de "
          + "administración, respetando el TTL vigente.")
  Map<String, Object> preparationRelease(
      @Parameter(description = "Id interno del paciente")
      @PathVariable long patientId, @Parameter(description = "Id del tratamiento")
      @PathVariable String treatmentId,
      @Parameter(description = "Número de ciclo del tratamiento")
      @PathVariable int cycleNumber, @Parameter(description = "Día de aplicación dentro del ciclo")
      @PathVariable int applicationDay,
      @RequestBody Basic body, HttpServletRequest request) {
    auth.requirePermission(request, "application.preparation.manage");
    SessionPrincipal actor = auth.require(request);
    return response(workflows.preparationRelease(
        patientId, treatmentId, cycleNumber, applicationDay, toBasic(body), actor.userId(), actor.displayName()));
  }

  @PostMapping(ROOT + "/preparation/restart")
  @Operation(
      summary = "Descartar y repetir una preparación",
      description = "Descarta la preparación actual (vencimiento, error o contaminación) "
          + "y habilita una repetición, conservando el registro anterior con usuario, "
          + "fecha y motivo del descarte.")
  Map<String, Object> preparationRestart(
      @Parameter(description = "Id interno del paciente")
      @PathVariable long patientId, @Parameter(description = "Id del tratamiento")
      @PathVariable String treatmentId,
      @Parameter(description = "Número de ciclo del tratamiento")
      @PathVariable int cycleNumber, @Parameter(description = "Día de aplicación dentro del ciclo")
      @PathVariable int applicationDay,
      @RequestBody Basic body, HttpServletRequest request) {
    auth.requirePermission(request, "application.preparation.manage");
    SessionPrincipal actor = auth.require(request);
    return response(workflows.preparationRestart(
        patientId, treatmentId, cycleNumber, applicationDay, toBasic(body), actor.userId(), actor.displayName()));
  }

  @GetMapping(value = ROOT + "/preparation-label", produces = MediaType.TEXT_HTML_VALUE)
  @Operation(
      summary = "Imprimir etiqueta trazable de la mezcla",
      description = "Devuelve el HTML imprimible de la etiqueta trazable de la mezcla "
          + "preparada, con droga, lote, vencimiento y datos del preparador/verificador.")
  ResponseEntity<String> preparationLabel(
      @Parameter(description = "Id interno del paciente")
      @PathVariable long patientId, @Parameter(description = "Id del tratamiento")
      @PathVariable String treatmentId,
      @Parameter(description = "Número de ciclo del tratamiento")
      @PathVariable int cycleNumber, @Parameter(description = "Día de aplicación dentro del ciclo")
      @PathVariable int applicationDay,
      HttpServletRequest request) {
    auth.requirePermission(request, "application.preparation.manage");
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
        .body(workflows.preparationLabel(patientId, treatmentId, cycleNumber, applicationDay));
  }

  @PostMapping(ROOT + "/administration/start")
  @Operation(
      summary = "Iniciar administración con doble control",
      description = "Inicia la administración de la mezcla exigiendo verificación de "
          + "paciente y etiqueta, con doble control registrado.")
  Map<String, Object> administrationStart(
      @Parameter(description = "Id interno del paciente")
      @PathVariable long patientId, @Parameter(description = "Id del tratamiento")
      @PathVariable String treatmentId,
      @Parameter(description = "Número de ciclo del tratamiento")
      @PathVariable int cycleNumber, @Parameter(description = "Día de aplicación dentro del ciclo")
      @PathVariable int applicationDay,
      @RequestBody AdministrationStart body, HttpServletRequest request) {
    auth.requirePermission(request, "application.administration.manage");
    SessionPrincipal actor = auth.require(request);
    return response(workflows.administrationStart(
        patientId, treatmentId, cycleNumber, applicationDay,
        new AdministrationStartCommand(
            body.expectedRevision(), body.idempotencyKey(), body.patientVerified(), body.labelVerified(),
            body.doubleCheckBy(), body.startedAt(), body.notes()),
        actor.userId(), actor.displayName()));
  }

  @PostMapping(ROOT + "/administration/interrupt")
  @Operation(
      summary = "Interrumpir una administración en curso",
      description = "Registra la interrupción de una administración en curso: motivo, "
          + "dosis parcial administrada, medidas tomadas, condición del paciente y "
          + "destino clínico. Queda pendiente de resolución.")
  Map<String, Object> administrationInterrupt(
      @Parameter(description = "Id interno del paciente")
      @PathVariable long patientId, @Parameter(description = "Id del tratamiento")
      @PathVariable String treatmentId,
      @Parameter(description = "Número de ciclo del tratamiento")
      @PathVariable int cycleNumber, @Parameter(description = "Día de aplicación dentro del ciclo")
      @PathVariable int applicationDay,
      @RequestBody AdministrationInterrupt body, HttpServletRequest request) {
    auth.requirePermission(request, "application.administration.manage");
    SessionPrincipal actor = auth.require(request);
    return response(workflows.administrationInterrupt(
        patientId, treatmentId, cycleNumber, applicationDay,
        new AdministrationInterruptCommand(
            body.expectedRevision(), body.idempotencyKey(), body.interruptedAt(), body.reason(),
            body.actualDose(), body.measures(), body.patientCondition(), body.disposition()),
        actor.userId(), actor.displayName()));
  }

  @PostMapping(ROOT + "/administration/resolve")
  @Operation(
      summary = "Resolver una administración interrumpida",
      description = "Resuelve una administración previamente interrumpida: reanudar con "
          + "dosis restante o cerrar según la decisión clínica registrada.")
  Map<String, Object> administrationResolve(
      @Parameter(description = "Id interno del paciente")
      @PathVariable long patientId, @Parameter(description = "Id del tratamiento")
      @PathVariable String treatmentId,
      @Parameter(description = "Número de ciclo del tratamiento")
      @PathVariable int cycleNumber, @Parameter(description = "Día de aplicación dentro del ciclo")
      @PathVariable int applicationDay,
      @RequestBody AdministrationResolve body, HttpServletRequest request) {
    auth.requirePermission(request, "application.administration.manage");
    SessionPrincipal actor = auth.require(request);
    return response(workflows.administrationResolve(
        patientId, treatmentId, cycleNumber, applicationDay,
        new AdministrationResolveCommand(
            body.expectedRevision(), body.idempotencyKey(), body.resolvedAt(), body.decision(),
            body.notes(), body.actualDose(), body.patientCondition()),
        actor.userId(), actor.displayName()));
  }

  @PostMapping(ROOT + "/administration/complete")
  @Operation(
      summary = "Cerrar la aplicación con datos reales",
      description = "Cierra la administración con la dosis real aplicada y la reacción "
          + "observada (si la hubo), idempotente por `idempotencyKey` para que finalizar "
          + "dos veces no duplique el acto clínico.")
  Map<String, Object> administrationComplete(
      @Parameter(description = "Id interno del paciente")
      @PathVariable long patientId, @Parameter(description = "Id del tratamiento")
      @PathVariable String treatmentId,
      @Parameter(description = "Número de ciclo del tratamiento")
      @PathVariable int cycleNumber, @Parameter(description = "Día de aplicación dentro del ciclo")
      @PathVariable int applicationDay,
      @RequestBody AdministrationComplete body, HttpServletRequest request) {
    auth.requirePermission(request, "application.administration.manage");
    SessionPrincipal actor = auth.require(request);
    return response(workflows.administrationComplete(
        patientId, treatmentId, cycleNumber, applicationDay,
        new AdministrationCompleteCommand(
            body.expectedRevision(), body.idempotencyKey(), body.completedAt(), body.actualDose(),
            body.reactionOccurred(), body.reactionDescription(), body.observation()),
        actor.userId(), actor.displayName()));
  }

  private BasicCommand toBasic(Basic body) {
    return new BasicCommand(body.expectedRevision(), body.idempotencyKey(), body.notes());
  }

  private StockComponentInput toInput(StockComponent component) {
    return new StockComponentInput(
        component.componentKey(), component.drugId(), component.drugName(), component.requestedQuantity(),
        component.requestedQuantityText(), component.unit(), component.inventoryLotId());
  }

  private PreparationInput toInput(Preparation preparation) {
    return new PreparationInput(
        preparation.componentKey(), preparation.drugName(), preparation.lot(), preparation.expiryDate(),
        preparation.quantity(), preparation.quantityText(), preparation.unit(), preparation.diluent(),
        preparation.finalVolume(), preparation.concentration(), preparation.ttlMinutes(),
        preparation.reservationId(), preparation.inventoryLotId());
  }

  private Map<String, Object> response(CommandResult result) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("ok", true);
    response.put("workflow", result.workflow());
    response.put("idempotentReplay", result.idempotentReplay());
    if (result.evolution() != null) {
      response.put("evolution", result.evolution());
      response.put("documentRevision", result.documentRevision());
    }
    return response;
  }
}
