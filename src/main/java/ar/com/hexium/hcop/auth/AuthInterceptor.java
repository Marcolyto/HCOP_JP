package ar.com.hexium.hcop.auth;

import ar.com.hexium.hcop.common.api.AuthenticationRequiredResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

@Component
public class AuthInterceptor implements HandlerInterceptor {
  private final AuthService auth;
  private final ObjectMapper mapper;
  private final String cookieName;

  public AuthInterceptor(AuthService auth, ObjectMapper mapper, ar.com.hexium.hcop.config.HcopProperties properties) {
    this.auth = auth;
    this.mapper = mapper;
    this.cookieName = properties.sessionCookieName();
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    String path = request.getRequestURI();
    String token = cookie(request, cookieName).orElse("");
    Optional<SessionPrincipal> principal = auth.authenticate(token);
    principal.ifPresent(value -> {
      request.setAttribute(AuthContext.PRINCIPAL_ATTRIBUTE, value);
      request.setAttribute(AuthContext.TOKEN_ATTRIBUTE, token);
    });

    if (!path.startsWith("/api/") || isPublic(path) || path.equals("/api/auth/me")) return true;
    if (principal.isPresent()) return true;
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    mapper.writeValue(response.getOutputStream(), AuthenticationRequiredResponse.required());
    return false;
  }

  private boolean isPublic(String path) {
    return path.equals("/api/auth/login")
        || path.equals("/api/runtime/status")
        || path.equals("/api/clinical/status")
        || path.equals("/api/lira/status");
  }

  private Optional<String> cookie(HttpServletRequest request, String name) {
    if (request.getCookies() == null) return Optional.empty();
    for (Cookie cookie : request.getCookies()) {
      if (name.equals(cookie.getName())) return Optional.ofNullable(cookie.getValue());
    }
    return Optional.empty();
  }
}
