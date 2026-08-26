package ar.com.hexium.hcop.integration.application.port.in;

import ar.com.hexium.hcop.integration.domain.LlmConfiguration;
import ar.com.hexium.hcop.sharedkernel.domain.UserId;

public interface SystemConfigurationUseCase {

  PublicConfiguration view();

  PublicConfiguration update(LlmConfigurationCommand command, UserId actorId);

  /** Configuración interna con la API key en claro — nunca sale al navegador. */
  LlmConfiguration currentConfiguration();

  /** Fusiona un borrador (sin persistirlo) sobre la configuración actual, ya validado. */
  LlmConfiguration draftConfiguration(LlmConfigurationCommand command);

  /**
   * Espejo tipado del sub-objeto {@code llm} del body — campos ausentes/en blanco quedan
   * {@code null}/{@code ""} y cada método de {@link SystemConfigurationUseCase} decide su propio
   * fallback (a la configuración actual en {@code update}, a valores fijos en
   * {@code draftConfiguration}), igual que el {@code SystemConfigService} original.
   */
  record LlmConfigurationCommand(
      Boolean enabled,
      String provider,
      String baseUrl,
      String model,
      Double temperature,
      Integer maxTokens,
      Integer timeoutMs,
      String apiKeyAction,
      String apiKey,
      boolean apiKeyProvided) {
  }

  record PublicConfiguration(
      boolean enabled,
      String provider,
      String baseUrl,
      String model,
      double temperature,
      int maxTokens,
      int timeoutMs,
      boolean hasApiKey) {
  }
}
