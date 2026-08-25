package ar.com.hexium.hcop.integration;

import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.integration.SystemConfigService.Config;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class LlmClient {
  static final int DEFAULT_STRUCTURED_MIN_TOKENS = 4096;
  private final ObjectMapper mapper;
  private final HttpClient http;

  public LlmClient(ObjectMapper mapper) {
    this.mapper = mapper;
    this.http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
  }

  public Completion complete(Config config, List<Message> messages, boolean requireEnabled) {
    return complete(config, messages, requireEnabled, null);
  }

  public Completion complete(
      Config config,
      List<Message> messages,
      boolean requireEnabled,
      StructuredOutput structuredOutput) {
    if (requireEnabled && !config.enabled()) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "El servicio LLM está desactivado.",
          "LLM_DISABLED");
    }
    if (requiresApiKey(config) && config.apiKey().isBlank()) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Falta configurar la API key de Gemini en Configuración.",
          "LLM_API_KEY_REQUIRED");
    }
    boolean ollama = "ollama".equalsIgnoreCase(config.provider())
        || config.baseUrl().contains(":11434");
    URI endpoint = URI.create(config.baseUrl() + (ollama ? "/api/chat" : "/chat/completions"));
    ObjectNode body = requestBody(config, messages, ollama, structuredOutput);
    HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
        .timeout(Duration.ofMillis(config.timeoutMs()))
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
    if (!config.apiKey().isBlank()) request.header("Authorization", "Bearer " + config.apiKey());
    try {
      HttpResponse<String> response = http.send(
          request.build(),
          HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      JsonNode payload = mapper.readTree(response.body());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        String detail = upstreamErrorDetail(payload, response.statusCode());
        throw new ApiException(
            HttpStatus.BAD_GATEWAY,
            "El servicio LLM respondió con error: " + detail,
            "LLM_UPSTREAM_ERROR");
      }
      String content = ollama
          ? payload.path("message").path("content").asText("")
          : payload.path("choices").path(0).path("message").path("content").asText("");
      if (structuredOutput != null && wasTruncated(payload, ollama)) {
        throw new ApiException(
            HttpStatus.BAD_GATEWAY,
            "El servicio LLM truncó la respuesta estructurada. Intente nuevamente.",
            "LLM_STRUCTURED_RESPONSE_TRUNCATED");
      }
      if (content.isBlank()) {
        throw new ApiException(
            HttpStatus.BAD_GATEWAY,
            "El servicio LLM devolvió una respuesta vacía.",
            "LLM_EMPTY_RESPONSE");
      }
      return new Completion(content, payload.path("model").asText(config.model()), payload);
    } catch (ApiException exception) {
      throw exception;
    } catch (java.net.http.HttpTimeoutException timeout) {
      throw new ApiException(
          HttpStatus.GATEWAY_TIMEOUT,
          "El servicio LLM excedió el tiempo de espera.",
          "LLM_TIMEOUT");
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new ApiException(
          HttpStatus.BAD_GATEWAY,
          "La conexión con el servicio LLM fue interrumpida.",
          "LLM_CONNECTION_INTERRUPTED");
    } catch (Exception exception) {
      throw new ApiException(
          HttpStatus.BAD_GATEWAY,
          "No se pudo conectar con el servicio LLM configurado: " + exception.getMessage(),
          "LLM_CONNECTION_ERROR");
    }
  }

  static boolean configured(Config config) {
    return config != null
        && !config.baseUrl().isBlank()
        && !config.model().isBlank()
        && (!requiresApiKey(config) || !config.apiKey().isBlank());
  }

  static boolean requiresApiKey(Config config) {
    if (config == null) return false;
    return requiresApiKey(config.provider(), config.baseUrl());
  }

  static boolean requiresApiKey(String providerValue, String baseUrl) {
    String provider = providerValue == null ? "" : providerValue.trim().toLowerCase();
    if (provider.contains("gemini") || provider.contains("google")) return true;
    try {
      String host = URI.create(baseUrl == null ? "" : baseUrl).getHost();
      return host != null && host.equalsIgnoreCase("generativelanguage.googleapis.com");
    } catch (IllegalArgumentException ignored) {
      return false;
    }
  }

  ObjectNode requestBody(
      Config config,
      List<Message> messages,
      boolean ollama,
      StructuredOutput structuredOutput) {
    ObjectNode body = mapper.createObjectNode();
    body.put("model", config.model());
    body.put("stream", false);
    ArrayNode messageNodes = body.putArray("messages");
    messages.forEach(message -> {
      ObjectNode node = messageNodes.addObject();
      node.put("role", message.role());
      node.put("content", message.content());
    });
    int outputTokens = structuredOutput == null
        ? config.maxTokens()
        : Math.max(config.maxTokens(), structuredOutput.minimumOutputTokens());
    if (ollama) {
      ObjectNode options = body.putObject("options");
      options.put("temperature", config.temperature());
      options.put("num_predict", outputTokens);
    } else {
      body.put("temperature", config.temperature());
      body.put("max_tokens", outputTokens);
    }
    if (requiresApiKey(config)) body.put("reasoning_effort", "low");
    if (structuredOutput != null) {
      if (ollama) {
        body.set("format", structuredOutput.schema().deepCopy());
      } else {
        ObjectNode responseFormat = body.putObject("response_format");
        responseFormat.put("type", "json_schema");
        ObjectNode jsonSchema = responseFormat.putObject("json_schema");
        jsonSchema.put("name", structuredOutput.name());
        jsonSchema.put("strict", true);
        jsonSchema.set("schema", structuredOutput.schema().deepCopy());
      }
    }
    return body;
  }

  private boolean wasTruncated(JsonNode payload, boolean ollama) {
    String reason = ollama
        ? payload.path("done_reason").asText("")
        : payload.path("choices").path(0).path("finish_reason").asText("");
    return "length".equalsIgnoreCase(reason);
  }

  private String upstreamErrorDetail(JsonNode payload, int statusCode) {
    JsonNode root = payload != null && payload.isArray() && !payload.isEmpty()
        ? payload.path(0) : payload;
    if (root != null) {
      JsonNode error = root.path("error");
      String message = error.path("message").asText("").trim();
      if (!message.isBlank()) return message;
      if (error.isTextual() && !error.asText("").isBlank()) return error.asText();
      message = root.path("message").asText("").trim();
      if (!message.isBlank()) return message;
    }
    return "HTTP " + statusCode;
  }

  public JsonNode parseJson(String content) {
    String value = content == null ? "" : content.trim();
    if (value.startsWith("```")) {
      value = value.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
    }
    try {
      return mapper.readTree(value);
    } catch (Exception invalid) {
      throw new ApiException(
          HttpStatus.BAD_GATEWAY,
          "El LLM no devolvió JSON válido.",
          "LLM_INVALID_JSON");
    }
  }

  public record Message(String role, String content) {
  }

  public record StructuredOutput(String name, JsonNode schema, int minimumOutputTokens) {
    public StructuredOutput(String name, JsonNode schema) {
      this(name, schema, DEFAULT_STRUCTURED_MIN_TOKENS);
    }

    public StructuredOutput {
      if (name == null || !name.matches("[A-Za-z0-9_-]{1,64}")) {
        throw new IllegalArgumentException("El nombre del esquema estructurado no es válido.");
      }
      if (schema == null || !schema.isObject()) {
        throw new IllegalArgumentException("El esquema estructurado debe ser un objeto JSON.");
      }
      if (minimumOutputTokens < 128 || minimumOutputTokens > 16_000) {
        throw new IllegalArgumentException("El presupuesto estructurado debe estar entre 128 y 16000 tokens.");
      }
      schema = schema.deepCopy();
    }
  }

  public record Completion(String content, String model, JsonNode raw) {
  }
}
