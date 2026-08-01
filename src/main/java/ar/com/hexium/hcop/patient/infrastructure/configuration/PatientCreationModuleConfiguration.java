package ar.com.hexium.hcop.patient.infrastructure.configuration;

import ar.com.hexium.hcop.patient.application.port.in.PatientCreationUseCase;
import ar.com.hexium.hcop.patient.application.port.out.PatientCreationStorePort;
import ar.com.hexium.hcop.patient.application.service.PatientCreationApplicationService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PatientCreationModuleConfiguration {
  @Bean
  PatientCreationUseCase patientCreationUseCase(PatientCreationStorePort store, Clock clock) {
    return new PatientCreationApplicationService(store, clock);
  }
}
