package ar.com.hexium.hcop.bff.proxy;

import ar.com.hexium.hcop.bff.auth.BffSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proxy genérico de {@code /api/**}. {@code BffAuthController} ya se quedó con login/logout/me
 * (mapeos más específicos, Spring los prioriza); todo lo demás — incluidos
 * {@code PUT /api/auth/password} y {@code PUT /api/auth/active-patient}, pass-through simple —
 * cae acá.
 *
 * <p>La sesión llega inyectada por {@code CurrentSessionArgumentResolver}, que lee lo que
 * {@code BffSessionFilter} ya resolvió una vez por request — sin pegarle a Redis de nuevo acá.
 */
@RestController
public class ApiProxyController {

    private final BackendApiClient backend;

    public ApiProxyController(BackendApiClient backend) {
        this.backend = backend;
    }

    @RequestMapping("/api/**")
    public void proxy(HttpServletRequest request, HttpServletResponse response, Optional<BffSession> session) throws IOException {
        backend.forward(request, response, session.map(BffSession::accessToken).orElse(null));
    }
}
