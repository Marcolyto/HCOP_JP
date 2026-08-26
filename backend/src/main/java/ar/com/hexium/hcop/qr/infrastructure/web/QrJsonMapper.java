package ar.com.hexium.hcop.qr.infrastructure.web;

import ar.com.hexium.hcop.qr.application.port.in.QrUseCase.ScanResult;
import ar.com.hexium.hcop.qr.domain.QrPatientView;
import ar.com.hexium.hcop.qr.domain.QrTreatmentView;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class QrJsonMapper {

  public Map<String, Object> scanResult(ScanResult result) {
    Map<String, Object> patientView = new LinkedHashMap<>();
    QrPatientView patient = result.patient();
    patientView.put("id", Long.toString(patient.id()));
    patientView.put("fullName", patient.fullName());
    patientView.put("dni", patient.dni());
    QrTreatmentView treatment = result.treatment();
    Map<String, Object> treatmentView = Map.of(
        "id", treatment.id(), "scheme", treatment.schemeName(), "diagnosis", treatment.diagnosis());
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("ok", true);
    response.put("patient", patientView);
    response.put("treatment", treatmentView);
    response.put("infusion", result.infusion());
    response.put("scan", Map.of("idempotent", result.idempotent()));
    response.put("idempotent", result.idempotent());
    response.put("evolution", result.evolution());
    response.put("documentRevision", result.documentRevision());
    return response;
  }
}
