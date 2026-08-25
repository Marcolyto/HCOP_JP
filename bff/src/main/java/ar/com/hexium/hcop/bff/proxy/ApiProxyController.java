package ar.com.hexium.hcop.bff.proxy;

import ar.com.hexium.hcop.bff.auth.BffSession;
import ar.com.hexium.hcop.bff.auth.BffSessionResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proxy genérico de {@code /api/**}. {@code BffAuthController} ya se quedó con login/logout/me
 * (mapeos más específicos, Spring los prioriza); todo lo demás — incluidos
 * {@code PUT /api/auth/password} y {@code PUT /api/auth/active-patient}, pass-through simple —
 * cae acá.
 *
 * <p>Resuelve la sesión leyendo Redis directo (vía {@link BffSessionResolver}) en vez de un
 * atributo de request poblado por filtro: F1.4 todavía no existe. Cuando {@code BffSessionFilter}
 * exista va a resolver una sola vez por request y este controller va a leer su atributo — mismo
 * comportamiento hacia afuera, sin doble lookup a Redis.
 */
@RestController
public class ApiProxyController {

    private final BackendApiClient backend;
    private final BffSessionResolver sessionResolver;

    public ApiProxyController(BackendApiClient backend, BffSessionResolver sessionResolver) {
        this.backend = backend;
        this.sessionResolver = sessionResolver;
    }

    @RequestMapping("/api/**")
    public void proxy(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String backendToken = sessionResolver.resolve(request).map(BffSession::backendToken).orElse(null);
        backend.forward(request, response, backendToken);
    }
}
