package ar.com.hexium.hcop.integration.application.port.in;

import ar.com.hexium.hcop.integration.application.port.in.SystemConfigurationUseCase.LlmConfigurationCommand;

public interface LlmConnectionTestUseCase {

  LlmTestResult test(LlmConfigurationCommand draft);

  record LlmTestResult(String model, String response) {
  }
}
