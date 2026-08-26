package ar.com.hexium.hcop.diagnosis.application.port.out;

import ar.com.hexium.hcop.diagnosis.domain.DiagnosisRecord;
import java.util.List;

/**
 * Lee los diagnósticos desde la historia clínica del paciente — {@code diagnosis} depende de
 * {@code patient} en el sentido permitido del orden canónico (F3.3.0), así que este puerto no
 * rompe ningún ciclo: existe para que {@code DiagnosisApplicationService} no conozca
 * {@code patient.PatientService}/{@code PatientDocumentService} ni el parseo JSON de la historia.
 * La implementación deja pasar sin traducir el {@code ApiException} 404 de
 * {@code PatientService.require}/{@code PatientDocumentService.require} — mismo criterio que
 * {@code media.infrastructure.patient.PatientServiceLookupAdapter}.
 */
public interface PatientDiagnosisPort {

  DiagnosisSnapshot snapshot(long patientId);

  record DiagnosisSnapshot(long revision, List<DiagnosisRecord> records) {
  }
}
