package ar.com.hexium.hcop.infusion.infrastructure.treatment;

import ar.com.hexium.hcop.infusion.application.port.in.InfusionUseCase;
import ar.com.hexium.hcop.infusion.application.port.out.InfusionStore;
import ar.com.hexium.hcop.infusion.domain.Infusion;
import ar.com.hexium.hcop.treatment.application.port.out.InfusionAppointmentPort;
import ar.com.hexium.hcop.treatment.application.port.out.InfusionAppointmentPort.InfusionAppointment;
import ar.com.hexium.hcop.treatment.application.port.out.InfusionSummaryPort;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * F3.3.0: único lugar que implementa los puertos cruzados {@code treatment} → {@code infusion}
 * que no involucran sincronización de logística (ver {@link TreatmentApplicationSyncAdapter}).
 */
@Component
public class InfusionForTreatmentAdapter implements InfusionSummaryPort, InfusionAppointmentPort {
  private final InfusionUseCase infusionService;
  private final InfusionStore infusionRepository;

  public InfusionForTreatmentAdapter(
      InfusionUseCase infusionService, InfusionStore infusionRepository) {
    this.infusionService = infusionService;
    this.infusionRepository = infusionRepository;
  }

  @Override
  public List<Map<String, Object>> list(long patientId) {
    return infusionService.list(patientId, null);
  }

  @Override
  public List<InfusionAppointment> forCycle(long patientId, String treatmentId, int cycleNumber) {
    return infusionRepository.list(patientId, null).stream()
        .filter(item -> treatmentId.equals(item.treatmentId()) && item.cycleNumber() == cycleNumber)
        .map(InfusionForTreatmentAdapter::toAppointment)
        .toList();
  }

  private static InfusionAppointment toAppointment(Infusion infusion) {
    return new InfusionAppointment(
        infusion.scheduledAt(), infusion.chair(), infusion.clinicalStatus(),
        infusion.pharmacyStatus(), infusion.administrationStatus());
  }
}
