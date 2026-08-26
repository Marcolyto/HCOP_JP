package ar.com.hexium.hcop.admin.application.port.out;

import ar.com.hexium.hcop.admin.domain.AdminRole;
import ar.com.hexium.hcop.admin.domain.AdminUser;
import ar.com.hexium.hcop.admin.domain.Permission;
import ar.com.hexium.hcop.admin.domain.SecuritySettings;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AdminStore {

  List<AdminUser> users();

  Optional<AdminUser> user(long id);

  List<AdminUser> usersWithPermission(String permission);

  boolean usernameOrEmailExists(String username, String email, Long excludedId);

  boolean rolesExist(List<Long> roleIds);

  long insertUser(NewUser user);

  void updateUser(ExistingUser user);

  void replaceUserRoles(long userId, List<Long> roleIds, long actorId);

  void revokeSessions(long userId);

  Set<Long> roleIdsForUser(long userId);

  List<Long> userIdsForRole(long roleId);

  List<AdminRole> roles();

  Optional<AdminRole> role(long id);

  List<Permission> permissions();

  boolean permissionsExist(List<String> permissions);

  long insertRole(NewRole role);

  void updateRole(ExistingRole role);

  void replaceRolePermissions(long roleId, List<String> permissions);

  Optional<SecuritySettings> security();

  Optional<SecuritySettings> updateSecurity(int sessionDurationMinutes, long actorId);

  record NewUser(
      String username, String email, String displayName, String specialty, String licenseNumber,
      boolean active, String passwordHash, Instant now) {
  }

  record ExistingUser(
      long id, String username, String email, String displayName, String specialty,
      String licenseNumber, boolean active, String passwordHash, Instant now) {
  }

  record NewRole(
      String key, String name, String description, boolean active, long actorId, Instant now) {
  }

  record ExistingRole(
      long id, String name, String description, boolean active, long actorId, Instant now) {
  }
}
