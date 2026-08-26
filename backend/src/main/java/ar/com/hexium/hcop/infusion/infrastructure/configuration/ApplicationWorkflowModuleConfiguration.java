package ar.com.hexium.hcop.infusion.infrastructure.configuration;

import ar.com.hexium.hcop.infusion.application.port.in.ApplicationWorkflowUseCase;
import ar.com.hexium.hcop.infusion.application.port.in.InfusionUseCase;
import ar.com.hexium.hcop.infusion.application.port.out.ApplicationWorkflowStore;
import ar.com.hexium.hcop.infusion.application.port.out.InfusionOperationsStore;
import ar.com.hexium.hcop.infusion.application.service.ApplicationWorkflowApplicationService;
import ar.com.hexium.hcop.infusion.application.service.InfusionApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Variante B — el `@Transactional` real vive en los `Postgres*Store` (ver su javadoc). */
@Configuration
public class ApplicationWorkflowModuleConfiguration {
  @Bean
  ApplicationWorkflowUseCase applicationWorkflowUseCase(ApplicationWorkflowStore store) {
    return new ApplicationWorkflowApplicationService(store);
  }

  @Bean
  InfusionUseCase infusionUseCase(InfusionOperationsStore store) {
    return new InfusionApplicationService(store);
  }
}
