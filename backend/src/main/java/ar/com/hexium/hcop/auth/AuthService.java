package ar.com.hexium.hcop.auth;

import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.config.HcopProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private final AuthRepository repository;
  private final PasswordService passwords;
  private final HcopProperties properties;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();
  private final String bootstrapUsername;
  private final String bootstrapPassword;
  private final String bootstrapEmail;
  private final String secondaryUsername;

  public AuthService(
      AuthRepository repository,
      PasswordService passwords,
      HcopProperties properties,
      Clock clock,
      @Value("${HCOP_BOOTSTRAP_USERNAME:${HCOP_BOOTSTRAP_USER:marcolyto}}") String bootstrapUsername,
      @Value("${HCOP_BOOTSTRAP_PASSWORD:}") String bootstrapPassword,
      @Value("${HCOP_BOOTSTRAP_EMAIL:local@hcop.invalid}") String bootstrapEmail,
      @Value("${HCOP_BOOTSTRAP_SECOND_USERNAME:marcolyto2}") String secondaryUsername) {
    this.repository = repository;
    this.passwords = passwords;
    this.properties = properties;
    this.clock = clock;
    this.bootstrapUsername = bootstrapUsername;
    if (bootstrapPassword == null || bootstrapPassword.length() < 10) {
      throw new IllegalStateException(
          "HCOP_BOOTSTRAP_PASSWORD es obligatorio y debe tener al menos 10 caracteres.");
    }
    this.bootstrapPassword = bootstrapPassword;
    this.bootstrapEmail = bootstrapEmail;
    this.secondaryUsername = secondaryUsername;
  }

  @Transactional
  public void bootstrapAdministrator() {
    Instant now = clock.instant();
    if (repository.userCount() == 0) {
      long id = repository.insertBootstrapUser(
          bootstrapUsername,
          bootstrapEmail,
          bootstrapUsername,
          passwords.encode(bootstrapPassword),
          now);
      repository.assignAdministrator(id);
    }
    String secondary = secondaryUsername == null ? "" : secondaryUsername.trim();
    if (!secondary.isBlank()
        && !secondary.equalsIgnoreCase(bootstrapUsername)
        && repository.findCredential(secondary).isEmpty()) {
      long id = repository.insertBootstrapUser(
          secondary,
          secondary + "@hcop.invalid",
          secondary,
          passwords.encode(bootstrapPassword),
          now);
      repository.assignAdministrator(id);
    }
  }

  @Transactional
  public LoginResult login(String identifier, String password, String clientAddress, String userAgent) {
    Instant now = clock.instant();
    repository.removeExpired(now);
    AuthRepository.UserCredential credential = repository.findCredential(identifier)
        .filter(AuthRepository.UserCredential::enabled)
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos."));
    if (!passwords.matches(password, credential.passwordHash())) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos.");
    }
    byte[] tokenBytes = new byte[32];
    random.nextBytes(tokenBytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    String tokenHash = sha256(token);
    Instant expiresAt = now.plus(Duration.ofMinutes(properties.sessionDurationMinutes()));
    repository.insertSession(tokenHash, credential.id(), expiresAt, clientAddress, userAgent);
    repository.markLogin(credential.id(), now);
    SessionPrincipal principal = repository.findSession(tokenHash, now).orElseThrow();
    return new LoginResult(token, expiresAt, principal);
  }

  @Transactional
  public Optional<SessionPrincipal> authenticate(String token) {
    if (token == null || token.isBlank()) return Optional.empty();
    Instant now = clock.instant();
    String hash = sha256(token);
    Optional<SessionPrincipal> principal = repository.findSession(hash, now)
        .filter(SessionPrincipal::active);
    principal.ifPresent(ignored -> repository.touchSession(hash, now));
    return principal;
  }

  @Transactional
  public void logout(String token) {
    if (token != null && !token.isBlank()) repository.deleteSession(sha256(token));
  }

  @Transactional
  public void changePassword(SessionPrincipal principal, String token, String currentPassword, String newPassword) {
    if (newPassword == null || newPassword.length() < 10 || newPassword.length() > 256) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "La nueva contraseña debe tener entre 10 y 256 caracteres.");
    }
    AuthRepository.UserCredential credential = repository.findCredential(principal.username()).orElseThrow();
    if (!passwords.matches(currentPassword, credential.passwordHash())) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "La contraseña actual es incorrecta.");
    }
    Instant now = clock.instant();
    repository.changePassword(principal.userId(), passwords.encode(newPassword), now);
    repository.deleteOtherSessions(principal.userId(), sha256(token));
  }

  @Transactional
  public void setActivePatient(String token, Long patientId) {
    if (patientId != null && !repository.patientExists(patientId)) {
      throw new ApiException(HttpStatus.NOT_FOUND, "Paciente no encontrado.");
    }
    repository.setActivePatient(sha256(token), patientId, clock.instant());
  }

  public String sha256(String value) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  public record LoginResult(String token, Instant expiresAt, SessionPrincipal principal) {
  }
}
