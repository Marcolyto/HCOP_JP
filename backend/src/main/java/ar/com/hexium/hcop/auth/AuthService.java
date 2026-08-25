package ar.com.hexium.hcop.auth;

import ar.com.hexium.hcop.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Token Handler JWT (F2.8 — el modo cookie/opaco se eliminó junto con {@code local_sessions},
 * ver {@code V014}). La sesión vive en {@code local_session_state} (claim {@code sid}); el
 * cliente solo recibe el par access+refresh, nunca {@code Set-Cookie} — quien guarda eso es el
 * BFF (F2.7.5), no el navegador.
 */
@Service
public class AuthService {
  private final AuthRepository repository;
  private final PasswordService passwords;
  private final Clock clock;
  private final TokenIssuer tokens;
  private final JwtProperties jwtProperties;
  private final SessionStateRepository sessions;
  private final RefreshTokenRepository refreshTokens;
  private final String bootstrapUsername;
  private final String bootstrapPassword;
  private final String bootstrapEmail;
  private final String secondaryUsername;

  public AuthService(
      AuthRepository repository,
      PasswordService passwords,
      Clock clock,
      TokenIssuer tokens,
      JwtProperties jwtProperties,
      SessionStateRepository sessions,
      RefreshTokenRepository refreshTokens,
      @Value("${HCOP_BOOTSTRAP_USERNAME:${HCOP_BOOTSTRAP_USER:marcolyto}}") String bootstrapUsername,
      @Value("${HCOP_BOOTSTRAP_PASSWORD:}") String bootstrapPassword,
      @Value("${HCOP_BOOTSTRAP_EMAIL:local@hcop.invalid}") String bootstrapEmail,
      @Value("${HCOP_BOOTSTRAP_SECOND_USERNAME:marcolyto2}") String secondaryUsername) {
    this.repository = repository;
    this.passwords = passwords;
    this.clock = clock;
    this.tokens = tokens;
    this.jwtProperties = jwtProperties;
    this.sessions = sessions;
    this.refreshTokens = refreshTokens;
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
  public JwtLoginResult login(String identifier, String password, String clientAddress, String userAgent) {
    Instant now = clock.instant();
    AuthRepository.UserCredential credential = repository.findCredential(identifier)
        .filter(AuthRepository.UserCredential::enabled)
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos."));
    if (!passwords.matches(password, credential.passwordHash())) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos.");
    }
    repository.markLogin(credential.id(), now);
    UUID sid = sessions.create(credential.id());
    SessionPrincipal principal = repository.findPrincipalByUserId(credential.id(), null).orElseThrow();
    return issueTokens(principal, sid, clientAddress, userAgent);
  }

  /** Rotación: el {@code jti} viejo se revoca, el {@code sid} se preserva. Roles/permisos se
   * releen de la base en cada refresh (nunca más viejos que el intervalo de refresh). */
  @Transactional
  public JwtLoginResult refresh(String refreshToken, String clientAddress, String userAgent) {
    TokenIssuer.RefreshTokenClaims claims = tokens.parseRefreshToken(refreshToken)
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token inválido."));
    Instant now = clock.instant();
    RefreshTokenRepository.RefreshToken stored = refreshTokens.find(claims.jti())
        .filter(token -> token.isUsable(now))
        .filter(token -> token.sid().equals(claims.sid()))
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token inválido."));
    if (sessions.isRevoked(stored.sid())) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "La sesión fue revocada.");
    }
    refreshTokens.revoke(stored.jti());
    SessionStateRepository.SessionState state = sessions.find(stored.sid())
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token inválido."));
    SessionPrincipal principal = repository.findPrincipalByUserId(stored.userId(), state.activePatientId())
        .filter(SessionPrincipal::active)
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Usuario deshabilitado."));
    return issueTokens(principal, stored.sid(), clientAddress, userAgent);
  }

  @Transactional
  public void logout(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) return;
    tokens.parseRefreshToken(refreshToken).ifPresent(claims -> {
      sessions.revoke(claims.sid(), clock.instant());
      refreshTokens.revokeAllForSession(claims.sid());
    });
  }

  @Transactional
  public void changePassword(
      SessionPrincipal principal, String sessionId, String currentPassword, String newPassword) {
    if (newPassword == null || newPassword.length() < 10 || newPassword.length() > 256) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "La nueva contraseña debe tener entre 10 y 256 caracteres.");
    }
    AuthRepository.UserCredential credential = repository.findCredential(principal.username()).orElseThrow();
    if (!passwords.matches(currentPassword, credential.passwordHash())) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "La contraseña actual es incorrecta.");
    }
    Instant now = clock.instant();
    repository.changePassword(principal.userId(), passwords.encode(newPassword), now);
    // Revoca toda otra sesión del usuario, preserva la que hizo el cambio.
    currentSid(sessionId).ifPresent(currentSid -> {
      sessions.revokeAllForUserExcept(principal.userId(), currentSid, now);
      refreshTokens.revokeAllForUserExcept(principal.userId(), currentSid);
    });
  }

  @Transactional
  public void setActivePatient(String sessionId, Long patientId) {
    if (patientId != null && !repository.patientExists(patientId)) {
      throw new ApiException(HttpStatus.NOT_FOUND, "Paciente no encontrado.");
    }
    UUID sid = currentSid(sessionId)
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión."));
    sessions.setActivePatient(sid, patientId, clock.instant());
  }

  private JwtLoginResult issueTokens(
      SessionPrincipal principal, UUID sid, String clientAddress, String userAgent) {
    TokenIssuer.IssuedToken access = tokens.issueAccessToken(principal, sid.toString(), jwtProperties.accessTokenTtl());
    Duration refreshTtl = Duration.ofMinutes(repository.sessionDurationMinutes());
    UUID jti = UUID.randomUUID();
    TokenIssuer.IssuedToken refresh = tokens.issueRefreshToken(principal.userId(), sid, jti, refreshTtl);
    refreshTokens.insert(jti, sid, principal.userId(), refresh.expiresAt(), clientAddress, userAgent);
    return new JwtLoginResult(access, refresh, principal);
  }

  private java.util.Optional<UUID> currentSid(String sessionId) {
    try {
      return java.util.Optional.of(UUID.fromString(sessionId));
    } catch (IllegalArgumentException | NullPointerException malformed) {
      return java.util.Optional.empty();
    }
  }

  /** Usado por {@code ClinicalFileService} para el token de un solo uso de borrado de estudios
   * — sin relación con la sesión (eso es {@link AuthContext#sessionId}). */
  public String sha256(String value) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  public record JwtLoginResult(
      TokenIssuer.IssuedToken access, TokenIssuer.IssuedToken refresh, SessionPrincipal principal) {
  }
}
