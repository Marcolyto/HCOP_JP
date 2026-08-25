package ar.com.hexium.hcop.configuration.application.port.out;

/**
 * El almacenamiento rechazó una clave duplicada para la misma familia.
 */
public final class ConfigurationKeyConflictException extends RuntimeException {
  public ConfigurationKeyConflictException(Throwable cause) {
    super("La clave de configuración ya existe.", cause);
  }
}
