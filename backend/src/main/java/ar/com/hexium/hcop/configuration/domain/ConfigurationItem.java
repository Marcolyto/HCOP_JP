package ar.com.hexium.hcop.configuration.domain;

import ar.com.hexium.hcop.sharedkernel.domain.Revision;
import java.time.Instant;
import java.util.Objects;

/**
 * Configuración persistida y versionada.
 */
public record ConfigurationItem(
    long id,
    ConfigurationKind kind,
    String key,
    String name,
    String description,
    boolean active,
    ConfigurationDefinition definition,
    Revision revision,
    Instant createdAt,
    Instant updatedAt) {

  public ConfigurationItem {
    if (id < 1) throw new IllegalArgumentException("El identificador de configuración debe ser positivo.");
    Objects.requireNonNull(kind, "kind");
    key = required(key, "La clave de configuración es obligatoria.");
    name = required(name, "El nombre de configuración es obligatorio.");
    description = description == null ? "" : description;
    Objects.requireNonNull(definition, "definition");
    Objects.requireNonNull(revision, "revision");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(updatedAt, "updatedAt");
  }

  private static String required(String value, String message) {
    String normalized = Objects.requireNonNull(value, "value").strip();
    if (normalized.isEmpty()) throw new IllegalArgumentException(message);
    return normalized;
  }
}
