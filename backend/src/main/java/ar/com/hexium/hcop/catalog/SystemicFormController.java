package ar.com.hexium.hcop.catalog;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class SystemicFormController {
  private final SystemicFormCatalogService catalog;
  private final AuthContext auth;

  public SystemicFormController(SystemicFormCatalogService catalog, AuthContext auth) {
    this.auth = auth;
    this.catalog = catalog;
  }

  @GetMapping("/api/systemic-forms")
  Map<String, Object> forms(HttpServletRequest request) {
    auth.requirePermission(request, "section.prescriptions.view");
    JsonNode forms = catalog.forms();
    if (!forms.isArray()) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Catálogo inválido.");
    return Map.of("ok", true, "count", forms.size(), "forms", forms);
  }
}
