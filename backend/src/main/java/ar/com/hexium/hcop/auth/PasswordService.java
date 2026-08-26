package ar.com.hexium.hcop.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.bouncycastle.crypto.generators.SCrypt;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public final class PasswordService {
  private static final int NODE_SCRYPT_N = 16384;
  private static final int NODE_SCRYPT_R = 8;
  private static final int NODE_SCRYPT_P = 1;
  private static final int KEY_LENGTH = 64;
  private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);
  private final SecureRandom random = new SecureRandom();

  public String encode(String rawPassword) {
    return "{bcrypt}" + bcrypt.encode(rawPassword);
  }

  public boolean matches(String rawPassword, String encoded) {
    if (encoded == null || encoded.isBlank()) return false;
    if (encoded.startsWith("{bcrypt}")) {
      return bcrypt.matches(rawPassword, encoded.substring("{bcrypt}".length()));
    }
    if (encoded.startsWith("$2")) return bcrypt.matches(rawPassword, encoded);
    if (encoded.startsWith("scrypt$")) return matchesLegacyScrypt(rawPassword, encoded);
    return false;
  }

  public String randomPassword(int bytes) {
    byte[] value = new byte[bytes];
    random.nextBytes(value);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }

  private boolean matchesLegacyScrypt(String rawPassword, String encoded) {
    try {
      String[] parts = encoded.split("\\$", -1);
      if (parts.length != 3) return false;
      byte[] salt = Base64.getUrlDecoder().decode(parts[1]);
      byte[] expected = Base64.getUrlDecoder().decode(parts[2]);
      byte[] actual = SCrypt.generate(
          rawPassword.getBytes(StandardCharsets.UTF_8),
          salt,
          NODE_SCRYPT_N,
          NODE_SCRYPT_R,
          NODE_SCRYPT_P,
          expected.length == 0 ? KEY_LENGTH : expected.length);
      return MessageDigest.isEqual(expected, actual);
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }
}
