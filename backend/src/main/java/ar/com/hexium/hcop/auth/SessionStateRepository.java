package ar.com.hexium.hcop.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@code local_session_state}: traducción honesta de {@code local_sessions} sin {@code token_hash}
 * (F2). La clave es el {@code sid} del JWT — generado una vez en el login, preservado en cada
 * refresh. {@code activePatientId} sigue siendo per-sesión-de-navegador, igual que hoy.
 */
@Repository
public class SessionStateRepository {
  private final JdbcTemplate jdbc;

  public SessionStateRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public UUID create(long userId) {
    UUID sid = UUID.randomUUID();
    jdbc.update("INSERT INTO local_session_state (sid, user_id) VALUES (?, ?)", sid, userId);
    return sid;
  }

  public Optional<SessionState> find(UUID sid) {
    return jdbc.query("""
        SELECT sid, user_id, active_patient_id, revoked
          FROM local_session_state
         WHERE sid = ?
        """, this::row, sid).stream().findFirst();
  }

  public boolean isRevoked(UUID sid) {
    Boolean revoked = jdbc.query(
        "SELECT revoked FROM local_session_state WHERE sid = ?",
        result -> result.next() ? result.getBoolean("revoked") : null,
        sid);
    return revoked == null || revoked;
  }

  public void setActivePatient(UUID sid, Long patientId, Instant now) {
    jdbc.update("""
        UPDATE local_session_state
           SET active_patient_id = ?, updated_at = ?
         WHERE sid = ?
        """, patientId, Timestamp.from(now), sid);
  }

  public void revoke(UUID sid, Instant now) {
    jdbc.update("""
        UPDATE local_session_state SET revoked = true, updated_at = ? WHERE sid = ?
        """, Timestamp.from(now), sid);
  }

  public void revokeAllForUser(long userId, Instant now) {
    jdbc.update("""
        UPDATE local_session_state SET revoked = true, updated_at = ?
         WHERE user_id = ? AND revoked = false
        """, Timestamp.from(now), userId);
  }

  /** F2.7: changePassword revoca todo lo demás pero preserva la sesión que hizo el cambio —
   * mismo criterio que {@code AuthRepository.deleteOtherSessions} en modo cookie. */
  public void revokeAllForUserExcept(long userId, UUID currentSid, Instant now) {
    jdbc.update("""
        UPDATE local_session_state SET revoked = true, updated_at = ?
         WHERE user_id = ? AND sid <> ? AND revoked = false
        """, Timestamp.from(now), userId, currentSid);
  }

  /** F2.7: revocación disparada por AdminService.updateRole (cambia permisos de un rol) —
   * revoca a todos los usuarios que hoy tienen ese rol, sin excepción. */
  public void revokeAllForUsers(Collection<Long> userIds, Instant now) {
    if (userIds.isEmpty()) return;
    jdbc.batchUpdate(
        "UPDATE local_session_state SET revoked = true, updated_at = ? WHERE user_id = ? AND revoked = false",
        userIds.stream().map(userId -> new Object[] {Timestamp.from(now), userId}).toList());
  }

  private SessionState row(ResultSet result, int rowNumber) throws SQLException {
    return new SessionState(
        (UUID) result.getObject("sid"),
        result.getLong("user_id"),
        nullableLong(result, "active_patient_id"),
        result.getBoolean("revoked"));
  }

  private Long nullableLong(ResultSet result, String column) throws SQLException {
    long value = result.getLong(column);
    return result.wasNull() ? null : value;
  }

  public record SessionState(UUID sid, long userId, Long activePatientId, boolean revoked) {
  }
}
