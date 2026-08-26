package ar.com.hexium.hcop.diagnosis.infrastructure.configuration;

import ar.com.hexium.hcop.diagnosis.application.port.in.DiagnosisUseCase;
import ar.com.hexium.hcop.diagnosis.application.port.out.PatientDiagnosisPort;
import ar.com.hexium.hcop.diagnosis.application.service.DiagnosisApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DiagnosisModuleConfiguration {
  @Bean
  DiagnosisUseCase diagnosisUseCase(PatientDiagnosisPort patientDiagnosis) {
    return new DiagnosisApplicationService(patientDiagnosis);
  }
}
