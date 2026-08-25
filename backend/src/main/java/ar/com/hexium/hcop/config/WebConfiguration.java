package ar.com.hexium.hcop.config;

import ar.com.hexium.hcop.auth.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Desde el corte de infraestructura (F0), el backend deja de servir el frontend: ya no hay
 * {@code static/}, ni redirects de entrada, ni resource handlers. Esas responsabilidades pasan a
 * nginx (ver {@code frontend/nginx.conf}), que replica los mismos redirects legacy que antes vivían
 * acá. Este configurador queda solo con el interceptor de autenticación de {@code /api/**}.
 */
@Configuration
public class WebConfiguration implements WebMvcConfigurer {
  private final AuthInterceptor authInterceptor;

  public WebConfiguration(AuthInterceptor authInterceptor) {
    this.authInterceptor = authInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
  }
}
