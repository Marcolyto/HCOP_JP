package ar.com.hexium.hcop.auth;

import ar.com.hexium.hcop.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AuthContext {
  public static final String PRINCIPAL_ATTRIBUTE = SessionPrincipal.class.getName();
  public static final String TOKEN_ATTRIBUTE = PRINCIPAL_ATTRIBUTE + ".token";

  public SessionPrincipal require(HttpServletRequest request) {
    Object value = request.getAttribute(PRINCIPAL_ATTRIBUTE);
    if (value instanceof SessionPrincipal principal) return principal;
    throw new ApiException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión.");
  }

  public String token(HttpServletRequest request) {
    Object value = request.getAttribute(TOKEN_ATTRIBUTE);
    return value == null ? "" : value.toString();
  }

  public void requirePermission(HttpServletRequest request, String permission) {
    SessionPrincipal principal = require(request);
    if (!principal.hasPermission(permission)) {
      throw new ApiException(HttpStatus.FORBIDDEN, "No tiene permiso para realizar esta acción.");
    }
  }
}
