package ar.com.hexium.hcop.auth;

import ar.com.hexium.hcop.common.api.AuthenticationRequiredResponse;
import ar.com.hexium.hcop.common.api.ApiErrorResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

@Component
public class AuthInterceptor implements HandlerInterceptor {
  private final AuthService auth;
  private final ObjectMapper mapper;
  private final String cookieName;
  private final boolean jwtMode;

  public AuthInterceptor(
      AuthService auth,
      ObjectMapper mapper,
      ar.com.hexium.hcop.config.HcopProperties properties,
      @Value("${hcop.auth.mode:cookie}") String authMode) {
    this.auth = auth;
    this.mapper = mapper;
    this.cookieName = properties.sessionCookieName();
    this.jwtMode = "jwt".equalsIgnoreCase(authMode);
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    String path = request.getRequestURI();
    // JwtAuthenticationFilter (F2.6) corre antes y ya puebla estos mismos atributos si el
    // Bearer es un JWT válido — no volver a resolver acá (evita reautenticar dos veces y
    // mantiene un solo lugar de verdad para "quién es el principal de esta request").
    Optional<SessionPrincipal> principal = resolvePrincipal(request);

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

  private Optional<SessionPrincipal> resolvePrincipal(HttpServletRequest request) {
    Object existing = request.getAttribute(AuthContext.PRINCIPAL_ATTRIBUTE);
    if (existing instanceof SessionPrincipal already) return Optional.of(already);
    String token = bearerToken(request).or(() -> cookie(request, cookieName)).orElse("");
    Optional<SessionPrincipal> principal = auth.authenticate(token);
    principal.ifPresent(value -> {
      request.setAttribute(AuthContext.PRINCIPAL_ATTRIBUTE, value);
      request.setAttribute(AuthContext.SESSION_ID_ATTRIBUTE, auth.sha256(token));
    });
    return principal;
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
    if (path.equals("/api/auth/login") || path.equals("/api/auth/refresh")) return true;
    // Modo JWT: logout debe poder invalidar la sesión incluso con el access token ya vencido
    // (el cliente solo conserva un refresh token válido en ese caso) — JwtAuthenticationFilter
    // no puebla el principal si el access token no verifica, así que exigir sesión acá dejaría
    // sin forma de cerrar sesión. En modo cookie el comportamiento no cambia: logout sigue
    // exigiendo la cookie/Bearer opaco válido (igual que siempre).
    if (jwtMode && path.equals("/api/auth/logout")) return true;
    return path.equals("/api/runtime/status")
        || path.equals("/api/clinical/status")
        || path.equals("/api/lira/status");
  }

  /**
   * F1 (BFF): el navegador ya no le habla directo al backend, el BFF reenvía el mismo token
   * opaco como {@code Authorization: Bearer}. La cookie sigue aceptada para compose.dev.yaml
   * (debug directo del backend) y los tests/scripts existentes.
   */
  private Optional<String> bearerToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) return Optional.empty();
    String value = header.substring(7).trim();
    return value.isBlank() ? Optional.empty() : Optional.of(value);
  }

  private Optional<String> cookie(HttpServletRequest request, String name) {
    if (request.getCookies() == null) return Optional.empty();
    for (Cookie cookie : request.getCookies()) {
      if (name.equals(cookie.getName())) return Optional.ofNullable(cookie.getValue());
    }
    return Optional.empty();
  }
}
