package ar.com.hexium.hcop.configuration.application.port.out;

import ar.com.hexium.hcop.configuration.domain.ConfigurationDefinition;
import ar.com.hexium.hcop.configuration.domain.ConfigurationItem;
import ar.com.hexium.hcop.configuration.domain.ConfigurationKind;
import ar.com.hexium.hcop.configuration.domain.ConfigurationVersion;
import ar.com.hexium.hcop.sharedkernel.domain.Revision;
import ar.com.hexium.hcop.sharedkernel.domain.UserId;
import java.util.List;
import java.util.Optional;

/**
 * Persistencia requerida por los casos de uso de configuración.
 */
public interface ConfigurationStore {

  List<ConfigurationItem> list(ConfigurationKind kind, boolean includeInactive);

  Optional<ConfigurationItem> find(long id, ConfigurationKind kind);

  Optional<ConfigurationItem> findByKey(ConfigurationKind kind, String key);

  ConfigurationItem insert(NewItem item);

  Optional<ConfigurationItem> update(ItemUpdate update);

  List<ConfigurationVersion> versions(long itemId, ConfigurationKind kind);

  record NewItem(
      ConfigurationKind kind,
      String key,
      String name,
      String description,
      boolean active,
      ConfigurationDefinition definition,
      UserId actorId) {
  }

  record ItemUpdate(
      long id,
      ConfigurationKind kind,
      Revision expectedRevision,
      String key,
      String name,
      String description,
      boolean active,
      ConfigurationDefinition definition,
      UserId actorId) {
  }
}
