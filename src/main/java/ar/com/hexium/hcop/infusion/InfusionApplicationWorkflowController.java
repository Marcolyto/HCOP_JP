package ar.com.hexium.hcop.infusion;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowCommands.AdministrationComplete;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowCommands.AdministrationInterrupt;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowCommands.AdministrationResolve;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowCommands.AdministrationStart;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowCommands.Basic;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowCommands.ClinicalAuthorization;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowCommands.PharmacyValidation;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowCommands.PreparationComplete;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowCommands.StockReservation;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowService.CommandResult;
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

  private final ApplicationWorkflowService workflows;
  private final AuthContext auth;

  public InfusionApplicationWorkflowController(
      ApplicationWorkflowService workflows, AuthContext auth) {
    this.workflows = workflows;
    this.auth = auth;
  }

  @GetMapping("/api/clinical/application-workflows")
  @Operation(
      summary = "Listar una cola operativa por aplicación",
      description = """
          Devuelve una fila por ciclo y día real con medicación. `pharmacy` permite filtrar
          `patient_to_bring`; `triage`, `preparation` y `administration` usan por defecto la
          fecha de hoy y se ordenan por hora del turno y sillón.
          """)
  Map<String, Object> list(
      @RequestParam(defaultValue = "pharmacy")
      @Parameter(description = "pharmacy, triage, preparation o administration.")
      String queue,
      @RequestParam(required = false)
      @Parameter(description = "Fecha ISO. En triaje/preparación/administración omitirla equivale a hoy.")
      LocalDate date,
      @RequestParam(defaultValue = "")
      @Parameter(description = "Busca por paciente, DNI, esquema, diagnóstico o droga.")
      String q,
      @RequestParam(defaultValue = "")
      @Parameter(description = "Fuente/custodia; use patient_to_bring para quienes deben traer medicación.")
      String medicationSource,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.day-hospital.view");
    List<Map<String, Object>> items = workflows.list(queue, date, q, medicationSource);
    return Map.of("ok", true, "queue", queue, "items", items, "total", items.size());
  }

  @GetMapping(ROOT)
  @Operation(
      summary = "Abrir el circuito completo de una aplicación",
      description = "Incluye identidad, turno, drogas, duración, estados, trazas y revisión optimista.")
  Map<String, Object> get(
      @PathVariable long patientId,
      @PathVariable String treatmentId,
      @PathVariable int cycleNumber,
      @PathVariable int applicationDay,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.day-hospital.view");
    return Map.of(
        "ok", true,
        "workflow", workflows.get(patientId, treatmentId, cycleNumber, applicationDay));
  }

  @PostMapping(ROOT + "/pharmacy-validation")
  @Operation(
      summary = "Validar la orden en Farmacia",
      description = """
          Aprueba o rechaza dosis, vía, intervalo y premedicación y fija la procedencia/custodia.
          No crea disponibilidad ficticia ni reserva stock.
          """)
  Map<String, Object> pharmacyValidation(
      @PathVariable long patientId,
      @PathVariable String treatmentId,
      @PathVariable int cycleNumber,
      @PathVariable int applicationDay,
      @RequestBody PharmacyValidation body,
      HttpServletRequest request) {
    auth.requirePermission(request, "application.pharmacy.manage");
    return response(workflows.pharmacyValidation(
        patientId, treatmentId, cycleNumber, applicationDay,
        body, auth.require(request)));
  }

  @PostMapping(ROOT + "/stock-reservation")
  @Operation(
      summary = "Reservar o liberar stock por componente",
      description = """
          La reserva blanda sólo admite `center_stock`. Puede respaldarse con un lote cuantificado
          del inventario (evita sobre-reserva de forma atómica) o mediante verificación manual
          explícita y documentada cuando aún no existe inventario electrónico.
          """)
  Map<String, Object> stockReservation(
      @PathVariable long patientId,
      @PathVariable String treatmentId,
      @PathVariable int cycleNumber,
      @PathVariable int applicationDay,
      @RequestBody StockReservation body,
      HttpServletRequest request) {
    auth.requirePermission(request, "application.pharmacy.manage");
    return response(workflows.stockReservation(
        patientId, treatmentId, cycleNumber, applicationDay,
        body, auth.require(request)));
  }

  @PostMapping(ROOT + "/clinical-authorization")
  @Operation(
      summary = "Registrar triaje y emitir PASS o FAIL",
      description = """
          PASS habilita preparación. FAIL exige causa, libera una reserva blanda, retira el turno
          activo y mantiene la aplicación disponible para reprogramación.
          """)
  Map<String, Object> clinicalAuthorization(
      @PathVariable long patientId,
      @PathVariable String treatmentId,
      @PathVariable int cycleNumber,
      @PathVariable int applicationDay,
      @RequestBody ClinicalAuthorization body,
      HttpServletRequest request) {
    auth.requirePermission(request, "application.triage.manage");
    return response(workflows.clinicalAuthorization(
        patientId, treatmentId, cycleNumber, applicationDay,
        body, auth.require(request)));
  }

  @PostMapping(ROOT + "/preparation/start")
  @Operation(
      summary = "Iniciar preparación estéril",
      description = "Exige PASS clínico y medicación asegurada para esa aplicación.")
  Map<String, Object> preparationStart(
      @PathVariable long patientId,
      @PathVariable String treatmentId,
      @PathVariable int cycleNumber,
      @PathVariable int applicationDay,
      @RequestBody Basic body,
      HttpServletRequest request) {
    auth.requirePermission(request, "application.preparation.manage");
    return response(workflows.preparationStart(
        patientId, treatmentId, cycleNumber, applicationDay,
        body, auth.require(request)));
  }

  @PostMapping(ROOT + "/preparation/complete")
  @Operation(
      summary = "Registrar mezcla, lotes, etiqueta y TTL",
      description = """
          Guarda trazabilidad por droga. Para stock del centro debe vincular todas las reservas
          activas y consume las cantidades correspondientes sin perder el historial.
          """)
  Map<String, Object> preparationComplete(
      @PathVariable long patientId,
      @PathVariable String treatmentId,
      @PathVariable int cycleNumber,
      @PathVariable int applicationDay,
      @RequestBody PreparationComplete body,
      HttpServletRequest request) {
    auth.requirePermission(request, "application.preparation.manage");
    return response(workflows.preparationComplete(
        patientId, treatmentId, cycleNumber, applicationDay,
        body, auth.require(request)));
  }

  @PostMapping(ROOT + "/preparation/release")
  @Operation(
      summary = "Liberar mezcla hacia la sala",
      description = "Rechaza automáticamente preparaciones cuyo TTL ya venció.")
  Map<String, Object> preparationRelease(
      @PathVariable long patientId,
      @PathVariable String treatmentId,
      @PathVariable int cycleNumber,
      @PathVariable int applicationDay,
      @RequestBody Basic body,
      HttpServletRequest request) {
    auth.requirePermission(request, "application.preparation.manage");
    return response(workflows.preparationRelease(
        patientId, treatmentId, cycleNumber, applicationDay,
        body, auth.require(request)));
  }

  @PostMapping(ROOT + "/preparation/restart")
  @Operation(
      summary = "Descartar y repetir una preparación",
      description = """
          Permite descartar una mezcla preparada o liberada por vencimiento, error, rotura o
          contaminación, siempre con un motivo documentado y antes de iniciar la administración.
          Conserva los lotes anteriores como descartados y devuelve la aplicación a Farmacia para
          obtener o reservar nuevamente la medicación y realizar un nuevo control clínico.
          """)
  Map<String, Object> preparationRestart(
      @PathVariable long patientId,
      @PathVariable String treatmentId,
      @PathVariable int cycleNumber,
      @PathVariable int applicationDay,
      @RequestBody Basic body,
      HttpServletRequest request) {
    auth.requirePermission(request, "application.preparation.manage");
    return response(workflows.preparationRestart(
        patientId, treatmentId, cycleNumber, applicationDay,
        body, auth.require(request)));
  }

  @GetMapping(value = ROOT + "/preparation-label", produces = MediaType.TEXT_HTML_VALUE)
  @Operation(
      summary = "Imprimir etiqueta trazable de la mezcla",
      description = """
          Incluye dos identificadores del paciente, esquema/ciclo/día, droga y dosis,
          lote, vencimiento, diluyente, volumen, concentración, preparador, verificador
          declarado, TTL y enlace al QR de identificación.
          """)
  ResponseEntity<String> preparationLabel(
      @PathVariable long patientId,
      @PathVariable String treatmentId,
      @PathVariable int cycleNumber,
      @PathVariable int applicationDay,
      HttpServletRequest request) {
    auth.requirePermission(request, "application.preparation.manage");
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
        .body(workflows.preparationLabel(
            patientId, treatmentId, cycleNumber, applicationDay));
  }

  @PostMapping(ROOT + "/administration/start")
  @Operation(
      summary = "Iniciar administración con doble control",
      description = """
          Exige PASS, preparación liberada, paciente y etiqueta confirmados, y un segundo
          profesional habilitado distinto del usuario activo.
          """)
  Map<String, Object> administrationStart(
      @PathVariable long patientId,
      @PathVariable String treatmentId,
      @PathVariable int cycleNumber,
      @PathVariable int applicationDay,
      @RequestBody AdministrationStart body,
      HttpServletRequest request) {
    auth.requirePermission(request, "application.administration.manage");
    return response(workflows.administrationStart(
        patientId, treatmentId, cycleNumber, applicationDay,
        body, auth.require(request)));
  }

  @PostMapping(ROOT + "/administration/interrupt")
  @Operation(
      summary = "Interrumpir una administración en curso",
      description = """
          Detiene inmediatamente la aplicación y registra hora, dosis parcial, motivo,
          medidas adoptadas, condición del paciente y destino clínico. La interrupción
          queda pendiente de una resolución explícita y genera una evolución inmutable.
          """)
  Map<String, Object> administrationInterrupt(
      @PathVariable long patientId,
      @PathVariable String treatmentId,
      @PathVariable int cycleNumber,
      @PathVariable int applicationDay,
      @RequestBody AdministrationInterrupt body,
      HttpServletRequest request) {
    auth.requirePermission(request, "application.administration.manage");
    return response(workflows.administrationInterrupt(
        patientId, treatmentId, cycleNumber, applicationDay,
        body, auth.require(request)));
  }

  @PostMapping(ROOT + "/administration/resolve")
  @Operation(
      summary = "Resolver una administración interrumpida",
      description = """
          Permite reanudar bajo una decisión documentada o cerrar la aplicación sin
          completarla. Ambas decisiones preservan la trazabilidad y generan una evolución.
          """)
  Map<String, Object> administrationResolve(
      @PathVariable long patientId,
      @PathVariable String treatmentId,
      @PathVariable int cycleNumber,
      @PathVariable int applicationDay,
      @RequestBody AdministrationResolve body,
      HttpServletRequest request) {
    auth.requirePermission(request, "application.administration.manage");
    return response(workflows.administrationResolve(
        patientId, treatmentId, cycleNumber, applicationDay,
        body, auth.require(request)));
  }

  @PostMapping(ROOT + "/administration/complete")
  @Operation(
      summary = "Cerrar la aplicación con datos reales",
      description = """
          Registra hora final, dosis efectivamente administrada, reacción y observación.
          La aplicación completada queda inmutable y sale de las colas operativas.
          """)
  Map<String, Object> administrationComplete(
      @PathVariable long patientId,
      @PathVariable String treatmentId,
      @PathVariable int cycleNumber,
      @PathVariable int applicationDay,
      @RequestBody AdministrationComplete body,
      HttpServletRequest request) {
    auth.requirePermission(request, "application.administration.manage");
    return response(workflows.administrationComplete(
        patientId, treatmentId, cycleNumber, applicationDay,
        body, auth.require(request)));
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
