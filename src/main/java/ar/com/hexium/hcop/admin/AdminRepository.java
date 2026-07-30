package ar.com.hexium.hcop.admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminRepository {
  private final JdbcTemplate jdbc;

  public AdminRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> users() {
    return jdbc.query("""
        SELECT id, username, email, display_name, specialty, license_number,
               enabled, last_login_at, created_at, updated_at
          FROM local_users
         ORDER BY lower(COALESCE(display_name, username)), id
        """, (result, row) -> user(result));
  }

  public Optional<Map<String, Object>> user(long id) {
    return users().stream().filter(item -> String.valueOf(id).equals(item.get("id"))).findFirst();
  }

  public List<Map<String, Object>> userRoles(long userId) {
    return jdbc.query("""
        SELECT r.id, r.role_key, r.display_name, r.description, r.system_role, r.enabled
          FROM local_user_roles ur
          JOIN local_roles r ON r.id = ur.role_id
         WHERE ur.user_id = ?
         ORDER BY lower(r.display_name)
        """, (result, row) -> roleSummary(result), userId);
  }

  public long insertUser(
      String username, String email, String displayName, String specialty, String licenseNumber,
      boolean active, String passwordHash, Instant now) {
    return jdbc.queryForObject("""
        INSERT INTO local_users (
          username, email, display_name, specialty, license_number, enabled,
          password_hash, created_at, updated_at
        ) VALUES (?, ?, ?, NULLIF(?, ''), NULLIF(?, ''), ?, ?, ?, ?)
        RETURNING id
        """, Long.class, username, email, displayName, specialty, licenseNumber, active,
        passwordHash, Timestamp.from(now), Timestamp.from(now));
  }

  public int updateUser(
      long id, String username, String email, String displayName, String specialty,
      String licenseNumber, boolean active, String passwordHash, Instant now) {
    if (passwordHash == null) {
      return jdbc.update("""
          UPDATE local_users
             SET username = ?, email = ?, display_name = ?, specialty = NULLIF(?, ''),
                 license_number = NULLIF(?, ''), enabled = ?, updated_at = ?
           WHERE id = ?
          """, username, email, displayName, specialty, licenseNumber, active,
          Timestamp.from(now), id);
    }
    return jdbc.update("""
        UPDATE local_users
           SET username = ?, email = ?, display_name = ?, specialty = NULLIF(?, ''),
               license_number = NULLIF(?, ''), enabled = ?, password_hash = ?, updated_at = ?
         WHERE id = ?
        """, username, email, displayName, specialty, licenseNumber, active, passwordHash,
        Timestamp.from(now), id);
  }

  public void replaceUserRoles(long userId, List<Long> roleIds, long actorId) {
    jdbc.update("DELETE FROM local_user_roles WHERE user_id = ?", userId);
    roleIds.forEach(roleId -> jdbc.update("""
        INSERT INTO local_user_roles (user_id, role_id, assigned_by)
        SELECT ?, id, ? FROM local_roles WHERE id = ? AND enabled = true
        """, userId, actorId, roleId));
  }

  public void revokeSessions(long userId) {
    jdbc.update("DELETE FROM local_sessions WHERE user_id = ?", userId);
  }

  public boolean usernameOrEmailExists(String username, String email, Long excludedId) {
    Integer count = jdbc.queryForObject("""
        SELECT count(*) FROM local_users
         WHERE (lower(username) = lower(?) OR lower(email) = lower(?))
           AND (? IS NULL OR id <> ?)
        """, Integer.class, username, email, excludedId, excludedId);
    return count != null && count > 0;
  }

  public boolean rolesExist(List<Long> roleIds) {
    if (roleIds.isEmpty()) return false;
    return roleIds.stream().allMatch(roleId -> {
      Integer count = jdbc.queryForObject(
          "SELECT count(*) FROM local_roles WHERE id = ? AND enabled = true",
          Integer.class, roleId);
      return count != null && count == 1;
    });
  }

  public List<Map<String, Object>> roles() {
    return jdbc.query("""
        SELECT r.id, r.role_key, r.display_name, r.description, r.system_role, r.enabled,
               count(DISTINCT ur.user_id) AS user_count
          FROM local_roles r
          LEFT JOIN local_user_roles ur ON ur.role_id = r.id
         GROUP BY r.id
         ORDER BY lower(r.display_name), r.id
        """, (result, row) -> {
      Map<String, Object> value = roleSummary(result);
      value.put("userCount", result.getLong("user_count"));
      value.put("permissions", rolePermissions(result.getLong("id")));
      return value;
    });
  }

  public Optional<Map<String, Object>> role(long id) {
    return roles().stream().filter(item -> String.valueOf(id).equals(item.get("id"))).findFirst();
  }

  public List<Map<String, Object>> permissions() {
    return jdbc.query("""
        SELECT permission_key, display_name, description
          FROM local_permissions
         ORDER BY permission_key
        """, (result, row) -> Map.of(
        "key", result.getString("permission_key"),
        "name", result.getString("display_name"),
        "description", value(result.getString("description"))));
  }

  public Set<String> rolePermissions(long roleId) {
    return Set.copyOf(jdbc.queryForList("""
        SELECT p.permission_key
          FROM local_role_permissions rp
          JOIN local_permissions p ON p.id = rp.permission_id
         WHERE rp.role_id = ?
         ORDER BY p.permission_key
        """, String.class, roleId));
  }

  public long insertRole(
      String key, String name, String description, boolean active, long actorId, Instant now) {
    return jdbc.queryForObject("""
        INSERT INTO local_roles (
          role_key, display_name, description, system_role, enabled,
          created_by, updated_by, created_at, updated_at
        ) VALUES (?, ?, NULLIF(?, ''), false, ?, ?, ?, ?, ?)
        RETURNING id
        """, Long.class, key, name, description, active, actorId, actorId,
        Timestamp.from(now), Timestamp.from(now));
  }

  public int updateRole(long id, String name, String description, boolean active, long actorId, Instant now) {
    return jdbc.update("""
        UPDATE local_roles
           SET display_name = ?, description = NULLIF(?, ''), enabled = ?,
               updated_by = ?, updated_at = ?
         WHERE id = ?
        """, name, description, active, actorId, Timestamp.from(now), id);
  }

  public void replaceRolePermissions(long roleId, List<String> permissions) {
    jdbc.update("DELETE FROM local_role_permissions WHERE role_id = ?", roleId);
    permissions.forEach(permission -> jdbc.update("""
        INSERT INTO local_role_permissions (role_id, permission_id)
        SELECT ?, id FROM local_permissions WHERE permission_key = ?
        """, roleId, permission));
  }

  public boolean permissionsExist(List<String> permissions) {
    if (permissions.isEmpty()) return true;
    return Set.copyOf(permissions).stream().allMatch(permission -> {
      Integer count = jdbc.queryForObject(
          "SELECT count(*) FROM local_permissions WHERE permission_key = ?",
          Integer.class, permission);
      return count != null && count == 1;
    });
  }

  public Map<String, Object> security() {
    return jdbc.query("""
        SELECT s.login_required, s.auto_user_id, s.session_duration_minutes, s.revision,
               u.username, u.email, u.display_name
          FROM local_security_settings s
          LEFT JOIN local_users u ON u.id = s.auto_user_id
         WHERE s.id = 1
        """, result -> {
      if (!result.next()) return Map.of();
      Map<String, Object> value = new LinkedHashMap<>();
      value.put("loginRequired", result.getBoolean("login_required"));
      Object autoUserId = result.getObject("auto_user_id");
      value.put("autoUserId", autoUserId == null ? "" : String.valueOf(autoUserId));
      value.put("autoUserUsername", value(result.getString("username")));
      value.put("autoUserEmail", value(result.getString("email")));
      value.put("autoUserName", value(result.getString("display_name")));
      value.put("sessionDurationMinutes", result.getInt("session_duration_minutes"));
      value.put("revision", result.getLong("revision"));
      return value;
    });
  }

  public Map<String, Object> updateSecurity(
      boolean loginRequired, Long autoUserId, int duration, long actorId) {
    jdbc.update("""
        UPDATE local_security_settings
           SET login_required = ?, auto_user_id = ?, session_duration_minutes = ?,
               revision = revision + 1, updated_by = ?, updated_at = clock_timestamp()
         WHERE id = 1
        """, loginRequired, autoUserId, duration, actorId);
    return security();
  }

  public List<Map<String, Object>> usersWithPermission(String permission) {
    return jdbc.query("""
        SELECT u.id, u.username, u.email, u.display_name, u.specialty, u.license_number,
               u.enabled, u.last_login_at, u.created_at, u.updated_at
          FROM local_users u
         WHERE u.enabled = true
           AND EXISTS (
             SELECT 1
               FROM local_user_roles ur
               JOIN local_roles r ON r.id = ur.role_id AND r.enabled = true
               JOIN local_role_permissions rp ON rp.role_id = r.id
               JOIN local_permissions p ON p.id = rp.permission_id
              WHERE ur.user_id = u.id AND p.permission_key = ?
           )
         ORDER BY lower(COALESCE(u.display_name, u.username))
        """, (result, row) -> user(result), permission);
  }

  private Map<String, Object> user(ResultSet result) throws SQLException {
    Map<String, Object> value = new LinkedHashMap<>();
    long id = result.getLong("id");
    value.put("id", String.valueOf(id));
    value.put("username", result.getString("username"));
    value.put("email", result.getString("email"));
    value.put("displayName", value(result.getString("display_name"), result.getString("username")));
    value.put("specialty", value(result.getString("specialty")));
    value.put("licenseNumber", value(result.getString("license_number")));
    value.put("active", result.getBoolean("enabled"));
    Timestamp lastLogin = result.getTimestamp("last_login_at");
    value.put("lastLoginAt", lastLogin == null ? "" : lastLogin.toInstant().toString());
    value.put("roles", userRoles(id));
    return value;
  }

  private Map<String, Object> roleSummary(ResultSet result) throws SQLException {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", String.valueOf(result.getLong("id")));
    value.put("key", result.getString("role_key"));
    value.put("name", result.getString("display_name"));
    value.put("description", value(result.getString("description")));
    value.put("system", result.getBoolean("system_role"));
    value.put("active", result.getBoolean("enabled"));
    return value;
  }

  private String value(String value) {
    return value(value, "");
  }

  private String value(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
