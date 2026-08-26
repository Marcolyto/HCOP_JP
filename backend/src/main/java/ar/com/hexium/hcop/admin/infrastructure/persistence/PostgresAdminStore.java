package ar.com.hexium.hcop.admin.infrastructure.persistence;

import ar.com.hexium.hcop.admin.application.port.out.AdminStore;
import ar.com.hexium.hcop.admin.application.port.out.RoleKeyConflictException;
import ar.com.hexium.hcop.admin.application.port.out.UsernameOrEmailConflictException;
import ar.com.hexium.hcop.admin.domain.AdminRole;
import ar.com.hexium.hcop.admin.domain.AdminUser;
import ar.com.hexium.hcop.admin.domain.Permission;
import ar.com.hexium.hcop.admin.domain.SecuritySettings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresAdminStore implements AdminStore {
  private final JdbcTemplate jdbc;

  public PostgresAdminStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<AdminUser> users() {
    return jdbc.query("""
        SELECT id, username, email, display_name, specialty, license_number,
               enabled, last_login_at, created_at, updated_at
          FROM local_users
         ORDER BY lower(COALESCE(display_name, username)), id
        """, (result, row) -> user(result));
  }

  @Override
  public Optional<AdminUser> user(long id) {
    return users().stream().filter(item -> item.id() == id).findFirst();
  }

  @Override
  public List<AdminUser> usersWithPermission(String permission) {
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

  @Override
  public boolean usernameOrEmailExists(String username, String email, Long excludedId) {
    // El cast explícito es necesario: sin él, Postgres no puede inferir el tipo de "?" cuando
    // excludedId es null (createUser) porque "? IS NULL" por sí solo no ata el placeholder a
    // ningún tipo de columna — falla en runtime con "could not determine data type of parameter".
    Integer count = jdbc.queryForObject("""
        SELECT count(*) FROM local_users
         WHERE (lower(username) = lower(?) OR lower(email) = lower(?))
           AND (?::bigint IS NULL OR id <> ?)
        """, Integer.class, username, email, excludedId, excludedId);
    return count != null && count > 0;
  }

  @Override
  public boolean rolesExist(List<Long> roleIds) {
    if (roleIds.isEmpty()) return false;
    return roleIds.stream().allMatch(roleId -> {
      Integer count = jdbc.queryForObject(
          "SELECT count(*) FROM local_roles WHERE id = ? AND enabled = true", Integer.class, roleId);
      return count != null && count == 1;
    });
  }

  @Override
  public long insertUser(NewUser user) {
    try {
      return jdbc.queryForObject("""
          INSERT INTO local_users (
            username, email, display_name, specialty, license_number, enabled,
            password_hash, created_at, updated_at
          ) VALUES (?, ?, ?, NULLIF(?, ''), NULLIF(?, ''), ?, ?, ?, ?)
          RETURNING id
          """, Long.class, user.username(), user.email(), user.displayName(), user.specialty(),
          user.licenseNumber(), user.active(), user.passwordHash(),
          Timestamp.from(user.now()), Timestamp.from(user.now()));
    } catch (DataIntegrityViolationException duplicate) {
      throw new UsernameOrEmailConflictException();
    }
  }

  @Override
  public void updateUser(ExistingUser user) {
    if (user.passwordHash() == null) {
      jdbc.update("""
          UPDATE local_users
             SET username = ?, email = ?, display_name = ?, specialty = NULLIF(?, ''),
                 license_number = NULLIF(?, ''), enabled = ?, updated_at = ?
           WHERE id = ?
          """, user.username(), user.email(), user.displayName(), user.specialty(),
          user.licenseNumber(), user.active(), Timestamp.from(user.now()), user.id());
      return;
    }
    jdbc.update("""
        UPDATE local_users
           SET username = ?, email = ?, display_name = ?, specialty = NULLIF(?, ''),
               license_number = NULLIF(?, ''), enabled = ?, password_hash = ?, updated_at = ?
         WHERE id = ?
        """, user.username(), user.email(), user.displayName(), user.specialty(),
        user.licenseNumber(), user.active(), user.passwordHash(), Timestamp.from(user.now()), user.id());
  }

  @Override
  public void replaceUserRoles(long userId, List<Long> roleIds, long actorId) {
    jdbc.update("DELETE FROM local_user_roles WHERE user_id = ?", userId);
    roleIds.forEach(roleId -> jdbc.update("""
        INSERT INTO local_user_roles (user_id, role_id, assigned_by)
        SELECT ?, id, ? FROM local_roles WHERE id = ? AND enabled = true
        """, userId, actorId, roleId));
  }

  @Override
  public void revokeSessions(long userId) {
    jdbc.update("DELETE FROM local_sessions WHERE user_id = ?", userId);
  }

  @Override
  public Set<Long> roleIdsForUser(long userId) {
    return Set.copyOf(jdbc.queryForList(
        "SELECT role_id FROM local_user_roles WHERE user_id = ?", Long.class, userId));
  }

  @Override
  public List<Long> userIdsForRole(long roleId) {
    return jdbc.queryForList("SELECT user_id FROM local_user_roles WHERE role_id = ?", Long.class, roleId);
  }

  @Override
  public List<AdminRole> roles() {
    return jdbc.query("""
        SELECT r.id, r.role_key, r.display_name, r.description, r.system_role, r.enabled,
               count(DISTINCT ur.user_id) AS user_count
          FROM local_roles r
          LEFT JOIN local_user_roles ur ON ur.role_id = r.id
         GROUP BY r.id
         ORDER BY lower(r.display_name), r.id
        """, (result, row) -> role(result));
  }

  @Override
  public Optional<AdminRole> role(long id) {
    return roles().stream().filter(item -> item.id() == id).findFirst();
  }

  @Override
  public List<Permission> permissions() {
    return jdbc.query("""
        SELECT permission_key, display_name, description
          FROM local_permissions
         ORDER BY permission_key
        """, (result, row) -> new Permission(
        result.getString("permission_key"), result.getString("display_name"),
        value(result.getString("description"))));
  }

  @Override
  public boolean permissionsExist(List<String> permissions) {
    if (permissions.isEmpty()) return true;
    return Set.copyOf(permissions).stream().allMatch(permission -> {
      Integer count = jdbc.queryForObject(
          "SELECT count(*) FROM local_permissions WHERE permission_key = ?", Integer.class, permission);
      return count != null && count == 1;
    });
  }

  @Override
  public long insertRole(NewRole role) {
    try {
      return jdbc.queryForObject("""
          INSERT INTO local_roles (
            role_key, display_name, description, system_role, enabled,
            created_by, updated_by, created_at, updated_at
          ) VALUES (?, ?, NULLIF(?, ''), false, ?, ?, ?, ?, ?)
          RETURNING id
          """, Long.class, role.key(), role.name(), role.description(), role.active(),
          role.actorId(), role.actorId(), Timestamp.from(role.now()), Timestamp.from(role.now()));
    } catch (DataIntegrityViolationException duplicate) {
      throw new RoleKeyConflictException();
    }
  }

  @Override
  public void updateRole(ExistingRole role) {
    jdbc.update("""
        UPDATE local_roles
           SET display_name = ?, description = NULLIF(?, ''), enabled = ?,
               updated_by = ?, updated_at = ?
         WHERE id = ?
        """, role.name(), role.description(), role.active(), role.actorId(),
        Timestamp.from(role.now()), role.id());
  }

  @Override
  public void replaceRolePermissions(long roleId, List<String> permissions) {
    jdbc.update("DELETE FROM local_role_permissions WHERE role_id = ?", roleId);
    permissions.forEach(permission -> jdbc.update("""
        INSERT INTO local_role_permissions (role_id, permission_id)
        SELECT ?, id FROM local_permissions WHERE permission_key = ?
        """, roleId, permission));
  }

  @Override
  public Optional<SecuritySettings> security() {
    return jdbc.query("""
        SELECT s.login_required, s.auto_user_id, s.session_duration_minutes, s.revision,
               u.username, u.email, u.display_name
          FROM local_security_settings s
          LEFT JOIN local_users u ON u.id = s.auto_user_id
         WHERE s.id = 1
        """, this::securitySettings);
  }

  @Override
  public Optional<SecuritySettings> updateSecurity(int sessionDurationMinutes, long actorId) {
    jdbc.update("""
        UPDATE local_security_settings
           SET session_duration_minutes = ?, revision = revision + 1,
               updated_by = ?, updated_at = clock_timestamp()
         WHERE id = 1
        """, sessionDurationMinutes, actorId);
    return security();
  }

  private AdminUser user(ResultSet result) throws SQLException {
    long id = result.getLong("id");
    Timestamp lastLogin = result.getTimestamp("last_login_at");
    return new AdminUser(
        id,
        result.getString("username"),
        result.getString("email"),
        value(result.getString("display_name"), result.getString("username")),
        value(result.getString("specialty")),
        value(result.getString("license_number")),
        result.getBoolean("enabled"),
        lastLogin == null ? null : lastLogin.toInstant(),
        userRoles(id));
  }

  private List<AdminUser.RoleSummary> userRoles(long userId) {
    return jdbc.query("""
        SELECT r.id, r.role_key, r.display_name, r.description, r.system_role, r.enabled
          FROM local_user_roles ur
          JOIN local_roles r ON r.id = ur.role_id
         WHERE ur.user_id = ?
         ORDER BY lower(r.display_name)
        """, (result, row) -> new AdminUser.RoleSummary(
        result.getLong("id"), result.getString("role_key"), result.getString("display_name"),
        value(result.getString("description")), result.getBoolean("system_role"),
        result.getBoolean("enabled")), userId);
  }

  private AdminRole role(ResultSet result) throws SQLException {
    long id = result.getLong("id");
    return new AdminRole(
        id,
        result.getString("role_key"),
        result.getString("display_name"),
        value(result.getString("description")),
        result.getBoolean("system_role"),
        result.getBoolean("enabled"),
        result.getLong("user_count"),
        rolePermissions(id));
  }

  private Set<String> rolePermissions(long roleId) {
    return Set.copyOf(jdbc.queryForList("""
        SELECT p.permission_key
          FROM local_role_permissions rp
          JOIN local_permissions p ON p.id = rp.permission_id
         WHERE rp.role_id = ?
         ORDER BY p.permission_key
        """, String.class, roleId));
  }

  private Optional<SecuritySettings> securitySettings(ResultSet result) throws SQLException {
    if (!result.next()) return Optional.empty();
    Object autoUserId = result.getObject("auto_user_id");
    return Optional.of(new SecuritySettings(
        result.getBoolean("login_required"),
        autoUserId == null ? "" : String.valueOf(autoUserId),
        value(result.getString("username")),
        value(result.getString("email")),
        value(result.getString("display_name")),
        result.getInt("session_duration_minutes"),
        result.getLong("revision")));
  }

  private String value(String value) {
    return value(value, "");
  }

  private String value(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
