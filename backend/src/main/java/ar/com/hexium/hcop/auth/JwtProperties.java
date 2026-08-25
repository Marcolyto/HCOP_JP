package ar.com.hexium.hcop.auth;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fail-fast igual que {@code HCOP_BOOTSTRAP_PASSWORD}: sin secreto de al menos 32 bytes
 * (256 bits, mínimo de HS256) el proceso no arranca.
 */
@Component
public class JwtProperties {
  private final byte[] secret;
  private final String issuer;

  public JwtProperties(
      @Value("${HCOP_JWT_SECRET:}") String secret,
      @Value("${HCOP_JWT_ISSUER:hcop-jp}") String issuer) {
    byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < 32) {
      throw new IllegalStateException(
          "HCOP_JWT_SECRET es obligatorio y debe tener al menos 32 bytes.");
    }
    this.secret = bytes;
    this.issuer = issuer;
  }

  public byte[] secret() {
    return secret;
  }

  public String issuer() {
    return issuer;
  }
}
