package ar.com.hexium.hcop.configuration.infrastructure.web;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogo clínico de formularios activos de investigación.
 *
 * <p>La administración completa continúa protegida por los permisos de Configuración. Este
 * endpoint sólo expone formularios activos a quienes trabajan en Investigación.</p>
 */
@RestController
@Tag(
    name = "Catálogos",
    description = "Catálogos clínicos de solo lectura disponibles durante la atención.")
public class ResearchFormCatalogController {
  private final ConfigurationManagementUseCase configurations;
  private final ConfigurationJsonMapper json;
  private final AuthContext auth;

  public ResearchFormCatalogController(
      ConfigurationManagementUseCase configurations,
      ConfigurationJsonMapper json,
      AuthContext auth) {
    this.configurations = configurations;
    this.json = json;
    this.auth = auth;
  }

  @GetMapping("/api/clinical/research/forms")
  @Operation(
      summary = "Listar formularios activos de investigación",
      description = """
          Devuelve exclusivamente formularios activos para la sección Investigación. No expone
          versiones inactivas ni habilita operaciones administrativas de Configuración.
          """)
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Catálogo activo recuperado."),
      @ApiResponse(responseCode = "401", description = "Sesión ausente o vencida."),
      @ApiResponse(responseCode = "403", description = "El usuario no puede consultar Investigación.")
  })
  Map<String, Object> list(HttpServletRequest request) {
    auth.requirePermission(request, "section.research.view");
    List<Map<String, Object>> items = configurations.list("research-form", false)
        .stream()
        .filter(ConfigurationManagementUseCase.ConfigurationView::active)
        .map(json::view)
        .toList();
    return Map.of("ok", true, "items", items, "total", items.size());
  }
}
