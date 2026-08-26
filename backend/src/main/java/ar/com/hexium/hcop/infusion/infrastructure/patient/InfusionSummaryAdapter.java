package ar.com.hexium.hcop.infusion.infrastructure.patient;

import ar.com.hexium.hcop.infusion.InfusionService;
import ar.com.hexium.hcop.patient.application.port.out.InfusionSummaryPort;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** F3.3.0: único lugar que implementa el puerto cruzado {@code patient} → {@code infusion}. */
@Component
public class InfusionSummaryAdapter implements InfusionSummaryPort {
  private final InfusionService infusions;

  public InfusionSummaryAdapter(InfusionService infusions) {
    this.infusions = infusions;
  }

  @Override
  public List<Map<String, Object>> list(long patientId) {
    return infusions.list(patientId, null);
  }
}
