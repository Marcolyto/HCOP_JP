package ar.com.hexium.hcop.integration.domain;

public record LlmConfiguration(
    boolean enabled,
    String provider,
    String baseUrl,
    String model,
    double temperature,
    int maxTokens,
    int timeoutMs,
    String apiKey) {
}
