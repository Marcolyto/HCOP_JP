package ar.com.hexium.hcop.auth.application.port.out;

import ar.com.hexium.hcop.auth.SessionPrincipal;
import java.time.Instant;
import java.util.Optional;

/** Persistencia que necesita el núcleo de identidad; los tokens ya llegan hasheados. */
public interface AuthenticationStorePort {
  Optional<UserCredential> findCredential(String identifier);

  Optional<SessionPrincipal> findSession(String tokenHash, Instant now);

  void insertSession(String tokenHash, long userId, Instant expiresAt, String clientAddress, String userAgent);

  void touchSession(String tokenHash, Instant now);

  void deleteSession(String tokenHash);

  void deleteOtherSessions(long userId, String currentTokenHash);

  void removeExpired(Instant now);

  void markLogin(long userId, Instant now);

  void changePassword(long userId, String encoded, Instant now);

  long userCount();

  long insertBootstrapUser(
      String username,
      String email,
      String displayName,
      String passwordHash,
      Instant now);

  void assignAdministrator(long userId);

  record UserCredential(long id, String username, String email, String passwordHash, boolean enabled) {
  }
}
