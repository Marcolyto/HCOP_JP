package ar.com.hexium.hcop.qr;

import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.config.HcopProperties;
import ar.com.hexium.hcop.infusion.InfusionRepository;
import ar.com.hexium.hcop.infusion.InfusionRepository.Infusion;
import ar.com.hexium.hcop.infusion.InfusionRepository.Logistics;
import ar.com.hexium.hcop.infusion.InfusionService;
import ar.com.hexium.hcop.patient.PatientDocumentService;
import ar.com.hexium.hcop.patient.PatientDocumentService.EvolutionAppend;
import ar.com.hexium.hcop.patient.PatientService;
import ar.com.hexium.hcop.treatment.DayHospitalApplicationPolicy;
import ar.com.hexium.hcop.treatment.TreatmentRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class QrWorkflowService {
  private final String secret;
  private final InfusionRepository infusionRepository;
  private final InfusionService infusions;
  private final TreatmentRepository treatments;
  private final PatientService patients;
  private final PatientDocumentService documents;
  private final QrWorkflowRepository scans;
  private final ObjectMapper mapper;
  private final Clock clock;

  public QrWorkflowService(
      HcopProperties properties,
      InfusionRepository infusionRepository,
      InfusionService infusions,
      TreatmentRepository treatments,
      PatientService patients,
      PatientDocumentService documents,
      QrWorkflowRepository scans,
      ObjectMapper mapper,
      Clock clock) {
    this.secret = properties.qrSecret();
    this.infusionRepository = infusionRepository;
    this.infusions = infusions;
    this.treatments = treatments;
    this.patients = patients;
    this.documents = documents;
    this.scans = scans;
    this.mapper = mapper;
    this.clock = clock;
  }

  public String code(long patientId, String treatmentId, int cycle, int applicationDay) {
    if (cycle < 1 || cycle > 500) throw new ApiException(HttpStatus.BAD_REQUEST, "Ciclo inválido.");
    if (!DayHospitalApplicationPolicy.isValidApplicationDay(applicationDay)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Día de aplicación inválido.");
    }
    patients.require(patientId);
    treatments.find(patientId, treatmentId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tratamiento no encontrado."));
    requireDayHospitalApplication(patientId, treatmentId, cycle, applicationDay);
    String treatment = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(treatmentId.getBytes(StandardCharsets.UTF_8));
    String payload = "HCOPJP|2|" + patientId + "|" + treatment + "|" + cycle + "|" + applicationDay;
    return payload + "|" + signature(payload);
  }

  public String printableHtml(
      long patientId, String treatmentId, int cycle, int applicationDay) {
    String code = code(patientId, treatmentId, cycle, applicationDay);
    var patient = patients.require(patientId);
    var treatment = treatments.find(patientId, treatmentId).orElseThrow();
    String svg = svg(code, 7);
    return """
        <!doctype html><html lang="es"><head><meta charset="utf-8">
        <title>Identificación de tratamiento</title>
        <style>body{font:16px system-ui;margin:0;display:grid;place-items:center;min-height:100vh;color:#16202a}
        main{width:92%%;max-width:620px;text-align:center;border:1px solid #cad4dc;padding:28px}
        svg{width:min(72vw,360px);height:auto}h1{font-size:22px;margin:0 0 8px}p{margin:4px}
        .code{font:11px ui-monospace;overflow-wrap:anywhere;color:#66727c}@media print{button{display:none}}
        button{margin-top:18px;padding:9px 18px}</style></head><body><main>
        <h1>Hospital de día · Identificación</h1>
        <p><strong>%s</strong> · DNI %s</p>
        <p>%s · Ciclo %d · Día %d</p>
        %s
        <p class="code">%s</p>
        <button onclick="print()">Imprimir</button>
        </main></body></html>
        """.formatted(
        escape(patient.fullName()), escape(patient.dni()), escape(treatment.schemeName()),
        cycle, applicationDay, svg, escape(code));
  }

  @Transactional
  public Map<String, Object> scan(String rawCode, String operationId, SessionPrincipal actor) {
    String code = rawCode == null ? "" : rawCode.trim();
    String operation = operationId == null ? "" : operationId.trim();
    if (operation.isBlank() || operation.length() > 128) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Identificador de operación inválido.");
    }
    String hash = sha256(code);
    var previous = scans.findOperation(operation).orElse(null);
    if (previous != null) {
      if (!MessageDigest.isEqual(
          previous.codeHash().getBytes(StandardCharsets.UTF_8),
          hash.getBytes(StandardCharsets.UTF_8))) {
        throw new ApiException(HttpStatus.CONFLICT, "La operación ya fue usada para otro QR.");
      }
      return resolved(previous.patientId(), previous.treatmentId(), previous.cycleNumber(),
          previous.infusionId(), true, null, null);
    }
    Parsed parsed = parse(code);
    Infusion infusion = parsed.version() == 1
        ? infusionRepository.findByCycle(parsed.patientId(), parsed.treatmentId(), parsed.cycle())
            .orElseThrow(() -> new ApiException(
                HttpStatus.CONFLICT,
                "El tratamiento y ciclo son válidos, pero todavía no tienen un turno activo."))
        : infusionRepository.findByApplication(
            parsed.patientId(), parsed.treatmentId(), parsed.cycle(), parsed.applicationDay())
        .orElseThrow(() -> new ApiException(
            HttpStatus.CONFLICT,
            "La aplicación identificada todavía no tiene un turno activo."));
    requireDayHospitalApplication(
        parsed.patientId(), parsed.treatmentId(), infusion.cycleNumber(), infusion.applicationDay());
    boolean inserted = scans.insertIfAbsent(
        operation, hash, parsed.patientId(), parsed.treatmentId(), parsed.cycle(),
        parsed.applicationDay(), infusion.id(), actor.userId(), clock.instant());
    if (!inserted) {
      var concurrent = scans.findOperation(operation).orElseThrow();
      if (!MessageDigest.isEqual(
          concurrent.codeHash().getBytes(StandardCharsets.UTF_8),
          hash.getBytes(StandardCharsets.UTF_8))) {
        throw new ApiException(HttpStatus.CONFLICT, "La operación ya fue usada para otro QR.");
      }
      return resolved(
          concurrent.patientId(), concurrent.treatmentId(), concurrent.cycleNumber(),
          concurrent.infusionId(), true, null, null);
    }
    ObjectNode evolution = mapper.createObjectNode();
    evolution.put("id", "qr-scan-" + operation);
    evolution.put("date", LocalDate.now(clock).toString());
    evolution.put("datePrecision", "day");
    evolution.put("author", actor.displayName());
    evolution.put("reason", "Identificación por QR");
    evolution.put("specialty", "Hospital de día");
    evolution.put("text", "Se escaneó el QR del tratamiento antes de la administración.\nEsquema: " +
        infusion.scheme() + "\nCiclo: " + infusion.cycleNumber() +
        "\nDía de aplicación: " + infusion.applicationDay() +
        "\nTurno: " + (infusion.scheduledAt() == null ? "sin fecha" : infusion.scheduledAt()));
    evolution.put("highlighted", false);
    evolution.put("createdAt", clock.instant().toString());
    evolution.put("updatedAt", clock.instant().toString());
    evolution.set("attachments", mapper.createArrayNode());
    evolution.set("linkedStudyIds", mapper.createArrayNode());
    evolution.putObject("sourceRef")
        .put("kind", "qr-scan")
        .put("operationId", operation)
        .put("infusionId", Long.toString(infusion.id()));
    EvolutionAppend append = documents.appendImmutableEvolution(
        parsed.patientId(), evolution, actor.userId());
    return resolved(parsed.patientId(), parsed.treatmentId(), parsed.cycle(), infusion.id(),
        false, append.evolution(), append.revision());
  }

  private Map<String, Object> resolved(
      long patientId, String treatmentId, int cycle, long infusionId,
      boolean idempotent, ObjectNode evolution, Long revision) {
    var patient = patients.require(patientId);
    var treatment = treatments.find(patientId, treatmentId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tratamiento no encontrado."));
    Infusion infusion = infusionRepository.find(infusionId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Aplicación no encontrada."));
    Map<String, Object> patientView = new LinkedHashMap<>();
    patientView.put("id", Long.toString(patient.id()));
    patientView.put("fullName", patient.fullName());
    patientView.put("dni", patient.dni());
    Map<String, Object> treatmentView = Map.of(
        "id", treatment.id(), "scheme", treatment.schemeName(), "diagnosis", treatment.diagnosis());
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("ok", true);
    result.put("patient", patientView);
    result.put("treatment", treatmentView);
    result.put("infusion", infusions.view(infusion));
    result.put("scan", Map.of("idempotent", idempotent));
    result.put("idempotent", idempotent);
    result.put("evolution", evolution);
    result.put("documentRevision", revision);
    return result;
  }

  private Parsed parse(String code) {
    String[] parts = code.split("\\|", -1);
    boolean legacy = parts.length == 6 && "HCOPJP".equals(parts[0]) && "1".equals(parts[1]);
    boolean current = parts.length == 7 && "HCOPJP".equals(parts[0]) && "2".equals(parts[1]);
    if (!legacy && !current) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "El QR no pertenece a HCOP JP.");
    }
    int signatureIndex = parts.length - 1;
    String payload = String.join("|", java.util.Arrays.copyOf(parts, signatureIndex));
    if (!MessageDigest.isEqual(
        signature(payload).getBytes(StandardCharsets.UTF_8),
        parts[signatureIndex].getBytes(StandardCharsets.UTF_8))) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "La firma del QR no es válida.");
    }
    try {
      long patientId = Long.parseLong(parts[2]);
      String treatmentId = new String(Base64.getUrlDecoder().decode(parts[3]), StandardCharsets.UTF_8);
      int cycle = Integer.parseInt(parts[4]);
      int applicationDay = current ? Integer.parseInt(parts[5]) : 1;
      if (patientId < 1 || treatmentId.isBlank() || cycle < 1 || cycle > 500
          || !DayHospitalApplicationPolicy.isValidApplicationDay(applicationDay)) {
        throw new IllegalArgumentException();
      }
      return new Parsed(patientId, treatmentId, cycle, applicationDay, current ? 2 : 1);
    } catch (IllegalArgumentException invalid) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "El contenido del QR es inválido.");
    }
  }

  private String signature(String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getUrlEncoder().withoutPadding()
          .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("No se pudo firmar el QR.", exception);
    }
  }

  private Logistics requireDayHospitalApplication(
      long patientId, String treatmentId, int cycle, int applicationDay) {
    Logistics logistics = infusionRepository.logistics(
            patientId, treatmentId, cycle, applicationDay)
        .orElseThrow(() -> new ApiException(
            HttpStatus.CONFLICT,
            "El dia seleccionado no corresponde a una aplicacion de Hospital de Dia."));
    JsonNode components = logistics.applicationDrugs();
    if (!components.isArray() || components.isEmpty()
        || components.valueStream().noneMatch(
            DayHospitalApplicationPolicy::requiresDayHospital)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "El dia seleccionado contiene solo dosis domiciliarias y no admite QR de Hospital de Dia.");
    }
    return logistics;
  }

  private String svg(String value, int scale) {
    try {
      BitMatrix matrix = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 45, 45);
      StringBuilder path = new StringBuilder();
      for (int y = 0; y < matrix.getHeight(); y++) {
        for (int x = 0; x < matrix.getWidth(); x++) {
          if (matrix.get(x, y)) path.append("M").append(x).append(" ").append(y).append("h1v1h-1z");
        }
      }
      return "<svg role=\"img\" aria-label=\"Código QR\" viewBox=\"0 0 " +
          matrix.getWidth() + " " + matrix.getHeight() +
          "\" shape-rendering=\"crispEdges\"><rect width=\"100%\" height=\"100%\" fill=\"white\"/>" +
          "<path d=\"" + path + "\" fill=\"black\"/></svg>";
    } catch (WriterException exception) {
      throw new IllegalStateException("No se pudo generar el QR.", exception);
    }
  }

  private String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static String escape(String value) {
    return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;");
  }

  private record Parsed(
      long patientId, String treatmentId, int cycle, int applicationDay, int version) {
  }
}
