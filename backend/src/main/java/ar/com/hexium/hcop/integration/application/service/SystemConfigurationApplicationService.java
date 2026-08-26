package ar.com.hexium.hcop.integration.application.service;

import ar.com.hexium.hcop.integration.application.port.in.SystemConfigurationUseCase;
import ar.com.hexium.hcop.integration.application.port.out.LlmConfigurationStore;
import ar.com.hexium.hcop.integration.domain.LlmConfiguration;
import ar.com.hexium.hcop.sharedkernel.domain.UserId;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

public final class SystemConfigurationApplicationService implements SystemConfigurationUseCase {
  private final LlmConfigurationStore store;

  public SystemConfigurationApplicationService(LlmConfigurationStore store) {
    this.store = store;
  }

  @Override
  public PublicConfiguration view() {
    return publicView(store.find());
  }

  @Override
  public LlmConfiguration currentConfiguration() {
    return store.find();
  }

  @Override
  public PublicConfiguration update(LlmConfigurationCommand command, UserId actorId) {
    LlmConfiguration current = store.find();
    boolean enabled = command.enabled() == null ? current.enabled() : command.enabled();
    String provider = text(command.provider(), current.provider()).toLowerCase(Locale.ROOT);
    if (!Set.of("openai-compatible", "ollama", "lm-studio", "gemini").contains(provider)) {
      throw new IntegrationFailure(IntegrationFailure.Type.INVALID, "Proveedor LLM no válido.");
    }
    String baseUrl = text(command.baseUrl(), current.baseUrl());
    String model = text(command.model(), current.model());
    validateEndpoint(baseUrl);
    if (model.isBlank() || model.length() > 200) {
      throw new IntegrationFailure(IntegrationFailure.Type.INVALID, "El modelo no es válido.");
    }
    double temperature = bounded(
        command.temperature() == null ? current.temperature() : command.temperature(), 0, 2);
    int maxTokens = Math.max(128, Math.min(16000,
        command.maxTokens() == null ? current.maxTokens() : command.maxTokens()));
    int timeoutMs = Math.max(5000, Math.min(180000,
        command.timeoutMs() == null ? current.timeoutMs() : command.timeoutMs()));
    String action = command.apiKeyAction() == null ? "" : command.apiKeyAction().toLowerCase(Locale.ROOT);
    boolean explicitLegacyKey = command.apiKeyProvided() && action.isBlank();
    boolean preserve = Set.of("", "keep", "preserve").contains(action) && !explicitLegacyKey;
    String apiKey = preserve ? current.apiKey() : "";
    if ("remove".equals(action)) preserve = false;
    if ("replace".equals(action) || explicitLegacyKey) {
      String replacement = command.apiKey() == null ? "" : command.apiKey().trim();
      if (replacement.isBlank()) {
        throw new IntegrationFailure(IntegrationFailure.Type.INVALID, "La API key está vacía.");
      }
      apiKey = replacement;
      preserve = false;
    }
    if (enabled) validateRequiredApiKey(provider, baseUrl, apiKey);
    LlmConfiguration updated = new LlmConfiguration(
        enabled, provider, stripSlash(baseUrl), model, temperature, maxTokens, timeoutMs, apiKey);
    return publicView(store.upsert(updated, preserve, actorId.value()));
  }

  @Override
  public LlmConfiguration draftConfiguration(LlmConfigurationCommand command) {
    LlmConfiguration current = store.find();
    String action = command.apiKeyAction() == null ? "" : command.apiKeyAction().toLowerCase(Locale.ROOT);
    String apiKey = switch (action) {
      case "remove" -> "";
      case "replace" -> command.apiKey() == null ? "" : command.apiKey().trim();
      default -> action.isBlank() && command.apiKeyProvided()
          ? (command.apiKey() == null ? "" : command.apiKey().trim()) : current.apiKey();
    };
    String baseUrl = text(command.baseUrl(), current.baseUrl());
    validateEndpoint(baseUrl);
    String provider = text(command.provider(), current.provider());
    String model = text(command.model(), current.model());
    validateRequiredApiKey(provider, baseUrl, apiKey);
    return new LlmConfiguration(
        command.enabled() == null ? true : command.enabled(),
        provider,
        stripSlash(baseUrl),
        model,
        bounded(command.temperature() == null ? current.temperature() : command.temperature(), 0, 2),
        Math.max(128, Math.min(16000, command.maxTokens() == null ? current.maxTokens() : command.maxTokens())),
        Math.max(5000, Math.min(180000, command.timeoutMs() == null ? current.timeoutMs() : command.timeoutMs())),
        apiKey);
  }

  private PublicConfiguration publicView(LlmConfiguration config) {
    return new PublicConfiguration(
        config.enabled(), config.provider(), config.baseUrl(), config.model(), config.temperature(),
        config.maxTokens(), config.timeoutMs(), !config.apiKey().isBlank());
  }

  private void validateRequiredApiKey(String provider, String baseUrl, String apiKey) {
    if (LlmProviders.requiresApiKey(provider, baseUrl) && apiKey.isBlank()) {
      throw new IntegrationFailure(
          IntegrationFailure.Type.INVALID, "Falta configurar la API key de Gemini.", "LLM_API_KEY_REQUIRED");
    }
  }

  private void validateEndpoint(String value) {
    try {
      URI uri = URI.create(value);
      if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
          || uri.getUserInfo() != null || "169.254.169.254".equals(uri.getHost())) throw new IllegalArgumentException();
    } catch (IllegalArgumentException invalid) {
      throw new IntegrationFailure(IntegrationFailure.Type.INVALID, "El endpoint LLM no es una URL HTTP válida.");
    }
  }

  private String stripSlash(String value) {
    return value.replaceAll("/+$", "");
  }

  private String text(String value, String fallback) {
    String normalized = value == null ? "" : value.trim();
    return normalized.isBlank() ? fallback : normalized;
  }

  private double bounded(double value, double minimum, double maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }
}
