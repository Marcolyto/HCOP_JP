package ar.com.hexium.hcop.auth.application.port.out;

/** Generación y huella persistible de tokens de sesión. */
public interface SessionTokenPort {
  String create();

  String fingerprint(String rawToken);
}
