package ar.com.hexium.hcop.auth.application.port.out;

/** Hash de credenciales aislado de los casos de uso para permitir evolución del algoritmo. */
public interface PasswordHashPort {
  String encode(String rawPassword);

  boolean matches(String rawPassword, String encodedPassword);
}
