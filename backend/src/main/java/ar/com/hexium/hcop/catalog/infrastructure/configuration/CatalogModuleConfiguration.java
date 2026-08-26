package ar.com.hexium.hcop.catalog.infrastructure.configuration;

import ar.com.hexium.hcop.catalog.application.port.in.AjccStagingUseCase;
import ar.com.hexium.hcop.catalog.application.port.in.DiagnosisCatalogUseCase;
import ar.com.hexium.hcop.catalog.application.port.in.DrugCatalogUseCase;
import ar.com.hexium.hcop.catalog.application.port.in.LegacyProtocolCatalogUseCase;
import ar.com.hexium.hcop.catalog.application.port.in.SystemicFormCatalogUseCase;
import ar.com.hexium.hcop.catalog.application.port.in.TnmCatalogUseCase;
import ar.com.hexium.hcop.catalog.application.port.in.TreatmentCatalogUseCase;
import ar.com.hexium.hcop.catalog.application.port.out.AjccCatalogStore;
import ar.com.hexium.hcop.catalog.application.port.out.DiagnosisEquivalenceStore;
import ar.com.hexium.hcop.catalog.application.port.out.DrugCatalogStore;
import ar.com.hexium.hcop.catalog.application.port.out.LegacyProtocolCatalogStore;
import ar.com.hexium.hcop.catalog.application.port.out.SystemicFormCatalogStore;
import ar.com.hexium.hcop.catalog.application.port.out.TnmSchemaStore;
import ar.com.hexium.hcop.catalog.application.port.out.TreatmentSchemeStore;
import ar.com.hexium.hcop.catalog.application.service.AjccStagingApplicationService;
import ar.com.hexium.hcop.catalog.application.service.DiagnosisCatalogApplicationService;
import ar.com.hexium.hcop.catalog.application.service.DrugCatalogApplicationService;
import ar.com.hexium.hcop.catalog.application.service.LegacyProtocolCatalogApplicationService;
import ar.com.hexium.hcop.catalog.application.service.SystemicFormCatalogApplicationService;
import ar.com.hexium.hcop.catalog.application.service.TnmCatalogApplicationService;
import ar.com.hexium.hcop.catalog.application.service.TreatmentCatalogApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CatalogModuleConfiguration {
  @Bean
  AjccStagingUseCase ajccStagingUseCase(AjccCatalogStore store) {
    return new AjccStagingApplicationService(store);
  }

  @Bean
  TnmCatalogUseCase tnmCatalogUseCase(TnmSchemaStore store) {
    return new TnmCatalogApplicationService(store);
  }

  @Bean
  SystemicFormCatalogUseCase systemicFormCatalogUseCase(SystemicFormCatalogStore store) {
    return new SystemicFormCatalogApplicationService(store);
  }

  @Bean
  DiagnosisCatalogUseCase diagnosisCatalogUseCase(AjccStagingUseCase ajcc, DiagnosisEquivalenceStore store) {
    return new DiagnosisCatalogApplicationService(ajcc, store);
  }

  @Bean
  TreatmentCatalogUseCase treatmentCatalogUseCase(TreatmentSchemeStore store) {
    return new TreatmentCatalogApplicationService(store);
  }

  @Bean
  LegacyProtocolCatalogUseCase legacyProtocolCatalogUseCase(LegacyProtocolCatalogStore store) {
    return new LegacyProtocolCatalogApplicationService(store);
  }

  @Bean
  DrugCatalogUseCase drugCatalogUseCase(DrugCatalogStore store) {
    return new DrugCatalogApplicationService(store);
  }
}
