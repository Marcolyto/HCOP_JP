package ar.com.hexium.hcop.auth.infrastructure.security;

import ar.com.hexium.hcop.auth.PasswordService;
import ar.com.hexium.hcop.auth.application.port.out.PasswordHashPort;
import org.springframework.stereotype.Component;

/** Adaptador del algoritmo de hash vigente hacia el puerto de autenticación. */
@Component
public final class PasswordServiceAdapter implements PasswordHashPort {
  private final PasswordService passwords;

  public PasswordServiceAdapter(PasswordService passwords) {
    this.passwords = passwords;
  }

  @Override
  public String encode(String rawPassword) {
    return passwords.encode(rawPassword);
  }

  @Override
  public boolean matches(String rawPassword, String encodedPassword) {
    return passwords.matches(rawPassword, encodedPassword);
  }
}
