package ar.com.hexium.hcop.configuration.domain;

import ar.com.hexium.hcop.sharedkernel.domain.Revision;
import ar.com.hexium.hcop.sharedkernel.domain.UserId;
import java.time.Instant;
import java.util.Objects;

/**
 * Fotografía inmutable de una revisión anterior de configuración.
 */
public record ConfigurationVersion(
    Revision revision,
    String name,
    String description,
    boolean active,
    ConfigurationDefinition definition,
    UserId changedBy,
    String changedByName,
    Instant createdAt) {

  public ConfigurationVersion {
    Objects.requireNonNull(revision, "revision");
    name = Objects.requireNonNull(name, "name");
    description = description == null ? "" : description;
    Objects.requireNonNull(definition, "definition");
    Objects.requireNonNull(changedBy, "changedBy");
    changedByName = changedByName == null ? "" : changedByName;
    Objects.requireNonNull(createdAt, "createdAt");
  }
}
