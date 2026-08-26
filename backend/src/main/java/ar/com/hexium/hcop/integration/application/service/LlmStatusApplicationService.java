package ar.com.hexium.hcop.integration.application.service;

import ar.com.hexium.hcop.integration.application.port.in.LlmStatusUseCase;
import ar.com.hexium.hcop.integration.application.port.in.SystemConfigurationUseCase;
import ar.com.hexium.hcop.integration.domain.LlmConfiguration;

public final class LlmStatusApplicationService implements LlmStatusUseCase {
  private final SystemConfigurationUseCase configuration;

  public LlmStatusApplicationService(SystemConfigurationUseCase configuration) {
    this.configuration = configuration;
  }

  @Override
  public LlmStatus status() {
    LlmConfiguration config = configuration.currentConfiguration();
    return new LlmStatus(config.enabled(), config.model(), config.provider(), LlmProviders.configured(config));
  }
}
