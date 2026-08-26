package ar.com.hexium.hcop.treatment.application.service;

import ar.com.hexium.hcop.media.application.port.in.ClinicalFileUseCase;
import ar.com.hexium.hcop.media.domain.ClinicalFile;
import ar.com.hexium.hcop.treatment.application.port.in.TreatmentDocumentUseCase;
import ar.com.hexium.hcop.treatment.application.port.out.InfusionAppointmentPort;
import ar.com.hexium.hcop.treatment.application.port.out.InfusionAppointmentPort.InfusionAppointment;
import ar.com.hexium.hcop.treatment.application.port.out.TreatmentPatientPort;
import ar.com.hexium.hcop.treatment.application.port.out.TreatmentStore;
import ar.com.hexium.hcop.treatment.domain.DrugLine;
import ar.com.hexium.hcop.treatment.domain.Treatment;
import ar.com.hexium.hcop.treatment.domain.TreatmentPatientView;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class TreatmentDocumentApplicationService implements TreatmentDocumentUseCase {
  private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
  private static final DateTimeFormatter DATE_TIME =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ARGENTINA);
  private final TreatmentStore treatments;
  private final TreatmentPatientPort patients;
  private final InfusionAppointmentPort infusions;
  private final ClinicalFileUseCase files;

  public TreatmentDocumentApplicationService(
      TreatmentStore treatments, TreatmentPatientPort patients, InfusionAppointmentPort infusions,
      ClinicalFileUseCase files) {
    this.treatments = treatments;
    this.patients = patients;
    this.infusions = infusions;
    this.files = files;
  }

  @Override
  public ClinicalFile stored(String treatmentId, String kind) {
    treatments.find(treatmentId)
        .orElseThrow(() -> new TreatmentFailure(TreatmentFailure.Type.NOT_FOUND, "Tratamiento no encontrado."));
    return files.findLatestByTreatment(treatmentId, kind)
        .orElseThrow(() -> new TreatmentFailure(
            TreatmentFailure.Type.NOT_FOUND, "El documento todavía no está disponible en la base clínica local."));
  }

  @Override
  public ClinicalFile stored(long patientId, String treatmentId, String kind) {
    treatments.find(patientId, treatmentId)
        .orElseThrow(() -> new TreatmentFailure(TreatmentFailure.Type.NOT_FOUND, "Tratamiento no encontrado."));
    return files.findLatestByTreatment(treatmentId, kind)
        .orElseThrow(() -> new TreatmentFailure(
            TreatmentFailure.Type.NOT_FOUND, "El documento todavía no está disponible en la base clínica local."));
  }

  @Override
  public String treatmentSheet(long patientId, String treatmentId, int cycle) {
    if (cycle < 1 || cycle > 500) {
      throw new TreatmentFailure(TreatmentFailure.Type.INVALID, "Ciclo inválido.");
    }
    TreatmentPatientView patient = patients.requirePatient(patientId);
    Treatment treatment = treatments.find(patientId, treatmentId)
        .orElseThrow(() -> new TreatmentFailure(TreatmentFailure.Type.NOT_FOUND, "Tratamiento no encontrado."));
    List<DrugLine> drugs = treatments.cycleDrugs(treatmentId, cycle)
        .orElseThrow(() -> new TreatmentFailure(
            TreatmentFailure.Type.NOT_FOUND, "El ciclo no pertenece al tratamiento."));
    List<InfusionAppointment> appointments = infusions.forCycle(patientId, treatmentId, cycle);
    String rows = drugRows(drugs);
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

  private String drugRows(List<DrugLine> drugs) {
    if (drugs.isEmpty()) {
      return "<tr><td colspan=\"4\">El protocolo no tiene drogas detalladas.</td></tr>";
    }
    StringBuilder html = new StringBuilder();
    for (DrugLine drug : drugs) {
      String dose = drug.doseText();
      String unit = drug.doseUnit();
      html.append("<tr><td>").append(escape(drug.drugName()))
          .append("</td><td>").append(escape(
              dose + (unit.isBlank() || dose.matches(".*[A-Za-z%].*") ? "" : " " + unit)))
          .append("</td><td>").append(escape(drug.route()))
          .append("</td><td>").append(escape(drug.administrationTime()))
          .append("</td></tr>");
    }
    return html.toString();
  }

  private String appointment(InfusionAppointment appointment) {
    String when = appointment.scheduledAt() == null
        ? "Sin fecha" : DATE_TIME.format(appointment.scheduledAt());
    return "<div class=\"appointment\"><strong>" + escape(when) + "</strong> · Sillón " +
        escape(appointment.chair()) + "<br>Estado: " + escape(appointment.clinicalStatus()) +
        " · Farmacia: " + escape(appointment.pharmacyStatus()) +
        " · Administración: " + escape(appointment.administrationStatus()) + "</div>";
  }

  static String escape(String value) {
    return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;");
  }
}
