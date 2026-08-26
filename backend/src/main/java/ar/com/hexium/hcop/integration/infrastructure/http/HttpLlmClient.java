package ar.com.hexium.hcop.integration.infrastructure.http;

import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.integration.application.port.out.LlmPort;
import ar.com.hexium.hcop.integration.application.service.LlmProviders;
import ar.com.hexium.hcop.integration.domain.AgentAnswer;
import ar.com.hexium.hcop.integration.domain.ChartArtifact;
import ar.com.hexium.hcop.integration.domain.ChatArtifact;
import ar.com.hexium.hcop.integration.domain.ChatHighlight;
import ar.com.hexium.hcop.integration.domain.ChatMessage;
import ar.com.hexium.hcop.integration.domain.LlmConfiguration;
import ar.com.hexium.hcop.integration.domain.TableArtifact;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Adapter HTTP del {@link LlmPort}. Absorbe, sin cambio de comportamiento, la lógica de
 * {@code LlmClient} (F1-F3.1) y la de saneamiento de {@code LlmController.parseAgentResponse} —
 * ambas son detalle de protocolo/parsing de un servicio externo no confiable, no casos de uso.
 */
@Component
public class HttpLlmClient implements LlmPort {
  static final int DEFAULT_STRUCTURED_MIN_TOKENS = 4096;
  private static final int MAX_AGENT_ANSWER_CHARS = 32_000;
  private static final int MAX_AGENT_ARTIFACTS = 2;
  private static final int MAX_AGENT_FOLLOW_UPS = 3;
  private static final int MAX_AGENT_TITLE_CHARS = 160;
  private static final int MAX_AGENT_COLUMNS = 8;
  private static final int MAX_AGENT_ROWS = 20;
  private static final int MAX_AGENT_CELL_CHARS = 500;
  private static final int MAX_AGENT_SERIES = 2;
  private static final int MAX_AGENT_POINTS = 20;
  private static final int MAX_AGENT_FOLLOW_UP_CHARS = 500;
  private static final Set<String> AGENT_CHART_TYPES = Set.of("line", "bar", "pie");
  private final ObjectMapper mapper;
  private final HttpClient http;

  public HttpLlmClient(ObjectMapper mapper) {
    this.mapper = mapper;
    this.http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
  }

