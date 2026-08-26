package ar.com.hexium.hcop.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Firma y valida los JWT de access token. El claim {@code sid} es la traducción honesta de
 * {@code local_sessions}: se genera una vez en el login y se preserva en cada refresh (solo
 * rota el {@code jti}) — es la clave de {@code local_session_state} y de la revocación
 * inmediata (F2.7).
 *
 * <p>El access token lleva el {@link SessionPrincipal} completo (menos {@code activePatientId},
 * que cambia sin reemitir el token y se relee de {@code local_session_state} en cada request) —
 * es lo que le permite a {@code JwtAuthenticationFilter} (F2.6) reconstruirlo sin repetir el
 * join de 6 tablas de {@code AuthRepository.findSession} en cada request autenticado.
 */
@Component
public class TokenIssuer {
  private final Key key;
  private final String issuer;

  public TokenIssuer(JwtProperties properties) {
    this.key = Keys.hmacShaKeyFor(properties.secret());
    this.issuer = properties.issuer();
  }

  public IssuedToken issueAccessToken(SessionPrincipal principal, String sid, Duration ttl) {
    Instant now = Instant.now();
    Instant expiresAt = now.plus(ttl);
    List<Map<String, String>> roles = principal.roles().stream()
        .map(role -> {
          Map<String, String> value = new LinkedHashMap<>();
          value.put("id", role.id());
          value.put("key", role.key());
          value.put("name", role.name());
          return value;
        })
        .toList();
    String token = Jwts.builder()
        .issuer(issuer)
        .subject(Long.toString(principal.userId()))
        .id(UUID.randomUUID().toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiresAt))
        .claim("sid", sid)
        .claim("username", principal.username())
        .claim("email", principal.email())
        .claim("displayName", principal.displayName())
        .claim("specialty", principal.specialty())
        .claim("licenseNumber", principal.licenseNumber())
        .claim("active", principal.active())
        .claim("roles", roles)
        .claim("permissions", List.copyOf(principal.permissions()))
        .signWith(key)
        .compact();
    return new IssuedToken(token, expiresAt);
  }

  @SuppressWarnings("unchecked")
  public Optional<AccessTokenClaims> parse(String token) {
    if (token == null || token.isBlank()) return Optional.empty();
    try {
      Claims claims = parseClaims(token);
      if (claims.get("typ") != null) return Optional.empty(); // es un refresh token, no access
      List<SessionPrincipal.RoleView> roles = ((List<Map<String, String>>) (List<?>)
          claims.get("roles", List.class)).stream()
          .map(role -> new SessionPrincipal.RoleView(role.get("id"), role.get("key"), role.get("name")))
          .toList();
      return Optional.of(new AccessTokenClaims(
          Long.parseLong(claims.getSubject()),
          claims.get("sid", String.class),
          claims.get("username", String.class),
          claims.get("email", String.class),
          claims.get("displayName", String.class),
          claims.get("specialty", String.class),
          claims.get("licenseNumber", String.class),
          Boolean.TRUE.equals(claims.get("active", Boolean.class)),
          roles,
          claims.get("permissions", List.class),
          claims.getExpiration().toInstant()));
    } catch (JwtException | IllegalArgumentException | NullPointerException | ClassCastException exception) {
      return Optional.empty();
    }
  }

  /**
   * Refresh token: JWT minimal (sin roles/permissions — se re-leen de la DB en cada refresh,
   * hallazgo del plan) que solo prueba posesión de {@code jti}/{@code sid}. La fila de
   * {@code local_refresh_tokens} es el ledger de revocación real; el JWT solo evita que alguien
   * con acceso de lectura a esa tabla pueda fabricar un refresh token válido.
   */
  public IssuedToken issueRefreshToken(long userId, UUID sid, UUID jti, Duration ttl) {
    Instant now = Instant.now();
    Instant expiresAt = now.plus(ttl);
    String token = Jwts.builder()
        .issuer(issuer)
        .subject(Long.toString(userId))
        .id(jti.toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiresAt))
        .claim("sid", sid.toString())
        .claim("typ", "refresh")
        .signWith(key)
        .compact();
    return new IssuedToken(token, expiresAt);
  }

  public Optional<RefreshTokenClaims> parseRefreshToken(String token) {
    if (token == null || token.isBlank()) return Optional.empty();
    try {
      Claims claims = parseClaims(token);
      if (!"refresh".equals(claims.get("typ", String.class))) return Optional.empty();
      return Optional.of(new RefreshTokenClaims(
          Long.parseLong(claims.getSubject()),
          UUID.fromString(claims.get("sid", String.class)),
          UUID.fromString(claims.getId()),
          claims.getExpiration().toInstant()));
    } catch (JwtException | IllegalArgumentException | NullPointerException exception) {
      return Optional.empty();
    }
  }

  private Claims parseClaims(String token) {
    return Jwts.parser().verifyWith((javax.crypto.SecretKey) key).build()
        .parseSignedClaims(token).getPayload();
  }

  public record IssuedToken(String token, Instant expiresAt) {
  }

  public record AccessTokenClaims(
      long userId,
      String sid,
      String username,
      String email,
      String displayName,
      String specialty,
      String licenseNumber,
      boolean active,
      List<SessionPrincipal.RoleView> roles,
      List<String> permissions,
      Instant expiresAt) {

    /** {@code activePatientId} no viaja acá — se relee de {@code local_session_state} por
     * request, ver la nota de clase de {@link TokenIssuer}. */
    public SessionPrincipal toPrincipal(Long activePatientId) {
      return new SessionPrincipal(
          userId, username, email, displayName, specialty, licenseNumber, active,
          activePatientId, roles, java.util.Set.copyOf(permissions));
    }
  }

  public record RefreshTokenClaims(long userId, UUID sid, UUID jti, Instant expiresAt) {
  }
}
