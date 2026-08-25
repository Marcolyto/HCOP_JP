package ar.com.hexium.hcop.auth;

import ar.com.hexium.hcop.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AuthContext {
  public static final String PRINCIPAL_ATTRIBUTE = SessionPrincipal.class.getName();
  public static final String SESSION_ID_ATTRIBUTE = PRINCIPAL_ATTRIBUTE + ".sessionId";

  public SessionPrincipal require(HttpServletRequest request) {
    Object value = request.getAttribute(PRINCIPAL_ATTRIBUTE);
    if (value instanceof SessionPrincipal principal) return principal;
    throw new ApiException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión.");
  }

  /**
   * Identificador estable de la sesión actual — hoy {@code sha256(token opaco)} (mismo valor
   * que {@code local_sessions.token_hash}), calculado una única vez por {@link AuthInterceptor};
   * mañana el claim {@code sid} del JWT (F2.5+), calculado por {@code JwtAuthenticationFilter}.
   * Los consumidores (grants de borrado de imágenes, active-patient) no distinguen el modo.
   */
  public String sessionId(HttpServletRequest request) {
    Object value = request.getAttribute(SESSION_ID_ATTRIBUTE);
    return value == null ? "" : value.toString();
  }

  public void requirePermission(HttpServletRequest request, String permission) {
    SessionPrincipal principal = require(request);
    if (!principal.hasPermission(permission)) {
      throw new ApiException(HttpStatus.FORBIDDEN, "No tiene permiso para realizar esta acción.");
    }
  }
}
