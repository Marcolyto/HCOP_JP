package ar.com.hexium.hcop.integration.infrastructure.configuration;

import ar.com.hexium.hcop.catalog.application.port.in.SystemicFormCatalogUseCase;
import ar.com.hexium.hcop.integration.application.port.in.AgentChatUseCase;
import ar.com.hexium.hcop.integration.application.port.in.ClinicalSummaryUseCase;
import ar.com.hexium.hcop.integration.application.port.in.ClinicalTimelineExtractionUseCase;
import ar.com.hexium.hcop.integration.application.port.in.LlmConnectionTestUseCase;
import ar.com.hexium.hcop.integration.application.port.in.LlmStatusUseCase;
import ar.com.hexium.hcop.integration.application.port.in.SystemConfigurationUseCase;
import ar.com.hexium.hcop.integration.application.port.in.SystemicFormFillUseCase;
import ar.com.hexium.hcop.integration.application.port.out.LlmPort;
import ar.com.hexium.hcop.integration.application.service.AgentChatApplicationService;
import ar.com.hexium.hcop.integration.application.service.ClinicalSummaryApplicationService;
import ar.com.hexium.hcop.integration.application.service.ClinicalTimelineExtractionApplicationService;
import ar.com.hexium.hcop.integration.application.service.LlmConnectionTestApplicationService;
import ar.com.hexium.hcop.integration.application.service.LlmStatusApplicationService;
import ar.com.hexium.hcop.integration.application.service.SystemicFormFillApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IntegrationModuleConfiguration {
  @Bean
  LlmStatusUseCase llmStatusUseCase(SystemConfigurationUseCase configuration) {
    return new LlmStatusApplicationService(configuration);
  }

  @Bean
  LlmConnectionTestUseCase llmConnectionTestUseCase(SystemConfigurationUseCase configuration, LlmPort llm) {
    return new LlmConnectionTestApplicationService(configuration, llm);
  }

  @Bean
  ClinicalTimelineExtractionUseCase clinicalTimelineExtractionUseCase(
      SystemConfigurationUseCase configuration, LlmPort llm) {
    return new ClinicalTimelineExtractionApplicationService(configuration, llm);
  }

  @Bean
  ClinicalSummaryUseCase clinicalSummaryUseCase(SystemConfigurationUseCase configuration, LlmPort llm) {
    return new ClinicalSummaryApplicationService(configuration, llm);
  }

  @Bean
  SystemicFormFillUseCase systemicFormFillUseCase(
      SystemicFormCatalogUseCase forms, SystemConfigurationUseCase configuration, LlmPort llm) {
    return new SystemicFormFillApplicationService(forms, configuration, llm);
  }

  @Bean
  AgentChatUseCase agentChatUseCase(SystemConfigurationUseCase configuration, LlmPort llm) {
    return new AgentChatApplicationService(configuration, llm);
  }
}
