package ar.com.hexium.hcop.qr.application.port.in;

import ar.com.hexium.hcop.qr.domain.QrPatientView;
import ar.com.hexium.hcop.qr.domain.QrTreatmentView;
import java.util.Map;

public interface QrUseCase {

  String code(long patientId, String treatmentId, int cycle, int applicationDay);

  String printableHtml(long patientId, String treatmentId, int cycle, int applicationDay);

  ScanResult scan(ScanCommand command);

  record ScanCommand(String rawCode, String operationId, long actorId, String actorDisplayName) {
  }

  record ScanResult(
      QrPatientView patient, QrTreatmentView treatment, Map<String, Object> infusion,
      boolean idempotent, Object evolution, Long documentRevision) {
  }
}
