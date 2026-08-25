package ar.com.hexium.hcop.config;

import ar.com.hexium.hcop.auth.AuthInterceptor;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {
  static final Map<String, String> ANGULAR_ENTRY_REDIRECTS = angularEntryRedirects();
  private final AuthInterceptor authInterceptor;

  public WebConfiguration(AuthInterceptor authInterceptor) {
    this.authInterceptor = authInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
  }

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    // Corte de entrada único: las rutas públicas históricas sólo conducen al
    // frontend Angular. Los archivos legacy pueden conservarse como referencia
    // visual durante el corte, pero ninguna entrada operativa los ejecuta.
    ANGULAR_ENTRY_REDIRECTS.forEach(registry::addRedirectViewController);
    registry.addViewController("/app/").setViewName("forward:/app/index.html");
    registry.addRedirectViewController("/docs", "/docs/index.html");
    registry.addRedirectViewController("/docs/", "/docs/index.html");
  }

  private static Map<String, String> angularEntryRedirects() {
    Map<String, String> redirects = new LinkedHashMap<>();
    redirects.put("/", "/app/");
    redirects.put("/index.html", "/app/");
    redirects.put("/app", "/app/");
    redirects.put("/configuration", "/app/#/configuration");
    redirects.put("/configuration/", "/app/#/configuration");
    redirects.put("/configuration/index.html", "/app/#/configuration");
    redirects.put("/protocol-admin", "/app/#/configuration?tab=protocols");
    redirects.put("/protocol-admin/", "/app/#/configuration?tab=protocols");
    redirects.put("/protocol-admin/index.html", "/app/#/configuration?tab=protocols");
    redirects.put("/herramientas", "/app/#/herramientas");
    redirects.put("/herramientas/", "/app/#/herramientas");
    redirects.put("/herramientas/index.html", "/app/#/herramientas");
    return Map.copyOf(redirects);
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/**")
        .addResourceLocations("classpath:/static/")
        .setCacheControl(CacheControl.maxAge(Duration.ZERO).mustRevalidate());
  }
}
