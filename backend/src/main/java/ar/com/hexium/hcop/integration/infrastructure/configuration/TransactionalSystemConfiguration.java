package ar.com.hexium.hcop.integration.infrastructure.configuration;

import ar.com.hexium.hcop.integration.application.port.in.SystemConfigurationUseCase;
import ar.com.hexium.hcop.integration.application.port.out.LlmConfigurationStore;
import ar.com.hexium.hcop.integration.application.service.SystemConfigurationApplicationService;
import ar.com.hexium.hcop.integration.domain.LlmConfiguration;
import ar.com.hexium.hcop.sharedkernel.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aplica los límites transaccionales sin contaminar la capa de aplicación con Spring.
 */
@Service
public class TransactionalSystemConfiguration implements SystemConfigurationUseCase {
  private final SystemConfigurationApplicationService delegate;

  public TransactionalSystemConfiguration(LlmConfigurationStore store) {
    this.delegate = new SystemConfigurationApplicationService(store);
  }

  @Override
  @Transactional(readOnly = true)
  public PublicConfiguration view() {
    return delegate.view();
  }

  @Override
  @Transactional
  public PublicConfiguration update(LlmConfigurationCommand command, UserId actorId) {
    return delegate.update(command, actorId);
  }

  @Override
  @Transactional(readOnly = true)
  public LlmConfiguration currentConfiguration() {
    return delegate.currentConfiguration();
  }

  @Override
  @Transactional(readOnly = true)
  public LlmConfiguration draftConfiguration(LlmConfigurationCommand command) {
    return delegate.draftConfiguration(command);
  }
}
