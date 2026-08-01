package ar.com.hexium.hcop.auth.infrastructure.security;

import ar.com.hexium.hcop.auth.application.port.out.SessionTokenPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Genera tokens aleatorios y almacena sólo su huella SHA-256. */
public final class SecureSessionTokenAdapter implements SessionTokenPort {
  private final SecureRandom random = new SecureRandom();

  @Override
  public String create() {
    byte[] value = new byte[32];
    random.nextBytes(value);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }

  @Override
  public String fingerprint(String rawToken) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
