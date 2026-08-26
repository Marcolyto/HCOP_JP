package ar.com.hexium.hcop.diagnosis.application.port.in;

import ar.com.hexium.hcop.diagnosis.domain.DiagnosisRecord;
import java.util.List;

public interface DiagnosisUseCase {

  DiagnosisListView list(long patientId);

  DiagnosisLinkResult link(long patientId, String diagnosisEntryId, long expectedRevision);

  record DiagnosisListView(List<DiagnosisRecord> diagnoses, long revision) {
  }

  record DiagnosisLinkResult(DiagnosisRecord diagnosis, long revision) {
  }
}
