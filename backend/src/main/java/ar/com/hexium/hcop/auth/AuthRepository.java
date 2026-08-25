package ar.com.hexium.hcop.auth;

import ar.com.hexium.hcop.auth.SessionPrincipal.RoleView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthRepository {
  private final JdbcTemplate jdbc;

  public AuthRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  Optional<UserCredential> findCredential(String identifier) {
    return jdbc.query("""
        SELECT id, username, email, password_hash, enabled
          FROM local_users
         WHERE lower(username) = lower(?) OR lower(email) = lower(?)
         ORDER BY CASE WHEN lower(username) = lower(?) THEN 0 ELSE 1 END
         LIMIT 1
        """, this::credential, identifier, identifier, identifier).stream().findFirst();
  }

  Optional<SessionPrincipal> findSession(String tokenHash, Instant now) {
    List<UserRow> rows = jdbc.query("""
        SELECT u.id, u.username, u.email, u.display_name, u.specialty, u.license_number,
               u.enabled, s.active_patient_id, r.id AS role_id, r.role_key, r.display_name AS role_name,
               p.permission_key
          FROM local_sessions s
          JOIN local_users u ON u.id = s.user_id
          LEFT JOIN local_user_roles ur ON ur.user_id = u.id
          LEFT JOIN local_roles r ON r.id = ur.role_id AND r.enabled = true
          LEFT JOIN local_role_permissions rp ON rp.role_id = r.id
          LEFT JOIN local_permissions p ON p.id = rp.permission_id
         WHERE s.token_hash = ? AND s.expires_at > ?
         ORDER BY r.id, p.permission_key
        """, this::userRow, tokenHash, Timestamp.from(now));
    if (rows.isEmpty()) return Optional.empty();
    UserRow first = rows.getFirst();
    List<RoleView> roles = new ArrayList<>();
    Set<String> roleIds = new LinkedHashSet<>();
    Set<String> permissions = new LinkedHashSet<>();
    for (UserRow row : rows) {
      if (row.roleId() != null && roleIds.add(row.roleId())) {
        roles.add(new RoleView(row.roleId(), row.roleKey(), row.roleName()));
      }
      if (row.permission() != null) permissions.add(row.permission());
    }
    return Optional.of(new SessionPrincipal(
        first.id(), first.username(), first.email(), value(first.displayName(), first.username()),
        value(first.specialty(), ""), value(first.licenseNumber(), ""), first.enabled(),
        first.activePatientId(), List.copyOf(roles), Set.copyOf(permissions)));
  }

  void insertSession(String tokenHash, long userId, Instant expiresAt, String clientAddress, String userAgent) {
    jdbc.update("""
        INSERT INTO local_sessions
          (token_hash, user_id, expires_at, client_address, user_agent)
        VALUES (?, ?, ?, CAST(NULLIF(?, '') AS inet), ?)
        """, tokenHash, userId, Timestamp.from(expiresAt), clientAddress, userAgent);
  }

  void touchSession(String tokenHash, Instant now) {
    jdbc.update("UPDATE local_sessions SET last_seen_at = ? WHERE token_hash = ?", Timestamp.from(now), tokenHash);
  }

  void deleteSession(String tokenHash) {
    jdbc.update("DELETE FROM local_sessions WHERE token_hash = ?", tokenHash);
  }

  void deleteOtherSessions(long userId, String currentTokenHash) {
    jdbc.update("DELETE FROM local_sessions WHERE user_id = ? AND token_hash <> ?", userId, currentTokenHash);
  }

  void removeExpired(Instant now) {
    jdbc.update("DELETE FROM local_sessions WHERE expires_at <= ?", Timestamp.from(now));
  }

  void markLogin(long userId, Instant now) {
    Timestamp timestamp = Timestamp.from(now);
    jdbc.update("UPDATE local_users SET last_login_at = ?, updated_at = ? WHERE id = ?", timestamp, timestamp, userId);
  }

  void changePassword(long userId, String encoded, Instant now) {
    jdbc.update("UPDATE local_users SET password_hash = ?, updated_at = ? WHERE id = ?", encoded, Timestamp.from(now), userId);
  }

  void setActivePatient(String tokenHash, Long patientId, Instant now) {
    jdbc.update("""
        UPDATE local_sessions
           SET active_patient_id = ?, last_seen_at = ?
         WHERE token_hash = ?
        """, patientId, Timestamp.from(now), tokenHash);
  }

  boolean patientExists(long patientId) {
    Integer count = jdbc.queryForObject(
        "SELECT count(*) FROM patients WHERE source_id = ?",
        Integer.class,
        patientId);
    return count != null && count > 0;
  }

  long userCount() {
    Long count = jdbc.queryForObject("SELECT count(*) FROM local_users", Long.class);
    return count == null ? 0 : count;
  }

  long insertBootstrapUser(
      String username,
      String email,
      String displayName,
      String passwordHash,
      Instant now) {
    return jdbc.queryForObject("""
        INSERT INTO local_users
          (username, email, display_name, password_hash, enabled, created_at, updated_at)
        VALUES (?, ?, ?, ?, true, ?, ?)
        RETURNING id
        """, Long.class, username, email, displayName, passwordHash, Timestamp.from(now), Timestamp.from(now));
  }

  void assignAdministrator(long userId) {
    jdbc.update("""
        INSERT INTO local_user_roles (user_id, role_id, assigned_by)
        SELECT ?, id, ? FROM local_roles WHERE role_key = 'administrator'
        ON CONFLICT DO NOTHING
        """, userId, userId);
    jdbc.update("""
        UPDATE local_security_settings
           SET updated_by = ?, revision = revision + 1, updated_at = clock_timestamp()
         WHERE id = 1
        """, userId);
  }

  private UserCredential credential(ResultSet result, int rowNumber) throws SQLException {
    return new UserCredential(
        result.getLong("id"),
        result.getString("username"),
        result.getString("email"),
        result.getString("password_hash"),
        result.getBoolean("enabled"));
  }

  private UserRow userRow(ResultSet result, int rowNumber) throws SQLException {
    return new UserRow(
        result.getLong("id"),
        result.getString("username"),
        result.getString("email"),
        result.getString("display_name"),
        result.getString("specialty"),
        result.getString("license_number"),
        result.getBoolean("enabled"),
        nullableLong(result, "active_patient_id"),
        result.getString("role_id"),
        result.getString("role_key"),
        result.getString("role_name"),
        result.getString("permission_key"));
  }

  private Long nullableLong(ResultSet result, String column) throws SQLException {
    long value = result.getLong(column);
    return result.wasNull() ? null : value;
  }

  private String value(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  record UserCredential(long id, String username, String email, String passwordHash, boolean enabled) {
  }

  private record UserRow(
      long id,
      String username,
      String email,
      String displayName,
      String specialty,
      String licenseNumber,
      boolean enabled,
      Long activePatientId,
      String roleId,
      String roleKey,
      String roleName,
      String permission) {
  }
}
