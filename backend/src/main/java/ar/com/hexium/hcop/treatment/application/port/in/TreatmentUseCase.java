package ar.com.hexium.hcop.treatment.application.port.in;

import java.util.List;
import java.util.Map;

public interface TreatmentUseCase {

  List<Map<String, Object>> list(long patientId);

  Map<String, Object> options(long patientId);

  Map<String, Object> requirements(long patientId, String schemeId);

  CreationResult create(CreateTreatmentCommand command);

  Map<String, Object> detail(long patientId, String treatmentId);

  /**
   * {@code rawBody} es el {@code JsonNode} crudo del request, tipado {@code Object} para que
   * application no dependa de Jackson — se preserva íntegro en el payload guardado (deep copy),
   * igual que el original. Los demás campos ya están extraídos/validados por el controller para
   * los casos donde el parseo puede fallar de forma incondicional (números, fechas con 2
   * formatos); las validaciones que SÍ dependen de otro dato (ej. si un número es obligatorio
   * según el esquema elegido) las hace el use case.
   */
  record CreateTreatmentCommand(
      long patientId, String diagnosisId, String schemeId, String cycleCountRaw,
      String initialCycleRaw, String cycleDaysRaw, String createdOnRaw, String firstCycleDateRaw,
      String treatmentType, String intent, String oncologistRaw, String consentRaw,
      boolean consentAvailable, boolean protocolMismatchConfirmed, String protocolMismatchReason,
      boolean requirementsConfirmed, String weightRaw, String heightRaw, String creatinineRaw,
      String gfrRaw, String targetAucRaw, String calciumRaw, String albuminRaw,
      String clinicalEntryId, Object rawBody, long actorId, String actorDisplayName) {
  }

  record CreationResult(
      Map<String, Object> treatment, Object evolution, long documentRevision, String createdAt,
      boolean idempotentReplay) {
  }
}
