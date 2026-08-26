package ar.com.hexium.hcop.configuration.application.port.in;

import ar.com.hexium.hcop.configuration.domain.ConfigurationDefinition;
import ar.com.hexium.hcop.sharedkernel.domain.UserId;
import java.time.Instant;
import java.util.List;

/**
 * API de aplicación del módulo de configuración.
 */
public interface ConfigurationManagementUseCase {

  List<ConfigurationView> list(String kind, boolean includeInactive);

  ConfigurationView create(CreateCommand command);

  ConfigurationView update(UpdateCommand command);

  ConfigurationView archive(String kind, long id, UserId actorId);

  List<ConfigurationVersionView> versions(String kind, long id);

  ConfigurationVersionView version(String kind, long id, long revision);

  record CreateCommand(
      String kind,
      String key,
      String name,
      String description,
      boolean active,
      ConfigurationDefinition definition,
      UserId actorId) {
  }

  record UpdateCommand(
      String kind,
      long id,
      Long expectedRevision,
      String key,
      String name,
      String description,
      Boolean active,
      ConfigurationDefinition definition,
      UserId actorId) {
  }

  record ConfigurationView(
      String id,
      String kind,
      String key,
      String name,
      String description,
      boolean active,
      ConfigurationDefinition definition,
      long revision,
      Instant createdAt,
      Instant updatedAt) {
  }

  record ConfigurationVersionView(
      long revision,
      String name,
      String description,
      boolean active,
      ConfigurationDefinition definition,
      String changedBy,
      String changedByName,
      Instant createdAt) {
  }
}