  @Override
  public LlmCompletion complete(LlmConfiguration config, List<ChatMessage> messages, boolean requireEnabled) {
    JsonNode payload = send(config, messages, requireEnabled, null);
    String content = extractContent(payload, ollama(config));
    if (content.isBlank()) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "El servicio LLM devolvió una respuesta vacía.", "LLM_EMPTY_RESPONSE");
    }
    return new LlmCompletion(content, payload.path("model").asText(config.model()));
  }

  @Override
  public AgentAnswer completeAgentChat(LlmConfiguration config, List<ChatMessage> messages) {
    JsonNode payload = send(config, messages, true, agentResponseSchema());
    boolean ollama = ollama(config);
    if (wasTruncated(payload, ollama)) {
      throw new ApiException(
          HttpStatus.BAD_GATEWAY,
          "El servicio LLM truncó la respuesta estructurada. Intente nuevamente.",
          "LLM_STRUCTURED_RESPONSE_TRUNCATED");
    }
    String content = extractContent(payload, ollama);
    if (content.isBlank()) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "El servicio LLM devolvió una respuesta vacía.", "LLM_EMPTY_RESPONSE");
    }
    String model = safeModel(payload.path("model").asText(config.model()));
    return parseAgentResponse(content, model);
  }

  @Override
  public Object parseJson(String content) {
    String value = content == null ? "" : content.trim();
    if (value.startsWith("```")) {
      value = value.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
    }
    try {
      return mapper.convertValue(mapper.readTree(value), Object.class);
    } catch (Exception invalid) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "El LLM no devolvió JSON válido.", "LLM_INVALID_JSON");
    }
  }

  private JsonNode send(LlmConfiguration config, List<ChatMessage> messages, boolean requireEnabled, JsonNode structuredSchema) {
    if (requireEnabled && !config.enabled()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "El servicio LLM está desactivado.", "LLM_DISABLED");
    }
    if (LlmProviders.requiresApiKey(config) && config.apiKey().isBlank()) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE, "Falta configurar la API key de Gemini en Configuración.", "LLM_API_KEY_REQUIRED");
    }
    boolean ollama = ollama(config);
    URI endpoint = URI.create(config.baseUrl() + (ollama ? "/api/chat" : "/chat/completions"));
    ObjectNode body = requestBody(config, messages, ollama, structuredSchema);
    HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
        .timeout(Duration.ofMillis(config.timeoutMs()))
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
    if (!config.apiKey().isBlank()) request.header("Authorization", "Bearer " + config.apiKey());
    try {
      HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      JsonNode payload = mapper.readTree(response.body());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new ApiException(
            HttpStatus.BAD_GATEWAY,
            "El servicio LLM respondió con error: " + upstreamErrorDetail(payload, response.statusCode()),
            "LLM_UPSTREAM_ERROR");
      }
      return payload;
    } catch (ApiException exception) {
      throw exception;
    } catch (java.net.http.HttpTimeoutException timeout) {
      throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "El servicio LLM excedió el tiempo de espera.", "LLM_TIMEOUT");
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new ApiException(
          HttpStatus.BAD_GATEWAY, "La conexión con el servicio LLM fue interrumpida.", "LLM_CONNECTION_INTERRUPTED");
    } catch (Exception exception) {
      throw new ApiException(
          HttpStatus.BAD_GATEWAY,
          "No se pudo conectar con el servicio LLM configurado: " + exception.getMessage(),
          "LLM_CONNECTION_ERROR");
    }
  }

  private boolean ollama(LlmConfiguration config) {
    return "ollama".equalsIgnoreCase(config.provider()) || config.baseUrl().contains(":11434");
  }

  private String extractContent(JsonNode payload, boolean ollama) {
    return ollama
        ? payload.path("message").path("content").asText("")
        : payload.path("choices").path(0).path("message").path("content").asText("");
  }

  private ObjectNode requestBody(LlmConfiguration config, List<ChatMessage> messages, boolean ollama, JsonNode structuredSchema) {
    ObjectNode body = mapper.createObjectNode();
    body.put("model", config.model());
    body.put("stream", false);
    ArrayNode messageNodes = body.putArray("messages");
    messages.forEach(message -> {
      ObjectNode node = messageNodes.addObject();
      node.put("role", message.role());
      node.put("content", message.content());
    });
    int outputTokens = structuredSchema == null ? config.maxTokens() : Math.max(config.maxTokens(), DEFAULT_STRUCTURED_MIN_TOKENS);
    if (ollama) {
      ObjectNode options = body.putObject("options");
      options.put("temperature", config.temperature());
      options.put("num_predict", outputTokens);
    } else {
      body.put("temperature", config.temperature());
      body.put("max_tokens", outputTokens);
    }
    if (LlmProviders.requiresApiKey(config)) body.put("reasoning_effort", "low");
    if (structuredSchema != null) {
      if (ollama) {
        body.set("format", structuredSchema.deepCopy());
      } else {
        ObjectNode responseFormat = body.putObject("response_format");
        responseFormat.put("type", "json_schema");
        ObjectNode jsonSchema = responseFormat.putObject("json_schema");
        jsonSchema.put("name", "hcop_agent_response");
        jsonSchema.put("strict", true);
        jsonSchema.set("schema", structuredSchema.deepCopy());
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
    JsonNode root = payload != null && payload.isArray() && !payload.isEmpty() ? payload.path(0) : payload;
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

  // ---------- Interpretación y saneamiento de la respuesta estructurada del Agente ----------

  private AgentAnswer parseAgentResponse(String raw, String model) {
    JsonNode structured = tryParseAgentJson(raw);
    if (structured == null) {
      if (resemblesStructuredJson(raw)) throw invalidStructuredResponse();
      return new AgentAnswer(limited(raw, MAX_AGENT_ANSWER_CHARS), model, List.of(), List.of(), List.of());
    }
    if (!structured.isObject()) throw invalidStructuredResponse();
    String answer = structuredText(structured.path("answer"), MAX_AGENT_ANSWER_CHARS);
    if (answer.isBlank()) throw invalidStructuredResponse();
    return new AgentAnswer(
        answer, model,
        sanitizeArtifacts(structured.path("artifacts")),
        sanitizeFollowUps(structured.path("followUps")),
        extractHighlights(structured.path("highlights")));
  }

  private ApiException invalidStructuredResponse() {
    return new ApiException(
        HttpStatus.BAD_GATEWAY,
        "El servicio LLM devolvió una respuesta estructurada incompleta. Intente nuevamente.",
        "LLM_INVALID_STRUCTURED_RESPONSE");
  }

  private boolean resemblesStructuredJson(String raw) {
    String value = raw == null ? "" : raw.stripLeading();
    if (value.startsWith("```")) value = value.replaceFirst("(?is)^```(?:json)?\\s*", "").stripLeading();
    return value.startsWith("{") || value.startsWith("[");
  }

  private JsonNode tryParseAgentJson(String raw) {
    String candidate = raw;
    if (candidate.startsWith("```")) {
      candidate = candidate.replaceFirst("(?is)^```(?:json)?\\s*", "").replaceFirst("(?is)\\s*```$", "").trim();
    }
    try {
      return mapper.readTree(candidate);
    } catch (Exception ignored) {
      return null;
    }
  }

  private List<ChatArtifact> sanitizeArtifacts(JsonNode input) {
    if (!input.isArray()) return List.of();
    List<ChatArtifact> result = new ArrayList<>(MAX_AGENT_ARTIFACTS);
    for (JsonNode item : input) {
      if (result.size() >= MAX_AGENT_ARTIFACTS) break;
      if (!item.isObject()) continue;
      ChatArtifact artifact = switch (item.path("type").asText("")) {
        case "table" -> sanitizeTableArtifact(item);
        case "chart" -> sanitizeChartArtifact(item);
        default -> null;
      };
      if (artifact != null) result.add(artifact);
    }
    return List.copyOf(result);
  }

  private TableArtifact sanitizeTableArtifact(JsonNode input) {
    JsonNode sourceColumns = input.path("columns");
    if (!sourceColumns.isArray() || sourceColumns.isEmpty()) return null;
    List<String> columns = new ArrayList<>();
    for (JsonNode column : sourceColumns) {
      if (columns.size() >= MAX_AGENT_COLUMNS) break;
      columns.add(scalarText(column, MAX_AGENT_TITLE_CHARS));
    }
    if (columns.isEmpty()) return null;
    List<List<String>> rows = new ArrayList<>();
    JsonNode sourceRows = input.path("rows");
    if (sourceRows.isArray()) {
      for (JsonNode row : sourceRows) {
        if (rows.size() >= MAX_AGENT_ROWS) break;
        if (!row.isArray()) continue;
        List<String> normalized = new ArrayList<>();
        for (int index = 0; index < columns.size(); index++) {
          normalized.add(index < row.size() ? scalarText(row.path(index), MAX_AGENT_CELL_CHARS) : "");
        }
        rows.add(List.copyOf(normalized));
      }
    }
    return new TableArtifact(structuredText(input.path("title"), MAX_AGENT_TITLE_CHARS), List.copyOf(columns), List.copyOf(rows));
  }

  private ChartArtifact sanitizeChartArtifact(JsonNode input) {
    JsonNode sourceSeries = input.path("series");
    if (!sourceSeries.isArray()) return null;
    String chartType = input.path("chartType").asText("");
    List<ChartArtifact.Series> series = new ArrayList<>();
    for (JsonNode source : sourceSeries) {
      if (series.size() >= MAX_AGENT_SERIES) break;
      if (!source.isObject() || !source.path("points").isArray()) continue;
      List<ChartArtifact.Point> points = new ArrayList<>();
      for (JsonNode point : source.path("points")) {
        if (points.size() >= MAX_AGENT_POINTS) break;
        if (!point.isObject() || !point.path("y").isNumber()) continue;
        double y = point.path("y").asDouble();
        if (!Double.isFinite(y)) continue;
        points.add(new ChartArtifact.Point(
            scalarText(point.path("x"), MAX_AGENT_TITLE_CHARS), y,
            structuredText(point.path("label"), MAX_AGENT_TITLE_CHARS)));
      }
      if (points.isEmpty()) continue;
      String color = source.path("color").asText("");
      series.add(new ChartArtifact.Series(
          structuredText(source.path("name"), MAX_AGENT_TITLE_CHARS),
          color.matches("^#[0-9a-fA-F]{6}$") ? color : null,
          List.copyOf(points)));
    }
    if (series.isEmpty()) return null;
    return new ChartArtifact(
        structuredText(input.path("title"), MAX_AGENT_TITLE_CHARS),
        AGENT_CHART_TYPES.contains(chartType) ? chartType : "line",
        structuredText(input.path("xLabel"), MAX_AGENT_TITLE_CHARS),
        List.copyOf(series));
  }

  private List<String> sanitizeFollowUps(JsonNode input) {
    if (!input.isArray()) return List.of();
    List<String> result = new ArrayList<>(MAX_AGENT_FOLLOW_UPS);
    Set<String> normalized = new java.util.HashSet<>();
    for (JsonNode item : input) {
      if (result.size() >= MAX_AGENT_FOLLOW_UPS) break;
      String value = structuredText(item, MAX_AGENT_FOLLOW_UP_CHARS);
      if (!value.isBlank() && normalized.add(value.toLowerCase(java.util.Locale.ROOT))) result.add(value);
    }
    return List.copyOf(result);
  }

  /** Sin acotar ni validar contra el texto clínico — eso es regla de negocio de la aplicación. */
  private List<ChatHighlight> extractHighlights(JsonNode input) {
    if (!input.isArray()) return List.of();
    List<ChatHighlight> result = new ArrayList<>();
    for (JsonNode item : input) {
      if (!item.isObject() || !item.path("terms").isArray()) continue;
      List<String> terms = new ArrayList<>();
      for (JsonNode term : item.path("terms")) {
        String value = structuredText(term, 4096);
        if (!value.isBlank()) terms.add(value);
      }
      if (terms.isEmpty()) continue;
      result.add(new ChatHighlight(List.copyOf(terms), item.path("color").asText("")));
    }
    return List.copyOf(result);
  }

  private String limited(String value, int maximum) {
    String text = value == null ? "" : value;
    return text.length() <= maximum ? text : text.substring(0, maximum);
  }

  private String structuredText(JsonNode node, int maximum) {
    return node != null && node.isTextual() ? limited(node.asText("").trim(), maximum) : "";
  }

  private String scalarText(JsonNode node, int maximum) {
    if (node == null || node.isNull() || !node.isValueNode()) return "";
    return limited(node.asText("").trim(), maximum);
  }

  private String safeModel(String model) {
    return limited(model == null ? "" : model.trim(), MAX_AGENT_TITLE_CHARS);
  }

  private JsonNode agentResponseSchema() {
    try {
      return mapper.readTree("""
          {
            "type": "object",
            "additionalProperties": false,
            "required": ["answer", "artifacts", "followUps", "highlights"],
            "properties": {
              "answer": {"type": "string"},
              "artifacts": {
                "type": "array",
                "items": {
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["type", "title", "columns", "rows", "chartType", "xLabel", "series"],
                  "properties": {
                    "type": {"type": "string", "enum": ["table", "chart"]},
                    "title": {"type": "string"},
                    "columns": {"type": "array", "items": {"type": "string"}},
                    "rows": {
                      "type": "array",
                      "items": {"type": "array", "items": {"type": "string"}}
                    },
                    "chartType": {"type": "string", "enum": ["", "line", "bar", "pie"]},
                    "xLabel": {"type": "string"},
                    "series": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "additionalProperties": false,
                        "required": ["name", "color", "points"],
                        "properties": {
                          "name": {"type": "string"},
                          "color": {"type": "string"},
                          "points": {
                            "type": "array",
                            "items": {
                              "type": "object",
                              "additionalProperties": false,
                              "required": ["x", "y", "label"],
                              "properties": {
                                "x": {"type": "string"},
                                "y": {"type": "number"},
                                "label": {"type": "string"}
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              },
              "followUps": {"type": "array", "items": {"type": "string"}},
              "highlights": {
                "type": "array",
                "items": {
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["terms", "color"],
                  "properties": {
                    "terms": {"type": "array", "items": {"type": "string"}},
                    "color": {
                      "type": "string",
                      "enum": ["study", "pathology", "chemotherapy", "evolution", "hormone",
                        "systemic", "radiotherapy", "surgery", "immunotherapy", "targeted"]
                    }
                  }
                }
              }
            }
          }
          """);
    } catch (Exception invalidSchema) {
      throw new IllegalStateException("El esquema estructurado del Agente no es válido.", invalidSchema);
    }
  }
}
