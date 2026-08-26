package ar.com.hexium.hcop.integration.application.service;

import ar.com.hexium.hcop.integration.application.port.in.LlmConnectionTestUseCase;
import ar.com.hexium.hcop.integration.application.port.in.SystemConfigurationUseCase;
import ar.com.hexium.hcop.integration.application.port.in.SystemConfigurationUseCase.LlmConfigurationCommand;
import ar.com.hexium.hcop.integration.application.port.out.LlmPort;
import ar.com.hexium.hcop.integration.domain.ChatMessage;
import ar.com.hexium.hcop.integration.domain.LlmConfiguration;
import java.util.List;

public final class LlmConnectionTestApplicationService implements LlmConnectionTestUseCase {
  private final SystemConfigurationUseCase configuration;
  private final LlmPort llm;

  public LlmConnectionTestApplicationService(SystemConfigurationUseCase configuration, LlmPort llm) {
    this.configuration = configuration;
    this.llm = llm;
  }

  @Override
  public LlmTestResult test(LlmConfigurationCommand draft) {
    LlmConfiguration config = configuration.draftConfiguration(draft);
    var response = llm.complete(config, List.of(
        new ChatMessage("system", "Respondé únicamente con la palabra OK."),
        new ChatMessage("user", "Prueba de conexión HCOP JP.")), false);
    return new LlmTestResult(response.model(), response.content().trim());
  }
}
