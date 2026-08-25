package ar.com.hexium.hcop.tools.infrastructure.configuration;

import ar.com.hexium.hcop.tools.application.port.in.CalculatorCatalogUseCase;
import ar.com.hexium.hcop.tools.application.port.out.CalculatorCatalogPort;
import ar.com.hexium.hcop.tools.application.service.CalculatorCatalogApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CalculatorModuleConfiguration {
  @Bean
  CalculatorCatalogUseCase calculatorCatalogUseCase(CalculatorCatalogPort catalog) {
    return new CalculatorCatalogApplicationService(catalog);
  }
}
