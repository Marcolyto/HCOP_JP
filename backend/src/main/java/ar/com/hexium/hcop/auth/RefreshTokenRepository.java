package ar.com.hexium.hcop.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@code local_refresh_tokens}: un refresh token por fila. Rotación en cada
 * {@code POST /api/auth/refresh} — el {@code jti} viejo se revoca y se inserta uno nuevo con el
 * mismo {@code sid}.
 */
@Repository
public class RefreshTokenRepository {
  private final JdbcTemplate jdbc;

  public RefreshTokenRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(
      UUID jti, UUID sid, long userId, Instant expiresAt, String clientAddress, String userAgent) {
    jdbc.update("""
        INSERT INTO local_refresh_tokens
          (jti, sid, user_id, expires_at, client_address, user_agent)
        VALUES (?, ?, ?, ?, CAST(NULLIF(?, '') AS inet), ?)
        """, jti, sid, userId, Timestamp.from(expiresAt), clientAddress, userAgent);
  }

  public Optional<RefreshToken> find(UUID jti) {
    return jdbc.query("""
        SELECT jti, sid, user_id, expires_at, revoked
          FROM local_refresh_tokens
         WHERE jti = ?
        """, this::row, jti).stream().findFirst();
  }

  public void revoke(UUID jti) {
    jdbc.update("UPDATE local_refresh_tokens SET revoked = true WHERE jti = ?", jti);
  }

  public void revokeAllForSession(UUID sid) {
    jdbc.update("UPDATE local_refresh_tokens SET revoked = true WHERE sid = ? AND revoked = false", sid);
  }

  public void revokeAllForUser(long userId) {
    jdbc.update("UPDATE local_refresh_tokens SET revoked = true WHERE user_id = ? AND revoked = false", userId);
  }

  public void removeExpired(Instant now) {
    jdbc.update("DELETE FROM local_refresh_tokens WHERE expires_at <= ?", Timestamp.from(now));
  }

  private RefreshToken row(ResultSet result, int rowNumber) throws SQLException {
    return new RefreshToken(
        (UUID) result.getObject("jti"),
        (UUID) result.getObject("sid"),
        result.getLong("user_id"),
        result.getTimestamp("expires_at").toInstant(),
        result.getBoolean("revoked"));
  }

  public record RefreshToken(UUID jti, UUID sid, long userId, Instant expiresAt, boolean revoked) {
    public boolean isUsable(Instant now) {
      return !revoked && expiresAt.isAfter(now);
    }
  }
}
