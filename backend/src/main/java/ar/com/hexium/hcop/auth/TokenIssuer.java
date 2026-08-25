package ar.com.hexium.hcop.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Firma y valida los JWT de access token. El claim {@code sid} es la traducción honesta de
 * {@code local_sessions}: se genera una vez en el login y se preserva en cada refresh (solo
 * rota el {@code jti}) — es la clave de {@code local_session_state} y de la revocación
 * inmediata (F2.7).
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
    String token = Jwts.builder()
        .issuer(issuer)
        .subject(Long.toString(principal.userId()))
        .id(UUID.randomUUID().toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiresAt))
        .claim("sid", sid)
        .claim("roles", principal.roles().stream().map(SessionPrincipal.RoleView::key).toList())
        .claim("permissions", List.copyOf(principal.permissions()))
        .signWith(key)
        .compact();
    return new IssuedToken(token, expiresAt);
  }

  public Optional<AccessTokenClaims> parse(String token) {
    if (token == null || token.isBlank()) return Optional.empty();
    try {
      Claims claims = Jwts.parser().verifyWith((javax.crypto.SecretKey) key).build()
          .parseSignedClaims(token).getPayload();
      return Optional.of(new AccessTokenClaims(
          Long.parseLong(claims.getSubject()),
          claims.get("sid", String.class),
          claims.get("roles", List.class),
          claims.get("permissions", List.class),
          claims.getExpiration().toInstant()));
    } catch (JwtException | IllegalArgumentException | NullPointerException exception) {
      return Optional.empty();
    }
  }

  public record IssuedToken(String token, Instant expiresAt) {
  }

  public record AccessTokenClaims(
      long userId, String sid, List<String> roles, List<String> permissions, Instant expiresAt) {
  }
}
