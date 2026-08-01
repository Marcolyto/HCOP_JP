package ar.com.hexium.hcop.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.auth.application.port.in.AuthenticationUseCase.ChangePasswordCommand;
import ar.com.hexium.hcop.auth.application.port.in.AuthenticationUseCase.LoginCommand;
import ar.com.hexium.hcop.auth.application.port.out.AuthenticationStorePort;
import ar.com.hexium.hcop.auth.application.port.out.PasswordHashPort;
import ar.com.hexium.hcop.auth.application.port.out.SessionTokenPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AuthenticationApplicationServiceTest {
  private final Clock clock = Clock.fixed(Instant.parse("2026-07-31T12:00:00Z"), ZoneOffset.UTC);
  private final SessionPrincipal principal = new SessionPrincipal(
      7L, "oncologo", "oncologo@hcop.test", "Oncólogo", "Oncología", "MP 1", true,
      null, java.util.List.of(), java.util.Set.of("section.history.view"));

  @Test
  void createsSessionUsingOnlyTheTokenFingerprintInTheStore() {
    RecordingStore store = new RecordingStore(principal, new AuthenticationStorePort.UserCredential(
        7L, "oncologo", "oncologo@hcop.test", "hash:clave", true));
    var service = service(store);

    var result = service.login(new LoginCommand("oncologo", "clave", "127.0.0.1", "test-agent"));

    assertThat(result.token()).isEqualTo("raw-session-token");
    assertThat(result.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(60)));
    assertThat(store.insertedTokenHash).isEqualTo("fp:raw-session-token");
    assertThat(store.insertedUserId).isEqualTo(7L);
    assertThat(store.lastLoginUserId).isEqualTo(7L);
  }

  @Test
  void rejectsInvalidCredentialsBeforeOpeningASession() {
    RecordingStore store = new RecordingStore(principal, new AuthenticationStorePort.UserCredential(
        7L, "oncologo", "oncologo@hcop.test", "hash:otra", true));
    var service = service(store);

    assertThatThrownBy(() -> service.login(new LoginCommand("oncologo", "clave", "", "")))
        .isInstanceOf(AuthenticationFailure.class)
        .hasMessage("Usuario o contraseña incorrectos.");
    assertThat(store.insertedTokenHash).isNull();
  }

  @Test
  void changesPasswordAndRevokesOtherSessions() {
    RecordingStore store = new RecordingStore(principal, new AuthenticationStorePort.UserCredential(
        7L, "oncologo", "oncologo@hcop.test", "hash:actual", true));
    var service = service(store);

    service.changePassword(new ChangePasswordCommand(principal, "current-session", "actual", "nueva-clave"));

    assertThat(store.changedPasswordHash).isEqualTo("hash:nueva-clave");
    assertThat(store.deletedOtherSessionsForUser).isEqualTo(7L);
    assertThat(store.currentSessionHash).isEqualTo("fp:current-session");
  }

  @Test
  void rejectsPasswordChangesWithoutCurrentSessionToken() {
    RecordingStore store = new RecordingStore(principal, new AuthenticationStorePort.UserCredential(
        7L, "oncologo", "oncologo@hcop.test", "hash:actual", true));
    var service = service(store);

    assertThatThrownBy(() -> service.changePassword(
        new ChangePasswordCommand(principal, "", "actual", "nueva-clave")))
        .isInstanceOf(AuthenticationFailure.class)
        .hasMessage("Debe iniciar sesión.");
    assertThat(store.changedPasswordHash).isNull();
  }

  private AuthenticationApplicationService service(RecordingStore store) {
    return new AuthenticationApplicationService(
        store,
        new TestPasswords(),
        new TestTokens(),
        clock,
        Duration.ofMinutes(60));
  }

  private static final class TestPasswords implements PasswordHashPort {
    @Override
    public String encode(String rawPassword) {
      return "hash:" + rawPassword;
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
      return ("hash:" + rawPassword).equals(encodedPassword);
    }
  }

  private static final class TestTokens implements SessionTokenPort {
    @Override
    public String create() {
      return "raw-session-token";
    }

    @Override
    public String fingerprint(String rawToken) {
      return "fp:" + rawToken;
    }
  }

  private static final class RecordingStore implements AuthenticationStorePort {
    private final SessionPrincipal principal;
    private final UserCredential credential;
    private String insertedTokenHash;
    private Long insertedUserId;
    private Long lastLoginUserId;
    private String changedPasswordHash;
    private Long deletedOtherSessionsForUser;
    private String currentSessionHash;

    private RecordingStore(SessionPrincipal principal, UserCredential credential) {
      this.principal = principal;
      this.credential = credential;
    }

    @Override
    public Optional<UserCredential> findCredential(String identifier) {
      return credential.username().equalsIgnoreCase(identifier) ? Optional.of(credential) : Optional.empty();
    }

    @Override
    public Optional<SessionPrincipal> findSession(String tokenHash, Instant now) {
      return Optional.of(principal);
    }

    @Override
    public void insertSession(String tokenHash, long userId, Instant expiresAt, String clientAddress, String userAgent) {
      insertedTokenHash = tokenHash;
      insertedUserId = userId;
    }

    @Override
    public void touchSession(String tokenHash, Instant now) {
    }

    @Override
    public void deleteSession(String tokenHash) {
    }

    @Override
    public void deleteOtherSessions(long userId, String currentTokenHash) {
      deletedOtherSessionsForUser = userId;
      currentSessionHash = currentTokenHash;
    }

    @Override
    public void removeExpired(Instant now) {
    }

    @Override
    public void markLogin(long userId, Instant now) {
      lastLoginUserId = userId;
    }

    @Override
    public void changePassword(long userId, String encoded, Instant now) {
      changedPasswordHash = encoded;
    }

    @Override
    public long userCount() {
      return 1;
    }

    @Override
    public long insertBootstrapUser(
        String username,
        String email,
        String displayName,
        String passwordHash,
        Instant now) {
      return 1;
    }

    @Override
    public void assignAdministrator(long userId) {
    }
  }
}
