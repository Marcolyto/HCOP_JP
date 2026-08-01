package ar.com.hexium.hcop.patientcontext.infrastructure.configuration;

import ar.com.hexium.hcop.patientcontext.application.port.in.ActivePatientContextUseCase;
import ar.com.hexium.hcop.patientcontext.application.service.ActivePatientContextApplicationService;
import ar.com.hexium.hcop.patientcontext.infrastructure.persistence.JdbcPatientContextAdapter;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class PatientContextModuleConfiguration {
  @Bean
  JdbcPatientContextAdapter jdbcPatientContextAdapter(JdbcTemplate jdbc) {
    return new JdbcPatientContextAdapter(jdbc);
  }

  @Bean
  ActivePatientContextUseCase activePatientContextUseCase(
      JdbcPatientContextAdapter adapter,
      Clock clock) {
    return new ActivePatientContextApplicationService(adapter, adapter, clock);
  }
}
