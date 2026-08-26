package ar.com.hexium.hcop.treatment.infrastructure.configuration;

import ar.com.hexium.hcop.media.application.port.in.ClinicalFileUseCase;
import ar.com.hexium.hcop.treatment.application.port.in.TreatmentDocumentUseCase;
import ar.com.hexium.hcop.treatment.application.port.out.InfusionAppointmentPort;
import ar.com.hexium.hcop.treatment.application.port.out.TreatmentPatientPort;
import ar.com.hexium.hcop.treatment.application.port.out.TreatmentStore;
import ar.com.hexium.hcop.treatment.application.service.TreatmentDocumentApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TreatmentDocumentModuleConfiguration {
  @Bean
  TreatmentDocumentUseCase treatmentDocumentUseCase(
      TreatmentStore treatments, TreatmentPatientPort patients, InfusionAppointmentPort infusions,
      ClinicalFileUseCase files) {
    return new TreatmentDocumentApplicationService(treatments, patients, infusions, files);
  }
}
