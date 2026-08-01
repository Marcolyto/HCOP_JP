package ar.com.hexium.hcop.clinicalhistory.infrastructure.configuration;

import ar.com.hexium.hcop.clinicalhistory.application.port.in.ClinicalEvolutionUseCase;
import ar.com.hexium.hcop.clinicalhistory.application.port.out.ClinicalEvolutionPort;
import ar.com.hexium.hcop.clinicalhistory.application.service.ClinicalEvolutionApplicationService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClinicalEvolutionModuleConfiguration {
  @Bean
  ClinicalEvolutionUseCase clinicalEvolutionUseCase(ClinicalEvolutionPort store, Clock clock) {
    return new ClinicalEvolutionApplicationService(store, clock);
  }
}
