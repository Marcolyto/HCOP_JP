package ar.com.hexium.hcop.admin.infrastructure.configuration;

import ar.com.hexium.hcop.admin.application.port.in.AdminManagementUseCase;
import ar.com.hexium.hcop.admin.application.port.out.AdminStore;
import ar.com.hexium.hcop.admin.application.service.AdminApplicationService;
import ar.com.hexium.hcop.admin.domain.AdminRole;
import ar.com.hexium.hcop.admin.domain.AdminUser;
import ar.com.hexium.hcop.admin.domain.Permission;
import ar.com.hexium.hcop.admin.domain.SecuritySettings;
import ar.com.hexium.hcop.auth.PasswordService;
import ar.com.hexium.hcop.auth.RefreshTokenRepository;
import ar.com.hexium.hcop.auth.SessionStateRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aplica los límites transaccionales sin contaminar la capa de aplicación con Spring.
 */
@Service
public class TransactionalAdminManagement implements AdminManagementUseCase {
  private final AdminApplicationService delegate;

  public TransactionalAdminManagement(
      AdminStore store,
      PasswordService passwords,
      Clock clock,
      SessionStateRepository sessions,
      RefreshTokenRepository refreshTokens) {
    this.delegate = new AdminApplicationService(store, passwords, clock, sessions, refreshTokens);
  }

  @Override
  @Transactional(readOnly = true)
  public List<AdminUser> users() {
    return delegate.users();
  }

  @Override
  @Transactional(readOnly = true)
  public List<AdminUser> usersWithPermission(String permission) {
    return delegate.usersWithPermission(permission);
  }

  @Override
  @Transactional
  public AdminUser createUser(CreateUserCommand command) {
    return delegate.createUser(command);
  }

  @Override
  @Transactional
  public AdminUser updateUser(UpdateUserCommand command) {
    return delegate.updateUser(command);
  }

  @Override
  @Transactional(readOnly = true)
  public List<AdminRole> roles() {
    return delegate.roles();
  }

  @Override
  @Transactional(readOnly = true)
  public List<Permission> permissionCatalog() {
    return delegate.permissionCatalog();
  }

  @Override
  @Transactional
  public AdminRole createRole(CreateRoleCommand command) {
    return delegate.createRole(command);
  }

  @Override
  @Transactional
  public AdminRole updateRole(UpdateRoleCommand command) {
    return delegate.updateRole(command);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<SecuritySettings> security() {
    return delegate.security();
  }

  @Override
  @Transactional
  public Optional<SecuritySettings> updateSecurity(UpdateSecurityCommand command) {
    return delegate.updateSecurity(command);
  }
}
