package ar.com.hexium.hcop.admin.application.port.in;

import ar.com.hexium.hcop.admin.domain.AdminRole;
import ar.com.hexium.hcop.admin.domain.AdminUser;
import ar.com.hexium.hcop.admin.domain.Permission;
import ar.com.hexium.hcop.admin.domain.SecuritySettings;
import ar.com.hexium.hcop.sharedkernel.domain.UserId;
import java.util.List;
import java.util.Optional;

public interface AdminManagementUseCase {

  List<AdminUser> users();

  List<AdminUser> usersWithPermission(String permission);

  AdminUser createUser(CreateUserCommand command);

  AdminUser updateUser(UpdateUserCommand command);

  List<AdminRole> roles();

  List<Permission> permissionCatalog();

  AdminRole createRole(CreateRoleCommand command);

  AdminRole updateRole(UpdateRoleCommand command);

  Optional<SecuritySettings> security();

  Optional<SecuritySettings> updateSecurity(UpdateSecurityCommand command);

  record CreateUserCommand(
      String username,
      String email,
      String displayName,
      String specialty,
      String licenseNumber,
      boolean active,
      String password,
      List<String> roleIds,
      UserId actorId) {
  }

  record UpdateUserCommand(
      long id,
      String username,
      String email,
      String displayName,
      String specialty,
      String licenseNumber,
      boolean active,
      String password,
      List<String> roleIds,
      UserId actorId) {
  }

  record CreateRoleCommand(
      String key,
      String name,
      String description,
      boolean active,
      List<String> permissions,
      UserId actorId) {
  }

  record UpdateRoleCommand(
      long id,
      String name,
      String description,
      boolean active,
      List<String> permissions,
      UserId actorId) {
  }

  record UpdateSecurityCommand(boolean loginRequired, int sessionDurationMinutes, UserId actorId) {
  }
}
