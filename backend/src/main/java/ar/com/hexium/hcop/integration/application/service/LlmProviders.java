package ar.com.hexium.hcop.integration.application.service;

import ar.com.hexium.hcop.integration.domain.LlmConfiguration;
import java.net.URI;

/** Lógica pura compartida por la validación de configuración y el adapter HTTP del LLM. */
public final class LlmProviders {
  private LlmProviders() {
  }

  public static boolean configured(LlmConfiguration config) {
    return config != null
        && !config.baseUrl().isBlank()
        && !config.model().isBlank()
        && (!requiresApiKey(config) || !config.apiKey().isBlank());
  }

  public static boolean requiresApiKey(LlmConfiguration config) {
    return config != null && requiresApiKey(config.provider(), config.baseUrl());
  }

  public static boolean requiresApiKey(String providerValue, String baseUrl) {
    String provider = providerValue == null ? "" : providerValue.trim().toLowerCase();
    if (provider.contains("gemini") || provider.contains("google")) return true;
    try {
      String host = URI.create(baseUrl == null ? "" : baseUrl).getHost();
      return host != null && host.equalsIgnoreCase("generativelanguage.googleapis.com");
    } catch (IllegalArgumentException ignored) {
      return false;
    }
  }
}
