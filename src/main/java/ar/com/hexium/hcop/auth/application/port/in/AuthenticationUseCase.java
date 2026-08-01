package ar.com.hexium.hcop.auth.application.port.in;

import ar.com.hexium.hcop.auth.SessionPrincipal;
import java.time.Instant;
import java.util.Optional;

/** Casos de uso de identidad, sesión y contraseña independientes de HTTP. */
public interface AuthenticationUseCase {
  void bootstrap(BootstrapCommand command);

  LoginResult login(LoginCommand command);

  Optional<SessionPrincipal> authenticate(String rawToken);

  void logout(String rawToken);

  void changePassword(ChangePasswordCommand command);

  record BootstrapCommand(
      String username,
      String password,
      String email,
      String secondaryUsername) {
  }

  record LoginCommand(
      String identifier,
      String password,
      String clientAddress,
      String userAgent) {
  }

  record LoginResult(String token, Instant expiresAt, SessionPrincipal principal) {
  }

  record ChangePasswordCommand(
      SessionPrincipal principal,
      String rawToken,
      String currentPassword,
      String newPassword) {
  }
}
