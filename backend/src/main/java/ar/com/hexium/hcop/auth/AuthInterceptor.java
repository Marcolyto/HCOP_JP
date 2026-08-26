package ar.com.hexium.hcop.auth;

import ar.com.hexium.hcop.platform.web.api.AuthenticationRequiredResponse;
import ar.com.hexium.hcop.platform.web.api.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

/**
 * Autorización de {@code /api/**} (F2.8 — quién resuelve el principal es exclusivamente
 * {@link JwtAuthenticationFilter}, que corre antes en la cadena de servlets; este interceptor ya
 * no sabe de tokens opacos ni cookies, solo lee lo que el filtro dejó en el request).
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {
  private final ObjectMapper mapper;

  public AuthInterceptor(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    String path = request.getRequestURI();
    Optional<SessionPrincipal> principal = currentPrincipal(request);

    if (!path.startsWith("/api/") || isPublic(path) || path.equals("/api/auth/me")) return true;
    if (principal.isPresent()) {
      String earlyPermission = earlyPermission(path, request.getMethod());
      if (earlyPermission.isBlank() || principal.get().hasPermission(earlyPermission)) return true;
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");
      mapper.writeValue(
          response.getOutputStream(),
          ApiErrorResponse.of(
              HttpServletResponse.SC_FORBIDDEN,
              "No tiene permiso para realizar esta acción.",
              "PERMISSION_DENIED"));
      return false;
    }
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    mapper.writeValue(response.getOutputStream(), AuthenticationRequiredResponse.required());
    return false;
  }

  private Optional<SessionPrincipal> currentPrincipal(HttpServletRequest request) {
    Object value = request.getAttribute(AuthContext.PRINCIPAL_ATTRIBUTE);
    return value instanceof SessionPrincipal principal ? Optional.of(principal) : Optional.empty();
  }

  /**
   * Endpoints sensibles se autorizan antes de llegar al controlador. Para solicitudes con
   * cuerpo, esto además evita materializar entradas no autorizadas. El controlador conserva
   * el mismo control como segunda barrera.
   */
  private String earlyPermission(String path, String method) {
    if ("POST".equalsIgnoreCase(method) && "/api/agent/chat".equals(path)) {
      return "section.agent.view";
    }
    if ("GET".equalsIgnoreCase(method)
        && ("/api/protocols".equals(path) || "/api/protocols/detail".equals(path))) {
      return "section.protocols.view";
    }
    if ("GET".equalsIgnoreCase(method)
        && ("/api/ajcc8".equals(path) || "/api/ajcc8/detail".equals(path))) {
      return "section.tools.view";
    }
    if ("POST".equalsIgnoreCase(method) && "/api/ajcc8/stage".equals(path)) {
      return "section.tools.use";
    }
    if ("GET".equalsIgnoreCase(method) && "/api/clinical/tools/calculators".equals(path)) {
      return "section.tools.use";
    }
    return "";
  }

  private boolean isPublic(String path) {
    // logout debe poder invalidar la sesión incluso con el access token ya vencido (el cliente
    // solo conserva un refresh token válido en ese caso) — JwtAuthenticationFilter no puebla el
    // principal si el access token no verifica, así que exigir sesión acá dejaría sin forma de
    // cerrar sesión.
    return path.equals("/api/auth/login")
        || path.equals("/api/auth/refresh")
        || path.equals("/api/auth/logout")
        || path.equals("/api/runtime/status")
        || path.equals("/api/clinical/status")
        || path.equals("/api/lira/status");
  }
}
