package ar.com.hexium.hcop.qr.application.port.out;

import ar.com.hexium.hcop.qr.domain.QrInfusionRef;
import java.util.Map;
import java.util.Optional;

/**
 * Cruza a {@code infusion}, dirección permitida por el orden canónico (F3.3.0).
 * {@code isDayHospitalApplication} absorbe la navegación del JSON de logística
 * ({@code Logistics.applicationDrugs()}) — {@code domain}/{@code application} de este módulo
 * nunca tocan {@code tools.jackson}.
 */
public interface QrInfusionPort {

  Optional<QrInfusionRef> findByCycle(long patientId, String treatmentId, int cycle);

  Optional<QrInfusionRef> findByApplication(
      long patientId, String treatmentId, int cycle, int applicationDay);

  /**
   * {@code empty()} cuando no existe fila de logística para ese día (mensaje distinto al de
   * "solo dosis domiciliarias" — el original los distingue, ver {@code QrApplicationService}).
   */
  Optional<Boolean> dayHospitalEligibility(long patientId, String treatmentId, int cycle, int applicationDay);

  /** La misma proyección que {@code InfusionService.view(Infusion)} — pasa a través sin cambios. */
  Optional<Map<String, Object>> view(long infusionId);
}
