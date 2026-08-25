package ar.com.hexium.hcop.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guardián del hallazgo 1 del plan F3 (la regla de mayor retorno): {@code OpenApiConfiguration}
 * indexa a mano {@code Controller.metodo} en dos mapas estáticos ({@code DOCUMENTATION},
 * {@code PERMISSIONS}) — un rename de clase o de método deja esas claves apuntando a nada, en
 * silencio, sin que ningún test lo note (el summary/permiso simplemente cae al fallback genérico).
 * Este test convierte ese riesgo silencioso en un fallo de test inmediato: recorre las dos claves
 * y verifica por reflexión que cada una resuelve a un {@code @RestController} real con ese
 * {@code getSimpleName()} y un método con ese nombre.
 */
class OpenApiDocumentationKeysTest {

  @Test
  void todasLasClavesDeDocumentationResuelvenAUnControllerYUnMetodoReales() throws Exception {
    assertKeysResolve(readMap("DOCUMENTATION"));
  }

  @Test
  void todasLasClavesDePermissionsResuelvenAUnControllerYUnMetodoReales() throws Exception {
    assertKeysResolve(readMap("PERMISSIONS"));
  }

  private void assertKeysResolve(Map<String, ?> keyed) {
    Map<String, Class<?>> controllersBySimpleName = restControllersBySimpleName();
    List<String> broken = new ArrayList<>();
    for (String key : keyed.keySet()) {
      int dot = key.indexOf('.');
      if (dot < 0) {
        broken.add(key + " -> clave sin el formato Controller.metodo");
        continue;
      }
      String controllerName = key.substring(0, dot);
      String methodName = key.substring(dot + 1);
      Class<?> controller = controllersBySimpleName.get(controllerName);
      if (controller == null) {
        broken.add(key + " -> no existe un @RestController llamado " + controllerName);
        continue;
      }
      boolean hasMethod = java.util.Arrays.stream(controller.getDeclaredMethods())
          .anyMatch(method -> method.getName().equals(methodName));
      if (!hasMethod) {
        broken.add(key + " -> " + controllerName + " no declara un método " + methodName);
      }
    }
    assertThat(broken).isEmpty();
  }

  private Map<String, Class<?>> restControllersBySimpleName() {
    JavaClasses imported = new ClassFileImporter().importPackages("ar.com.hexium.hcop");
    Map<String, Class<?>> result = new java.util.HashMap<>();
    imported.forEach(javaClass -> {
      if (!javaClass.isAnnotatedWith(RestController.class)) return;
      Class<?> reflected = javaClass.reflect();
      result.put(reflected.getSimpleName(), reflected);
    });
    return result;
  }

  @SuppressWarnings("unchecked")
  private Map<String, ?> readMap(String fieldName) throws Exception {
    Field field = OpenApiConfiguration.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return (Map<String, ?>) field.get(null);
  }
}
