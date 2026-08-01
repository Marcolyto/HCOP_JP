package ar.com.hexium.hcop.configuration.infrastructure.configuration;

import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase;
import ar.com.hexium.hcop.configuration.application.port.out.ConfigurationStore;
import ar.com.hexium.hcop.configuration.application.service.ConfigurationApplicationService;
import ar.com.hexium.hcop.sharedkernel.domain.UserId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aplica los límites transaccionales sin contaminar la capa de aplicación con Spring.
 */
@Service
public class TransactionalConfigurationManagement implements ConfigurationManagementUseCase {
  private final ConfigurationApplicationService delegate;

  public TransactionalConfigurationManagement(ConfigurationStore store) {
    this.delegate = new ConfigurationApplicationService(store);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ConfigurationView> list(String kind, boolean includeInactive) {
    return delegate.list(kind, includeInactive);
  }

  @Override
  @Transactional
  public ConfigurationView create(CreateCommand command) {
    return delegate.create(command);
  }

  @Override
  @Transactional
  public ConfigurationView update(UpdateCommand command) {
    return delegate.update(command);
  }

  @Override
  @Transactional
  public ConfigurationView archive(String kind, long id, UserId actorId) {
    return delegate.archive(kind, id, actorId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ConfigurationVersionView> versions(String kind, long id) {
    return delegate.versions(kind, id);
  }

  @Override
  @Transactional(readOnly = true)
  public ConfigurationVersionView version(String kind, long id, long revision) {
    return delegate.version(kind, id, revision);
  }
}
