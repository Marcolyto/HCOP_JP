package ar.com.hexium.hcop.clinicalhistory.infrastructure.configuration;

import ar.com.hexium.hcop.clinicalhistory.application.port.in.ClinicalHistorySaveUseCase;
import ar.com.hexium.hcop.clinicalhistory.application.port.out.ClinicalHistorySavePort;
import ar.com.hexium.hcop.clinicalhistory.application.service.ClinicalHistorySaveApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClinicalHistorySaveModuleConfiguration {
  @Bean
  ClinicalHistorySaveUseCase clinicalHistorySaveUseCase(ClinicalHistorySavePort store) {
    return new ClinicalHistorySaveApplicationService(store);
  }
}
