package ar.com.hexium.hcop.auth.application.service;

import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.auth.application.port.in.AuthenticationUseCase;
import ar.com.hexium.hcop.auth.application.port.out.AuthenticationStorePort;
import ar.com.hexium.hcop.auth.application.port.out.PasswordHashPort;
import ar.com.hexium.hcop.auth.application.port.out.SessionTokenPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Implementación Java pura de credenciales y ciclo de vida de sesión. */
public final class AuthenticationApplicationService implements AuthenticationUseCase {
  private final AuthenticationStorePort store;
  private final PasswordHashPort passwords;
  private final SessionTokenPort tokens;
  private final Clock clock;
  private final Duration sessionDuration;

  public AuthenticationApplicationService(
      AuthenticationStorePort store,
      PasswordHashPort passwords,
      SessionTokenPort tokens,
      Clock clock,
      Duration sessionDuration) {
    this.store = store;
    this.passwords = passwords;
    this.tokens = tokens;
    this.clock = clock;
    this.sessionDuration = sessionDuration;
  }

  @Override
  public void bootstrap(BootstrapCommand command) {
    String username = required(command.username(), "HCOP_BOOTSTRAP_USERNAME es obligatorio.");
    String password = command.password();
    if (password == null || password.length() < 10) {
      throw new AuthenticationFailure(
          AuthenticationFailure.Reason.INVALID_BOOTSTRAP_CONFIGURATION,
          "HCOP_BOOTSTRAP_PASSWORD es obligatorio y debe tener al menos 10 caracteres.");
    }
    Instant now = clock.instant();
    if (store.userCount() == 0) {
      long userId = store.insertBootstrapUser(
          username,
          value(command.email(), "local@hcop.invalid"),
          username,
          passwords.encode(password),
          now);
      store.assignAdministrator(userId);
    }
    String secondary = value(command.secondaryUsername(), "").trim();
    if (!secondary.isBlank()
        && !secondary.equalsIgnoreCase(username)
        && store.findCredential(secondary).isEmpty()) {
      long userId = store.insertBootstrapUser(
          secondary,
          secondary + "@hcop.invalid",
          secondary,
          passwords.encode(password),
          now);
      store.assignAdministrator(userId);
    }
  }

  @Override
  public LoginResult login(LoginCommand command) {
    Instant now = clock.instant();
    store.removeExpired(now);
    String identifier = value(command.identifier(), "").trim();
    AuthenticationStorePort.UserCredential credential = store.findCredential(identifier)
        .filter(AuthenticationStorePort.UserCredential::enabled)
        .orElseThrow(this::invalidCredentials);
    if (command.password() == null || !passwords.matches(command.password(), credential.passwordHash())) {
      throw invalidCredentials();
    }
    String token = tokens.create();
    String tokenHash = tokens.fingerprint(token);
    Instant expiresAt = now.plus(sessionDuration);
    store.insertSession(tokenHash, credential.id(), expiresAt, command.clientAddress(), command.userAgent());
    store.markLogin(credential.id(), now);
    SessionPrincipal principal = store.findSession(tokenHash, now).orElseThrow(() ->
        new IllegalStateException("No se pudo recuperar la sesión recién creada."));
    return new LoginResult(token, expiresAt, principal);
  }

  @Override
  public Optional<SessionPrincipal> authenticate(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) return Optional.empty();
    Instant now = clock.instant();
    String tokenHash = tokens.fingerprint(rawToken);
    Optional<SessionPrincipal> principal = store.findSession(tokenHash, now)
        .filter(SessionPrincipal::active);
    principal.ifPresent(ignored -> store.touchSession(tokenHash, now));
    return principal;
  }

  @Override
  public void logout(String rawToken) {
    if (rawToken != null && !rawToken.isBlank()) {
      store.deleteSession(tokens.fingerprint(rawToken));
    }
  }

  @Override
  public void changePassword(ChangePasswordCommand command) {
    if (command.newPassword() == null || command.newPassword().length() < 10
        || command.newPassword().length() > 256) {
      throw new AuthenticationFailure(
          AuthenticationFailure.Reason.INVALID_NEW_PASSWORD,
          "La nueva contraseña debe tener entre 10 y 256 caracteres.");
    }
    if (command.principal() == null) {
      throw new AuthenticationFailure(AuthenticationFailure.Reason.INVALID_CURRENT_PASSWORD, "Debe iniciar sesión.");
    }
    AuthenticationStorePort.UserCredential credential = store.findCredential(command.principal().username())
        .orElseThrow(() -> new AuthenticationFailure(
            AuthenticationFailure.Reason.INVALID_CURRENT_PASSWORD,
            "La contraseña actual es incorrecta."));
    if (command.currentPassword() == null || !passwords.matches(command.currentPassword(), credential.passwordHash())) {
      throw new AuthenticationFailure(
          AuthenticationFailure.Reason.INVALID_CURRENT_PASSWORD,
          "La contraseña actual es incorrecta.");
    }
    if (command.rawToken() == null || command.rawToken().isBlank()) {
      throw new AuthenticationFailure(
          AuthenticationFailure.Reason.INVALID_CURRENT_PASSWORD,
          "Debe iniciar sesión.");
    }
    Instant now = clock.instant();
    store.changePassword(command.principal().userId(), passwords.encode(command.newPassword()), now);
    store.deleteOtherSessions(command.principal().userId(), tokens.fingerprint(command.rawToken()));
  }

  private AuthenticationFailure invalidCredentials() {
    return new AuthenticationFailure(
        AuthenticationFailure.Reason.INVALID_CREDENTIALS,
        "Usuario o contraseña incorrectos.");
  }

  private String required(String value, String message) {
    String normalized = value(value, "").trim();
    if (normalized.isBlank()) {
      throw new AuthenticationFailure(AuthenticationFailure.Reason.INVALID_BOOTSTRAP_CONFIGURATION, message);
    }
    return normalized;
  }

  private String value(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
