package ar.com.hexium.hcop.bff.proxy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OpenAPI/Swagger del backend, mismos paths que nginx ya proxeaba directo en F0
 * ({@code frontend/nginx.conf}: {@code /v3/api-docs}, {@code /swagger-ui.html},
 * {@code /swagger-ui/}, {@code /webjars/}). El BFF no tiene OpenAPI propio (sin dominio, ver
 * {@code base/03-bff.md}) — reenvía tal cual, sin sesión: son públicos hoy y lo siguen siendo.
 */
@RestController
public class DocsProxyController {

    private final BackendApiClient backend;

    public DocsProxyController(BackendApiClient backend) {
        this.backend = backend;
    }

    @RequestMapping({"/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**", "/webjars/**"})
    public void proxy(HttpServletRequest request, HttpServletResponse response) throws IOException {
        backend.forward(request, response, null);
    }
}
