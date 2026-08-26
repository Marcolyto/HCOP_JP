package ar.com.hexium.hcop.qr.application.service;

import ar.com.hexium.hcop.qr.application.port.in.QrUseCase;
import ar.com.hexium.hcop.qr.application.port.out.PatientEvolutionPort;
import ar.com.hexium.hcop.qr.application.port.out.PatientEvolutionPort.AppendedEvolution;
import ar.com.hexium.hcop.qr.application.port.out.QrInfusionPort;
import ar.com.hexium.hcop.qr.application.port.out.QrPatientPort;
import ar.com.hexium.hcop.qr.application.port.out.QrScanStore;
import ar.com.hexium.hcop.qr.application.port.out.QrTreatmentPort;
import ar.com.hexium.hcop.qr.domain.EvolutionDraft;
import ar.com.hexium.hcop.qr.domain.QrInfusionRef;
import ar.com.hexium.hcop.qr.domain.QrPatientView;
import ar.com.hexium.hcop.qr.domain.QrScan;
import ar.com.hexium.hcop.qr.domain.QrTreatmentView;
import ar.com.hexium.hcop.treatment.domain.DayHospitalApplicationPolicy;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class QrApplicationService implements QrUseCase {
  private final String secret;
  private final QrPatientPort patients;
  private final QrTreatmentPort treatments;
  private final QrInfusionPort infusions;
  private final QrScanStore scans;
  private final PatientEvolutionPort evolutions;
  private final Clock clock;

  public QrApplicationService(
      String secret, QrPatientPort patients, QrTreatmentPort treatments, QrInfusionPort infusions,
      QrScanStore scans, PatientEvolutionPort evolutions, Clock clock) {
    this.secret = secret;
    this.patients = patients;
    this.treatments = treatments;
    this.infusions = infusions;
    this.scans = scans;
    this.evolutions = evolutions;
    this.clock = clock;
  }

  @Override
  public String code(long patientId, String treatmentId, int cycle, int applicationDay) {
    if (cycle < 1 || cycle > 500) invalid("Ciclo inválido.");
    if (!DayHospitalApplicationPolicy.isValidApplicationDay(applicationDay)) {
      invalid("Día de aplicación inválido.");
    }
    patients.requirePatient(patientId);
    requireTreatment(patientId, treatmentId);
    requireDayHospitalApplication(patientId, treatmentId, cycle, applicationDay);
    String treatment = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(treatmentId.getBytes(StandardCharsets.UTF_8));
    String payload = "HCOPJP|2|" + patientId + "|" + treatment + "|" + cycle + "|" + applicationDay;
    return payload + "|" + signature(payload);
  }

  @Override
  public String printableHtml(long patientId, String treatmentId, int cycle, int applicationDay) {
    String code = code(patientId, treatmentId, cycle, applicationDay);
    QrPatientView patient = patients.requirePatient(patientId);
    QrTreatmentView treatment = requireTreatment(patientId, treatmentId);
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

  @Override
  public ScanResult scan(ScanCommand command) {
    String code = orEmpty(command.rawCode());
    String operation = orEmpty(command.operationId());
    if (operation.isBlank() || operation.length() > 128) invalid("Identificador de operación inválido.");
    String hash = sha256(code);
    QrScan previous = scans.findOperation(operation).orElse(null);
    if (previous != null) {
      if (!hashesMatch(previous.codeHash(), hash)) conflict("La operación ya fue usada para otro QR.");
      return resolved(previous.patientId(), previous.treatmentId(), previous.infusionId(), true, null, null);
    }
    Parsed parsed = parse(code);
    QrInfusionRef infusion = parsed.version() == 1
        ? infusions.findByCycle(parsed.patientId(), parsed.treatmentId(), parsed.cycle())
            .orElseThrow(() -> conflictException(
                "El tratamiento y ciclo son válidos, pero todavía no tienen un turno activo."))
        : infusions.findByApplication(
                parsed.patientId(), parsed.treatmentId(), parsed.cycle(), parsed.applicationDay())
            .orElseThrow(() -> conflictException(
                "La aplicación identificada todavía no tiene un turno activo."));
    requireDayHospitalApplication(
        parsed.patientId(), parsed.treatmentId(), infusion.cycleNumber(), infusion.applicationDay());
    boolean inserted = scans.insertIfAbsent(
        operation, hash, parsed.patientId(), parsed.treatmentId(), parsed.cycle(),
        parsed.applicationDay(), infusion.id(), command.actorId(), clock.instant());
    if (!inserted) {
      QrScan concurrent = scans.findOperation(operation).orElseThrow();
      if (!hashesMatch(concurrent.codeHash(), hash)) conflict("La operación ya fue usada para otro QR.");
      return resolved(concurrent.patientId(), concurrent.treatmentId(), concurrent.infusionId(), true, null, null);
    }
    String text = "Se escaneó el QR del tratamiento antes de la administración.\nEsquema: " +
        infusion.scheme() + "\nCiclo: " + infusion.cycleNumber() +
        "\nDía de aplicación: " + infusion.applicationDay() +
        "\nTurno: " + (infusion.scheduledAt() == null ? "sin fecha" : infusion.scheduledAt());
    EvolutionDraft draft = new EvolutionDraft(
        "qr-scan-" + operation, "Identificación por QR", text, "Hospital de día", false,
        Map.of("kind", "qr-scan", "operationId", operation, "infusionId", Long.toString(infusion.id())));
    AppendedEvolution appended = evolutions.append(
        parsed.patientId(), draft, command.actorId(), command.actorDisplayName());
    return resolved(
        parsed.patientId(), parsed.treatmentId(), infusion.id(), false,
        appended.evolution(), appended.revision());
  }

  private ScanResult resolved(
      long patientId, String treatmentId, long infusionId, boolean idempotent, Object evolution,
      Long revision) {
    QrPatientView patient = patients.requirePatient(patientId);
    QrTreatmentView treatment = requireTreatment(patientId, treatmentId);
    Map<String, Object> infusionView = infusions.view(infusionId)
        .orElseThrow(() -> new QrFailure(QrFailure.Type.NOT_FOUND, "Aplicación no encontrada."));
    return new ScanResult(patient, treatment, infusionView, idempotent, evolution, revision);
  }

  private QrTreatmentView requireTreatment(long patientId, String treatmentId) {
    return treatments.findTreatment(patientId, treatmentId)
        .orElseThrow(() -> new QrFailure(QrFailure.Type.NOT_FOUND, "Tratamiento no encontrado."));
  }

  private void requireDayHospitalApplication(
      long patientId, String treatmentId, int cycle, int applicationDay) {
    Boolean eligible = infusions.dayHospitalEligibility(patientId, treatmentId, cycle, applicationDay)
        .orElseThrow(() -> conflictException(
            "El dia seleccionado no corresponde a una aplicacion de Hospital de Dia."));
    if (!eligible) {
      conflict("El dia seleccionado contiene solo dosis domiciliarias y no admite QR de Hospital de Dia.");
    }
  }

  private Parsed parse(String code) {
    String[] parts = code.split("\\|", -1);
    boolean legacy = parts.length == 6 && "HCOPJP".equals(parts[0]) && "1".equals(parts[1]);
    boolean current = parts.length == 7 && "HCOPJP".equals(parts[0]) && "2".equals(parts[1]);
    if (!legacy && !current) invalid("El QR no pertenece a HCOP JP.");
    int signatureIndex = parts.length - 1;
    String payload = String.join("|", Arrays.copyOf(parts, signatureIndex));
    if (!hashesMatch(signature(payload), parts[signatureIndex])) invalid("La firma del QR no es válida.");
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
      throw new QrFailure(QrFailure.Type.INVALID, "El contenido del QR es inválido.");
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

  private boolean hashesMatch(String a, String b) {
    return MessageDigest.isEqual(
        a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }

  private static String escape(String value) {
    return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;");
  }

  private String orEmpty(String value) {
    return value == null ? "" : value.trim();
  }

  private void invalid(String message) {
    throw new QrFailure(QrFailure.Type.INVALID, message);
  }

  private void conflict(String message) {
    throw conflictException(message);
  }

  private QrFailure conflictException(String message) {
    return new QrFailure(QrFailure.Type.CONFLICT, message);
  }

  private record Parsed(
      long patientId, String treatmentId, int cycle, int applicationDay, int version) {
  }
}
