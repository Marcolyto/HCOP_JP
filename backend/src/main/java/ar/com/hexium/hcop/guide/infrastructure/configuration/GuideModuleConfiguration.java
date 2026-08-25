package ar.com.hexium.hcop.guide.infrastructure.configuration;

import ar.com.hexium.hcop.guide.application.port.in.GuideCatalogUseCase;
import ar.com.hexium.hcop.guide.application.port.out.GuideFileStore;
import ar.com.hexium.hcop.guide.application.port.out.GuideMetadataPort;
import ar.com.hexium.hcop.guide.application.service.GuideCatalogApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GuideModuleConfiguration {
  @Bean
  GuideCatalogUseCase guideCatalogUseCase(
      GuideFileStore files,
      GuideMetadataPort metadata) {
    return new GuideCatalogApplicationService(files, metadata);
  }
}
