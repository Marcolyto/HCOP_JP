package ar.com.hexium.hcop.config;

import ar.com.hexium.hcop.auth.JwtAuthenticationFilter;
import ar.com.hexium.hcop.auth.SessionStateRepository;
import ar.com.hexium.hcop.auth.TokenIssuer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * F2.2: la dependencia {@code spring-boot-starter-security} solo trae este bean sin activarla —
 * sin él, Boot auto-configura un login por defecto (usuario/clave generados, todo detrás de form
 * login) que rompe la API. {@code permitAll()} en todo, deliberadamente: la autorización real
 * (93 {@code requirePermission}, 4 {@code hasPermission} de filtrado de datos) sigue viviendo en
 * {@code AuthInterceptor} — un {@code HandlerInterceptor}, no un filtro de Spring Security — para
 * ambos modos (cookie y JWT, hallazgo 6 del plan: "SessionPrincipal + AuthContext.requirePermission
 * intactos"). Endurecer acá a {@code anyRequest().authenticated()} rompería el modo cookie: los
 * filtros de Spring Security corren <b>antes</b> que cualquier {@code HandlerInterceptor}, así
 * que para cuando {@code AuthInterceptor} resuelve una sesión por cookie, el gate de Security ya
 * habría rechazado la request. Desvío consciente de la redacción literal del plan, documentado acá
 * y en el tracker — no hay forma de endurecer el filter chain sin romper cookie o reescribir los
 * 93 call-sites, ambos fuera de alcance de F2.6.
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

  /**
   * SIN {@code @Component} a propósito (ver javadoc de {@link JwtAuthenticationFilter}):
   * registrado a mano para que exista una única instancia, una única ejecución por request.
   */
  @Bean
  FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilter(
      TokenIssuer tokens,
      SessionStateRepository sessions,
      @Value("${hcop.jwt.session-revocation-check:true}") boolean revocationCheckEnabled) {
    FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(
        new JwtAuthenticationFilter(tokens, sessions, revocationCheckEnabled));
    registration.addUrlPatterns("/api/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    return registration;
  }
}
