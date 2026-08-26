package ar.com.hexium.hcop.catalog.infrastructure.web;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.catalog.application.port.in.SystemicFormCatalogUseCase;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SystemicFormController {
  private final SystemicFormCatalogUseCase catalog;
  private final AuthContext auth;

  public SystemicFormController(SystemicFormCatalogUseCase catalog, AuthContext auth) {
    this.auth = auth;
    this.catalog = catalog;
  }

  @GetMapping("/api/systemic-forms")
  Map<String, Object> forms(HttpServletRequest request) {
    auth.requirePermission(request, "section.prescriptions.view");
    List<Object> forms = catalog.forms();
    return Map.of("ok", true, "count", forms.size(), "forms", forms);
  }
}
