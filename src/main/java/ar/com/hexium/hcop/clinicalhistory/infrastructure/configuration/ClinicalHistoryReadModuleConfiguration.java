package ar.com.hexium.hcop.clinicalhistory.infrastructure.configuration;

import ar.com.hexium.hcop.clinicalhistory.application.port.in.ClinicalHistoryReadUseCase;
import ar.com.hexium.hcop.clinicalhistory.application.port.out.ClinicalHistoryReadPort;
import ar.com.hexium.hcop.clinicalhistory.application.service.ClinicalHistoryReadApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClinicalHistoryReadModuleConfiguration {
  @Bean
  ClinicalHistoryReadUseCase clinicalHistoryReadUseCase(ClinicalHistoryReadPort store) {
    return new ClinicalHistoryReadApplicationService(store);
  }
}
