package ar.com.hexium.hcop.system.infrastructure.configuration;

import ar.com.hexium.hcop.system.application.port.in.SystemStatusUseCase;
import ar.com.hexium.hcop.system.application.port.out.ApplicationVersionPort;
import ar.com.hexium.hcop.system.application.port.out.DatabaseHealthStore;
import ar.com.hexium.hcop.system.application.service.SystemStatusApplicationService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SystemModuleConfiguration {
  @Bean
  SystemStatusUseCase systemStatusUseCase(
      DatabaseHealthStore health, ApplicationVersionPort version, Clock clock) {
    return new SystemStatusApplicationService(health, version, clock);
  }
}
