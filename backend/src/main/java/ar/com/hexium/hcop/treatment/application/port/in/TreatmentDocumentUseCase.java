package ar.com.hexium.hcop.treatment.application.port.in;

import ar.com.hexium.hcop.media.domain.ClinicalFile;

public interface TreatmentDocumentUseCase {

  ClinicalFile stored(String treatmentId, String kind);

  ClinicalFile stored(long patientId, String treatmentId, String kind);

  String treatmentSheet(long patientId, String treatmentId, int cycle);
}
