package ar.com.hexium.hcop.bff.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Exige sesión en {@code /api/**}, salvo los mismos paths públicos que hoy acepta
 * {@code AuthInterceptor.isPublic()} en el backend. Corre después de {@link BffSessionFilter}
 * (que ya intentó poblar {@link BffSessionFilter#SESSION_ATTRIBUTE}).
 *
 * <p>El 401 es byte a byte el mismo shape que {@code AuthenticationRequiredResponse.required()}
 * del backend — el frontend no puede notar de qué lado del corte vino.
 */
@Component
@Order(40)
public class SessionRequiredFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/me",
            "/api/runtime/status",
            "/api/clinical/status",
            "/api/lira/status");

    private final ObjectMapper mapper;

    public SessionRequiredFilter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean hasSession = request.getAttribute(BffSessionFilter.SESSION_ATTRIBUTE) != null;
        if (!path.startsWith("/api/") || PUBLIC_PATHS.contains(path) || hasSession) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        JsonNode body = mapper.createObjectNode()
                .put("ok", false)
                .put("authenticated", false)
                .put("loginRequired", true)
                .put("error", "Debe iniciar sesión.")
                .put("code", "AUTHENTICATION_REQUIRED")
                .put("status", 401);
        mapper.writeValue(response.getOutputStream(), body);
    }
}
