package ar.com.hexium.hcop.diagnosis.application.service;

import ar.com.hexium.hcop.diagnosis.application.port.in.DiagnosisUseCase;
import ar.com.hexium.hcop.diagnosis.application.port.out.PatientDiagnosisPort;
import ar.com.hexium.hcop.diagnosis.application.port.out.PatientDiagnosisPort.DiagnosisSnapshot;
import ar.com.hexium.hcop.diagnosis.domain.DiagnosisRecord;

public final class DiagnosisApplicationService implements DiagnosisUseCase {
  private final PatientDiagnosisPort patientDiagnosis;

  public DiagnosisApplicationService(PatientDiagnosisPort patientDiagnosis) {
    this.patientDiagnosis = patientDiagnosis;
  }

  @Override
  public DiagnosisListView list(long patientId) {
    DiagnosisSnapshot snapshot = patientDiagnosis.snapshot(patientId);
    return new DiagnosisListView(snapshot.records(), snapshot.revision());
  }

  @Override
  public DiagnosisLinkResult link(long patientId, String diagnosisEntryId, long expectedRevision) {
    DiagnosisSnapshot snapshot = patientDiagnosis.snapshot(patientId);
    if (expectedRevision > 0 && expectedRevision != snapshot.revision()) {
      throw new DiagnosisFailure(
          DiagnosisFailure.Type.CONFLICT,
          "La historia fue modificada en otra ventana.",
          "VERSION_CONFLICT");
    }
    String requestedId = diagnosisEntryId == null ? "" : diagnosisEntryId.trim();
    DiagnosisRecord selected = requestedId.isBlank()
        ? snapshot.records().stream().findFirst().orElse(null)
        : snapshot.records().stream()
            .filter(record -> requestedId.equals(record.id()))
            .findFirst()
            .orElse(null);
    if (selected == null) {
      throw new DiagnosisFailure(
          DiagnosisFailure.Type.UNPROCESSABLE,
          "El diagnóstico guardado no está disponible para Tratamientos.");
    }
    return new DiagnosisLinkResult(selected, snapshot.revision());
  }
}
