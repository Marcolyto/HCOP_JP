package ar.com.hexium.hcop.config;

import ar.com.hexium.hcop.auth.AuthService;
import ar.com.hexium.hcop.patient.DefaultDemoPatientBootstrap;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BootstrapConfiguration {
  @Bean
  ApplicationRunner bootstrapLocalAdministrator(
      AuthService authService,
      ClinicalCatalogBootstrap clinicalCatalogs,
      DefaultDemoPatientBootstrap demoPatient) {
    return arguments -> {
      authService.bootstrapAdministrator();
      clinicalCatalogs.seed();
      demoPatient.seed();
    };
  }
}
