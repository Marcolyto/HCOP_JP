package ar.com.hexium.hcop;

import ar.com.hexium.hcop.config.HcopProperties;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

// UserDetailsServiceAutoConfiguration excluida: F2 no usa el AuthenticationManager de Spring
// Security (auth propia vía AuthInterceptor/JwtAuthenticationFilter) — sin esto Boot genera
// un usuario/contraseña de desarrollo en cada arranque que no sirve para nada acá.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(HcopProperties.class)
public class HcopApplication {
  public static void main(String[] args) {
    SpringApplication.run(HcopApplication.class, args);
  }

  @Bean
  Clock systemClock() {
    return Clock.system(ZoneId.of("America/Argentina/Buenos_Aires"));
  }
}
