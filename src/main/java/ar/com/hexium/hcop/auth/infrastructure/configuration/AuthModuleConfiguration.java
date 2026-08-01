package ar.com.hexium.hcop.auth.infrastructure.configuration;

import ar.com.hexium.hcop.auth.application.port.in.AuthenticationUseCase;
import ar.com.hexium.hcop.auth.application.port.out.AuthenticationStorePort;
import ar.com.hexium.hcop.auth.application.port.out.PasswordHashPort;
import ar.com.hexium.hcop.auth.application.port.out.SessionTokenPort;
import ar.com.hexium.hcop.auth.application.service.AuthenticationApplicationService;
import ar.com.hexium.hcop.auth.infrastructure.security.SecureSessionTokenAdapter;
import ar.com.hexium.hcop.config.HcopProperties;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthModuleConfiguration {
  @Bean
  SessionTokenPort sessionTokenPort() {
    return new SecureSessionTokenAdapter();
  }

  @Bean
  AuthenticationUseCase authenticationUseCase(
      AuthenticationStorePort store,
      PasswordHashPort passwords,
      SessionTokenPort tokens,
      HcopProperties properties,
      Clock clock) {
    return new AuthenticationApplicationService(
        store,
        passwords,
        tokens,
        clock,
        Duration.ofMinutes(properties.sessionDurationMinutes()));
  }
}
