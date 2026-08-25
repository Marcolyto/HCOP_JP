package ar.com.hexium.hcop.integration;

import ar.com.hexium.hcop.config.HcopProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class SecretBox {
  private static final byte VERSION = 1;
  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  public SecretBox(HcopProperties properties) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(properties.encryptionSecret().getBytes(StandardCharsets.UTF_8));
      this.key = new SecretKeySpec(digest, "AES");
    } catch (Exception exception) {
      throw new IllegalStateException("No se pudo inicializar el cifrado.", exception);
    }
  }

  public byte[] encrypt(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      byte[] nonce = new byte[12];
      random.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
      byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
      return ByteBuffer.allocate(1 + nonce.length + encrypted.length)
          .put(VERSION).put(nonce).put(encrypted).array();
    } catch (Exception exception) {
      throw new IllegalStateException("No se pudo cifrar el secreto.", exception);
    }
  }

  public String decrypt(byte[] value) {
    if (value == null || value.length < 30) return "";
    try {
      ByteBuffer buffer = ByteBuffer.wrap(value);
      if (buffer.get() != VERSION) throw new IllegalStateException("Versión de secreto desconocida.");
      byte[] nonce = new byte[12];
      buffer.get(nonce);
      byte[] encrypted = new byte[buffer.remaining()];
      buffer.get(encrypted);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
      return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    } catch (Exception exception) {
      throw new IllegalStateException(
          "No se pudo descifrar la configuración. Verifique HCOP_ENCRYPTION_SECRET.", exception);
    }
  }
}
