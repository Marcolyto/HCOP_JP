package ar.com.hexium.hcop.qr.infrastructure.infusion;

import ar.com.hexium.hcop.infusion.application.port.in.InfusionUseCase;
import ar.com.hexium.hcop.infusion.application.port.out.InfusionStore;
import ar.com.hexium.hcop.infusion.domain.Infusion;
import ar.com.hexium.hcop.qr.application.port.out.QrInfusionPort;
import ar.com.hexium.hcop.qr.domain.QrInfusionRef;
import ar.com.hexium.hcop.treatment.infrastructure.legacy.DayHospitalProtocolRules;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Único lugar del módulo que navega el JSON de logística (drogas por día de aplicación). */
@Component
public class QrInfusionAdapter implements QrInfusionPort {
  private final InfusionStore infusionRepository;
  private final InfusionUseCase infusionService;

  public QrInfusionAdapter(InfusionStore infusionRepository, InfusionUseCase infusionService) {
    this.infusionRepository = infusionRepository;
    this.infusionService = infusionService;
  }

  @Override
  public Optional<QrInfusionRef> findByCycle(long patientId, String treatmentId, int cycle) {
    return infusionRepository.findByCycle(patientId, treatmentId, cycle).map(this::toRef);
  }

  @Override
  public Optional<QrInfusionRef> findByApplication(
      long patientId, String treatmentId, int cycle, int applicationDay) {
    return infusionRepository.findByApplication(patientId, treatmentId, cycle, applicationDay)
        .map(this::toRef);
  }

  @Override
  public Optional<Boolean> dayHospitalEligibility(
      long patientId, String treatmentId, int cycle, int applicationDay) {
    return infusionRepository.logistics(patientId, treatmentId, cycle, applicationDay)
        .map(logistics -> {
          JsonNode components = (JsonNode) logistics.applicationDrugs();
          return components.isArray() && !components.isEmpty()
              && components.valueStream().anyMatch(DayHospitalProtocolRules::requiresDayHospital);
        });
  }

  @Override
  public Optional<Map<String, Object>> view(long infusionId) {
    return infusionRepository.find(infusionId).map(infusionService::view);
  }

  private QrInfusionRef toRef(Infusion infusion) {
    return new QrInfusionRef(
        infusion.id(), infusion.cycleNumber(), infusion.applicationDay(), infusion.scheme(),
        infusion.scheduledAt());
  }
}
