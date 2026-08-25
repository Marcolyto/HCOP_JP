package ar.com.hexium.hcop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * F2.2: solo trae la dependencia {@code spring-boot-starter-security} sin activarla — sin este
 * bean, Boot auto-configura un login por defecto (usuario/clave generados, todo detrás de form
 * login) que rompe la API. {@code permitAll()} en todo: la autorización real la sigue haciendo
 * {@link ar.com.hexium.hcop.auth.AuthInterceptor} (sesión por cookie/Bearer opaco) hasta F2.6,
 * cuando {@code JwtAuthenticationFilter} entra y esto se endurece a {@code anyRequest().authenticated()}
 * con los `permitAll` puntuales del plan (`/error`, `/actuator/health/**`,
 * `/api/auth/login|refresh`, `/api/auth/me`, los 3 `/status`).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .csrf(AbstractHttpConfigurer::disable)
        .cors(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .anonymous(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
        .build();
  }
}
