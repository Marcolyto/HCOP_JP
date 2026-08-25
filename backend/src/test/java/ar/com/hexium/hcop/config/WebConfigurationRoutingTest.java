package ar.com.hexium.hcop.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Desde el corte de infraestructura (F0) el backend deja de servir el frontend: nginx replica los
 * redirects legacy y sirve los estáticos (ver {@code frontend/nginx.conf}). Este test es el
 * guardián de que el corte se mantiene — que {@code WebConfiguration} no vuelva a acumular esas
 * responsabilidades y que el classpath no vuelva a traer un {@code static/} embebido.
 */
class WebConfigurationRoutingTest {
  @Test
  void webConfigurationOnlyRegistersTheAuthInterceptor() {
    Method[] declared = WebConfiguration.class.getDeclaredMethods();
    boolean declaresViewControllers =
        java.util.Arrays.stream(declared).anyMatch(m -> m.getName().equals("addViewControllers"));
    boolean declaresResourceHandlers =
        java.util.Arrays.stream(declared).anyMatch(m -> m.getName().equals("addResourceHandlers"));

    assertFalse(declaresViewControllers, "el backend ya no redirige rutas de entrada al frontend");
    assertFalse(declaresResourceHandlers, "el backend ya no sirve estáticos del frontend");
  }

  @Test
  void classpathHasNoEmbeddedFrontend() {
    Path staticDir = Path.of("src/main/resources/static");
    assertTrue(
        Files.notExists(staticDir),
        "src/main/resources/static no debe reaparecer: el frontend vive en frontend/ y lo sirve nginx");
  }
}
