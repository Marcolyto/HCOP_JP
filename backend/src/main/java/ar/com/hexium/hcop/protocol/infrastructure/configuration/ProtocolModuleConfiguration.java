package ar.com.hexium.hcop.protocol.infrastructure.configuration;

import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase;
import ar.com.hexium.hcop.protocol.application.port.in.ProtocolManagementUseCase;
import ar.com.hexium.hcop.protocol.application.port.out.DrugCatalogPort;
import ar.com.hexium.hcop.protocol.application.port.out.ProtocolCatalogPort;
import ar.com.hexium.hcop.protocol.application.service.ProtocolApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProtocolModuleConfiguration {
  @Bean
  ProtocolManagementUseCase protocolManagementUseCase(
      ConfigurationManagementUseCase configurations,
      ProtocolCatalogPort catalog,
      DrugCatalogPort drugs) {
    return new ProtocolApplicationService(configurations, catalog, drugs);
  }
}
