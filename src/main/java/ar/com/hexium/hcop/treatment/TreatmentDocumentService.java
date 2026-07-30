package ar.com.hexium.hcop.treatment;

import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.infusion.InfusionRepository;
import ar.com.hexium.hcop.infusion.InfusionRepository.Infusion;
import ar.com.hexium.hcop.media.ClinicalFileRepository;
import ar.com.hexium.hcop.media.ClinicalFileRepository.StoredFile;
import ar.com.hexium.hcop.patient.PatientService;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
public class TreatmentDocumentService {
  private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
  private static final DateTimeFormatter DATE_TIME =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ARGENTINA);
  private final TreatmentRepository treatments;
  private final PatientService patients;
  private final InfusionRepository infusions;
  private final ClinicalFileRepository files;

  public TreatmentDocumentService(
      TreatmentRepository treatments,
      PatientService patients,
      InfusionRepository infusions,
      ClinicalFileRepository files) {
    this.treatments = treatments;
    this.patients = patients;
    this.infusions = infusions;
    this.files = files;
  }

  public StoredFile stored(String treatmentId, String kind) {
    treatments.find(treatmentId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tratamiento no encontrado."));
    return files.findLatestByTreatment(treatmentId, kind)
        .orElseThrow(() -> new ApiException(
            HttpStatus.NOT_FOUND, "El documento todavía no está disponible en la base clínica local."));
  }

  public StoredFile stored(long patientId, String treatmentId, String kind) {
    treatments.find(patientId, treatmentId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tratamiento no encontrado."));
    return files.findLatestByTreatment(treatmentId, kind)
        .orElseThrow(() -> new ApiException(
            HttpStatus.NOT_FOUND, "El documento todavía no está disponible en la base clínica local."));
  }

  public String treatmentSheet(long patientId, String treatmentId, int cycle) {
    if (cycle < 1 || cycle > 500) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Ciclo inválido.");
    }
    var patient = patients.require(patientId);
    var treatment = treatments.find(patientId, treatmentId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tratamiento no encontrado."));
    JsonNode detail = treatments.detail(treatmentId);
    JsonNode cycleNode = null;
    for (JsonNode item : detail.path("cycles")) {
      if (item.path("number").asInt() == cycle) {
        cycleNode = item;
        break;
      }
    }
    if (cycleNode == null) {
      throw new ApiException(HttpStatus.NOT_FOUND, "El ciclo no pertenece al tratamiento.");
    }
    List<Infusion> appointments = infusions.list(patientId, null).stream()
        .filter(item -> treatmentId.equals(item.treatmentId()) && item.cycleNumber() == cycle)
        .toList();
    String rows = drugRows(cycleNode.path("drugs"));
    String appointmentsHtml = appointments.isEmpty()
        ? "<p>Sin turno asignado.</p>"
        : appointments.stream().map(this::appointment).reduce("", String::concat);
    return """
        <!doctype html>
        <html lang="es"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Hoja de tratamiento</title>
        <style>
        body{font:15px system-ui;color:#17232d;margin:0;background:#f3f6f8}
        main{box-sizing:border-box;width:min(980px,96%%);margin:24px auto;background:white;padding:28px;border:1px solid #cfd9e0}
        h1{font-size:24px;margin:0 0 4px}h2{font-size:17px;margin:24px 0 8px}
        .meta{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin-top:20px}
        .meta div{border:1px solid #dbe3e8;padding:10px}.meta small{display:block;color:#64727d}
        table{border-collapse:collapse;width:100%%}th,td{border:1px solid #dbe3e8;padding:9px;text-align:left}
        th{background:#edf6fb}.appointment{border-left:4px solid #238cc2;padding:8px 12px;margin:6px 0}
        button{margin-top:22px;padding:8px 18px}@media print{body{background:white}main{border:0;margin:0;width:100%%}button{display:none}}
        </style></head><body><main>
        <h1>Hoja de tratamiento · Ciclo %d</h1>
        <p>HCOP JP · documento generado desde la base clínica local</p>
        <section class="meta">
          <div><small>Paciente</small><strong>%s</strong></div>
          <div><small>DNI</small><strong>%s</strong></div>
          <div><small>Historia clínica</small><strong>%s</strong></div>
          <div><small>Diagnóstico</small><strong>%s</strong></div>
          <div><small>Esquema</small><strong>%s</strong></div>
          <div><small>Oncólogo</small><strong>%s</strong></div>
        </section>
        <h2>Drogas y preparación</h2>
        <table><thead><tr><th>Droga</th><th>Dosis</th><th>Vía</th><th>Tiempo</th></tr></thead>
        <tbody>%s</tbody></table>
        <h2>Turno y administración</h2>%s
        <button onclick="print()">Imprimir</button>
        </main></body></html>
        """.formatted(
        cycle,
        escape(patient.fullName()),
        escape(patient.dni()),
        escape(patient.medicalRecord()),
        escape(treatment.diagnosis()),
        escape(treatment.schemeName()),
        escape(treatment.oncologist()),
        rows,
        appointmentsHtml);
  }

  private String drugRows(JsonNode drugs) {
    if (!drugs.isArray() || drugs.isEmpty()) {
      return "<tr><td colspan=\"4\">El protocolo no tiene drogas detalladas.</td></tr>";
    }
    StringBuilder html = new StringBuilder();
    for (JsonNode drug : drugs) {
      String dose = text(drug, "prescribedDoseText", "dose", "dosis");
      String unit = text(drug, "doseUnit", "unidadDosis", "unidad");
      html.append("<tr><td>").append(escape(text(drug, "drugName", "name", "nombre")))
          .append("</td><td>").append(escape(
              dose + (unit.isBlank() || dose.matches(".*[A-Za-z%].*") ? "" : " " + unit)))
          .append("</td><td>").append(escape(text(drug, "route", "via")))
          .append("</td><td>").append(escape(text(drug, "administrationTime", "time")))
          .append("</td></tr>");
    }
    return html.toString();
  }

  private String appointment(Infusion infusion) {
    String when = infusion.scheduledAt() == null ? "Sin fecha" : DATE_TIME.format(infusion.scheduledAt());
    return "<div class=\"appointment\"><strong>" + escape(when) + "</strong> · Sillón " +
        escape(infusion.chair()) + "<br>Estado: " + escape(infusion.clinicalStatus()) +
        " · Farmacia: " + escape(infusion.pharmacyStatus()) +
        " · Administración: " + escape(infusion.administrationStatus()) + "</div>";
  }

  private String text(JsonNode node, String... keys) {
    for (String key : keys) {
      String value = node.path(key).asText("").trim();
      if (!value.isBlank()) return value;
    }
    return "";
  }

  static String escape(String value) {
    return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;");
  }
}
