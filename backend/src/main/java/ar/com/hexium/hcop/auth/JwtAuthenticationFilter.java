package ar.com.hexium.hcop.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reconstruye el {@link SessionPrincipal} desde un access token JWT válido, en los mismos
 * atributos de request que {@link AuthInterceptor} usa para el modo cookie
 * ({@code AuthContext.PRINCIPAL_ATTRIBUTE}/{@code SESSION_ID_ATTRIBUTE}) — así los 93
 * {@code requirePermission} y los 4 {@code hasPermission} de filtrado de datos no distinguen el
 * modo (hallazgo 6 del plan).
 *
 * <p><b>Instanciado a mano, SIN {@code @Component}</b> (ver {@code SecurityConfiguration}): con
 * {@code @Component} Boot lo registraría también como filtro de servlet de forma automática por
 * escaneo, duplicando el registro manual de abajo — dos ejecuciones por request es, en el mejor
 * caso, trabajo repetido, y en el peor, un origen de bugs silenciosos difíciles de diagnosticar.
 *
 * <p>Nunca rechaza una request por sí solo: si el Bearer no es un JWT válido o la sesión no
 * existe, simplemente no puebla nada y deja que seguidores (AuthInterceptor, `me`) decidan —
 * eso mantiene el modo cookie exactamente igual mientras conviven ambos.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final TokenIssuer tokens;
  private final SessionStateRepository sessions;

  public JwtAuthenticationFilter(TokenIssuer tokens, SessionStateRepository sessions) {
    this.tokens = tokens;
    this.sessions = sessions;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    bearerToken(request)
        .flatMap(tokens::parse)
        .ifPresent(claims -> authenticate(request, claims));
    chain.doFilter(request, response);
  }

  private void authenticate(HttpServletRequest request, TokenIssuer.AccessTokenClaims claims) {
    UUID sid;
    try {
      sid = UUID.fromString(claims.sid());
    } catch (IllegalArgumentException malformed) {
      return;
    }
    // F2.7 agrega acá el corte por state.revoked() — por ahora solo activePatientId fresco
    // (cambia sin reemitir el token, ver TokenIssuer).
    sessions.find(sid).ifPresent(state -> {
      request.setAttribute(AuthContext.PRINCIPAL_ATTRIBUTE, claims.toPrincipal(state.activePatientId()));
      request.setAttribute(AuthContext.SESSION_ID_ATTRIBUTE, claims.sid());
    });
  }

  private Optional<String> bearerToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) return Optional.empty();
    String value = header.substring(7).trim();
    return value.isBlank() ? Optional.empty() : Optional.of(value);
  }
}
