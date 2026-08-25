package ar.com.hexium.hcop.integration;

import ar.com.hexium.hcop.common.ApiException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class SystemConfigService {
  private final SystemSettingsRepository settings;
  private final SecretBox secrets;
  private final ObjectMapper mapper;

  public SystemConfigService(
      SystemSettingsRepository settings, SecretBox secrets, ObjectMapper mapper) {
    this.settings = settings;
    this.secrets = secrets;
    this.mapper = mapper;
  }

  public Config internal() {
    var stored = settings.find("llm").orElse(null);
    JsonNode value = stored == null ? defaultLlm() : stored.value();
    return new Config(
        value.path("enabled").asBoolean(false),
        value.path("provider").asText("openai-compatible"),
        value.path("baseUrl").asText("https://generativelanguage.googleapis.com/v1beta/openai"),
        value.path("model").asText("gemini-3.5-flash"),
        value.path("temperature").asDouble(0.2),
        value.path("maxTokens").asInt(1200),
        value.path("timeoutMs").asInt(60000),
        stored == null ? "" : secrets.decrypt(stored.secret()));
  }

  public Map<String, Object> publicView() {
    Config config = internal();
    Map<String, Object> llm = new LinkedHashMap<>();
    llm.put("enabled", config.enabled());
    llm.put("provider", config.provider());
    llm.put("baseUrl", config.baseUrl());
    llm.put("model", config.model());
    llm.put("temperature", config.temperature());
    llm.put("maxTokens", config.maxTokens());
    llm.put("timeoutMs", config.timeoutMs());
    llm.put("hasApiKey", !config.apiKey().isBlank());
    llm.put("lockedFields", java.util.List.of());
    return Map.of(
        "ok", true,
        "llm", llm,
        "ajcc", Map.of("endpoint", "local://ajcc8", "hasApiKey", false, "offline", true));
  }

  @Transactional
  public Map<String, Object> update(JsonNode input, long actorId) {
    JsonNode llmInput = input.path("llm");
    if (!llmInput.isObject()) return publicView();
    Config current = internal();
    boolean enabled = llmInput.path("enabled").asBoolean(current.enabled());
    String provider = text(llmInput, "provider", current.provider()).toLowerCase(Locale.ROOT);
    if (!Set.of("openai-compatible", "ollama", "lm-studio", "gemini").contains(provider)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Proveedor LLM no válido.");
    }
    String baseUrl = text(llmInput, "baseUrl", current.baseUrl());
    String model = text(llmInput, "model", current.model());
    validateEndpoint(baseUrl);
    if (model.isBlank() || model.length() > 200) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "El modelo no es válido.");
    }
    ObjectNode value = mapper.createObjectNode();
    value.put("enabled", enabled);
    value.put("provider", provider);
    value.put("baseUrl", stripSlash(baseUrl));
    value.put("model", model);
    value.put("temperature", bounded(llmInput.path("temperature").asDouble(current.temperature()), 0, 2));
    value.put("maxTokens", Math.max(128, Math.min(16000, llmInput.path("maxTokens").asInt(current.maxTokens()))));
    value.put("timeoutMs", Math.max(5000, Math.min(180000, llmInput.path("timeoutMs").asInt(current.timeoutMs()))));
    String action = llmInput.path("apiKeyAction").asText("").toLowerCase(Locale.ROOT);
    boolean explicitLegacyKey = llmInput.has("apiKey") && action.isBlank();
    boolean preserve = Set.of("", "keep", "preserve").contains(action) && !explicitLegacyKey;
    byte[] encrypted = null;
    String effectiveApiKey = preserve ? current.apiKey() : "";
    if ("remove".equals(action)) preserve = false;
    if ("replace".equals(action) || explicitLegacyKey) {
      String apiKey = llmInput.path("apiKey").asText("").trim();
      if (apiKey.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "La API key está vacía.");
      encrypted = secrets.encrypt(apiKey);
      effectiveApiKey = apiKey;
      preserve = false;
    }
    if (enabled) validateRequiredApiKey(provider, baseUrl, effectiveApiKey);
    settings.upsert("llm", value, encrypted, preserve, actorId);
    return publicView();
  }

  public Config draft(JsonNode input) {
    JsonNode llm = input.path("llm");
    if (!llm.isObject()) return internal();
    Config current = internal();
    String action = llm.path("apiKeyAction").asText("").toLowerCase(Locale.ROOT);
    String apiKey = switch (action) {
      case "remove" -> "";
      case "replace" -> llm.path("apiKey").asText("").trim();
      default -> action.isBlank() && llm.has("apiKey")
          ? llm.path("apiKey").asText("").trim() : current.apiKey();
    };
    String baseUrl = text(llm, "baseUrl", current.baseUrl());
    validateEndpoint(baseUrl);
    String provider = text(llm, "provider", current.provider());
    String model = text(llm, "model", current.model());
    validateRequiredApiKey(provider, baseUrl, apiKey);
    return new Config(
        llm.path("enabled").asBoolean(true),
        provider,
        stripSlash(baseUrl),
        model,
        bounded(llm.path("temperature").asDouble(current.temperature()), 0, 2),
        Math.max(128, Math.min(16000, llm.path("maxTokens").asInt(current.maxTokens()))),
        Math.max(5000, Math.min(180000, llm.path("timeoutMs").asInt(current.timeoutMs()))),
        apiKey);
  }

  private void validateRequiredApiKey(String provider, String baseUrl, String apiKey) {
    if (LlmClient.requiresApiKey(provider, baseUrl) && apiKey.isBlank()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "Falta configurar la API key de Gemini.",
          "LLM_API_KEY_REQUIRED");
    }
  }

  private JsonNode defaultLlm() {
    ObjectNode value = mapper.createObjectNode();
    value.put("enabled", false);
    value.put("provider", "openai-compatible");
    value.put("baseUrl", "https://generativelanguage.googleapis.com/v1beta/openai");
    value.put("model", "gemini-3.5-flash");
    value.put("temperature", 0.2);
    value.put("maxTokens", 1200);
    value.put("timeoutMs", 60000);
    return value;
  }

  private void validateEndpoint(String value) {
    try {
      URI uri = URI.create(value);
      if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
          || uri.getUserInfo() != null || "169.254.169.254".equals(uri.getHost())) throw new IllegalArgumentException();
    } catch (IllegalArgumentException invalid) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "El endpoint LLM no es una URL HTTP válida.");
    }
  }

  private String stripSlash(String value) {
    return value.replaceAll("/+$", "");
  }

  private String text(JsonNode node, String key, String fallback) {
    String value = node.path(key).asText("").trim();
    return value.isBlank() ? fallback : value;
  }

  private double bounded(double value, double minimum, double maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

  public record Config(
      boolean enabled, String provider, String baseUrl, String model, double temperature,
      int maxTokens, int timeoutMs, String apiKey) {
  }
}
