package ar.com.hexium.hcop.platform;

import ar.com.hexium.hcop.auth.AuthService;
import java.util.List;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code platform} nunca depende de las clases concretas de {@code catalog}/{@code patient} — cada
 * módulo registra su propio {@link BootstrapTask} (F3.4, rompe los ciclos
 * {@code platform}↔{@code catalog} y {@code platform}↔{@code patient} documentados en
 * DECISIONES-F3.md). Spring ordena la lista por {@code @Order}: catálogos (1) antes que el
 * paciente demo (2), igual que el orden literal anterior.
 */
@Configuration
public class BootstrapConfiguration {
  @Bean
  ApplicationRunner bootstrapLocalAdministrator(AuthService authService, List<BootstrapTask> tasks) {
    return arguments -> {
      authService.bootstrapAdministrator();
      tasks.forEach(BootstrapTask::run);
    };
  }
}
