package ar.com.hexium.hcop.patient.infrastructure.configuration;

import ar.com.hexium.hcop.patient.application.port.in.PatientDocumentUseCase;
import ar.com.hexium.hcop.patient.application.port.out.PatientDocumentStore;
import ar.com.hexium.hcop.patient.application.service.PatientDocumentApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Cada método de {@link PatientDocumentStore} ya lleva su propio {@code @Transactional} — esta
 * aplicación es un pasamano sin límite transaccional propio que agregar. */
@Configuration
public class PatientDocumentModuleConfiguration {
  @Bean
  PatientDocumentUseCase patientDocumentUseCase(PatientDocumentStore documents) {
    return new PatientDocumentApplicationService(documents);
  }
}
