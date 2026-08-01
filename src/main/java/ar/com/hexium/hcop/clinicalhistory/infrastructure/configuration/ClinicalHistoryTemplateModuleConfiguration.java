package ar.com.hexium.hcop.clinicalhistory.infrastructure.configuration;

import ar.com.hexium.hcop.clinicalhistory.application.port.in.ClinicalHistoryTemplateUseCase;
import ar.com.hexium.hcop.clinicalhistory.application.port.out.ClinicalHistoryTemplatePort;
import ar.com.hexium.hcop.clinicalhistory.application.service.ClinicalHistoryTemplateApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClinicalHistoryTemplateModuleConfiguration {
  @Bean
  ClinicalHistoryTemplateUseCase clinicalHistoryTemplateUseCase(ClinicalHistoryTemplatePort repository) {
    return new ClinicalHistoryTemplateApplicationService(repository);
  }
}
