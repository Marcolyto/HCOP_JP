package ar.com.hexium.hcop.treatment.application.port.out;

import ar.com.hexium.hcop.catalog.domain.TreatmentScheme;
import ar.com.hexium.hcop.treatment.domain.DrugLine;
import ar.com.hexium.hcop.treatment.domain.Treatment;
import ar.com.hexium.hcop.treatment.domain.WorkflowState;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persistencia + armado del JSON de tratamiento (payload/detail). El armado del árbol JSON
 * (drogas por ciclo desde el esquema, evolución inmutable, enriquecimiento del detalle vía
 * timeline) queda en el adapter porque son literalmente los documentos que se guardan — separarlo
 * de la persistencia multiplicaría la superficie sin beneficio real (mismo criterio que
 * {@code media.infrastructure.persistence.FilesystemClinicalFileBlobStore} validando bytes no
 * confiables en el borde de guardado, no en application).
 */
public interface TreatmentStore {

  List<Treatment> list(long patientId);

  Map<String, WorkflowState> workflowStates(long patientId);

  Optional<Treatment> find(long patientId, String treatmentId);

  Optional<Treatment> find(String treatmentId);

  Map<String, Object> view(Treatment treatment, WorkflowState workflow, Integer resolvedDurationMinutes);

  TreatmentCreationOutcome insert(NewTreatmentDraft draft);

  Object enrichedDetail(String treatmentId, TreatmentScheme schemeOrNull, List<Map<String, Object>> sessions);

  Optional<List<DrugLine>> cycleDrugs(String treatmentId, int cycle);

  record NewTreatmentDraft(
      String id, long patientId, String diagnosisId, String diagnosis, String schemeId,
      TreatmentScheme scheme, int initialCycle, int cycleCount, int cycleDays, String treatmentType,
      String intent, String oncologist, String consentStatus, boolean consentAvailable,
      LocalDate createdOn, LocalDate firstCycleDate, String protocolDiagnosisGroup,
      String protocolGroup, boolean protocolMismatchConfirmed, String protocolMismatchReason,
      double weightKg, double heightCm, double bodySurface, double gfr, double targetAuc,
      String clinicalEntryId, Object rawBody, long actorId, String actorDisplayName) {
  }

  record TreatmentCreationOutcome(
      Map<String, Object> treatmentView, Object evolution, long documentRevision,
      String createdAt, boolean idempotentReplay) {
  }
}
