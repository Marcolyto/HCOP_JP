package ar.com.hexium.hcop.integration.infrastructure.web;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.platform.web.ApiException;
import ar.com.hexium.hcop.integration.application.port.in.AgentChatUseCase;
import ar.com.hexium.hcop.integration.application.port.in.AgentChatUseCase.AgentChatCommand;
import ar.com.hexium.hcop.integration.application.port.in.AgentChatUseCase.HistoryEntry;
import ar.com.hexium.hcop.integration.application.port.in.ClinicalSummaryUseCase;
import ar.com.hexium.hcop.integration.application.port.in.ClinicalTimelineExtractionUseCase;
import ar.com.hexium.hcop.integration.application.port.in.LlmConnectionTestUseCase;
import ar.com.hexium.hcop.integration.application.port.in.LlmStatusUseCase;
import ar.com.hexium.hcop.integration.application.port.in.SystemConfigurationUseCase;
import ar.com.hexium.hcop.integration.application.port.in.SystemicFormFillUseCase;
import ar.com.hexium.hcop.integration.domain.AgentAnswer;
import ar.com.hexium.hcop.sharedkernel.domain.UserId;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
public class LlmController {
  private final SystemConfigurationUseCase configuration;
  private final LlmStatusUseCase status;
  private final LlmConnectionTestUseCase connectionTest;
  private final ClinicalTimelineExtractionUseCase timelineExtraction;
  private final ClinicalSummaryUseCase summary;
  private final AgentChatUseCase agentChat;
  private final SystemicFormFillUseCase formFill;
  private final IntegrationJsonMapper json;
  private final AuthContext auth;
  private final ObjectMapper mapper;

  public LlmController(
      SystemConfigurationUseCase configuration,
      LlmStatusUseCase status,
      LlmConnectionTestUseCase connectionTest,
      ClinicalTimelineExtractionUseCase timelineExtraction,
      ClinicalSummaryUseCase summary,
      AgentChatUseCase agentChat,
      SystemicFormFillUseCase formFill,
      IntegrationJsonMapper json,
      AuthContext auth,
      ObjectMapper mapper) {
    this.configuration = configuration;
    this.status = status;
    this.connectionTest = connectionTest;
    this.timelineExtraction = timelineExtraction;
    this.summary = summary;
    this.agentChat = agentChat;
    this.formFill = formFill;
    this.json = json;
    this.auth = auth;
    this.mapper = mapper;
  }

  @GetMapping("/api/config")
  Map<String, Object> config(HttpServletRequest request) {
    auth.requirePermission(request, "section.configuration.view");
    return json.view(configuration.view());
  }

  @PutMapping("/api/config")
  Map<String, Object> updateConfig(@RequestBody JsonNode body, HttpServletRequest request) {
    auth.requirePermission(request, "section.configuration.manage");
    if (!body.path("llm").isObject()) return json.view(configuration.view());
    var result = configuration.update(json.command(body.path("llm")), UserId.of(auth.require(request).userId()));
    return json.view(result);
  }

  @GetMapping("/api/llm/status")
  LlmStatusResponse status(HttpServletRequest request) {
    auth.requirePermission(request, "section.agent.view");
    var result = status.status();
    return new LlmStatusResponse(true, result.enabled(), result.model(), result.provider(), result.configured());
  }

  @PostMapping("/api/llm/test")
  Map<String, Object> test(@RequestBody JsonNode body, HttpServletRequest request) {
    auth.requirePermission(request, "section.configuration.manage");
    var result = connectionTest.test(json.command(body.path("llm")));
    return Map.of("ok", true, "model", result.model(), "response", result.response(), "message", "Conexión correcta");
  }

  @PostMapping("/api/llm/extract-timeline")
  Map<String, Object> timeline(@RequestBody JsonNode body, HttpServletRequest request) {
    auth.requirePermission(request, "section.timeline.view");
    var result = timelineExtraction.extractTimeline(body.path("text").asText(""));
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("ok", true);
    response.put("events", result.events());
    response.put("warnings", result.warnings());
    response.put("model", result.model());
    response.put("extractorVersion", "timeline-java-v1");
    return response;
  }

  @PostMapping("/api/llm/summarize")
  Map<String, Object> summarize(@RequestBody JsonNode body, HttpServletRequest request) {
    auth.requirePermission(request, "section.timeline.view");
    JsonNode inputEvents = body.path("events");
    if (!inputEvents.isArray() || inputEvents.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "No se recibieron eventos.");
    }
    var events = mapper.createArrayNode();
    int count = 0;
    for (JsonNode event : inputEvents) {
      if (count++ >= 250) break;
      events.add(event.deepCopy());
    }
    var result = summary.summarize(body.path("period").asText(""), events.toString());
    return Map.of("ok", true, "model", result.model(), "summary", result.summary());
  }

  @PostMapping("/api/agent/chat")
  AgentChatResponse agent(@RequestBody AgentChatRequest body, HttpServletRequest request) {
    auth.requirePermission(request, "section.agent.view");
    List<HistoryEntry> history = body == null || body.history() == null
        ? List.of()
        : body.history().stream().map(item -> new HistoryEntry(item.role(), item.content())).toList();
    AgentAnswer answer = agentChat.chat(new AgentChatCommand(
        body == null ? "" : body.message(), body == null ? "" : body.clinicalText(), history));
    return new AgentChatResponse(
        true, answer.answer(), answer.model(),
        json.artifacts(answer.artifacts()), answer.followUps(), json.highlights(answer.highlights()));
  }

  @PostMapping("/api/llm/fill-systemic-form")
  Map<String, Object> fillSystemic(@RequestBody JsonNode body, HttpServletRequest request) {
    auth.requirePermission(request, "section.prescriptions.edit");
    String templateId = body.path("templateId").asText("");
    var result = formFill.fill(templateId, body.path("clinicalText").asText(""), body.path("notes").asText(""));
    return Map.of(
        "ok", true, "templateId", result.templateId(),
        "fields", mapper.valueToTree(result.fields()), "model", result.model());
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  @Schema(name = "AgentChatRequest", description = "Consulta acotada para el agente clínico.")
  public record AgentChatRequest(
      @Schema(
          description = "Consulta actual. No debe repetirse como último mensaje user del historial.",
          requiredMode = Schema.RequiredMode.REQUIRED,
          minLength = 1,
          maxLength = 8_000,
          example = "¿Qué toxicidades documentadas requieren seguimiento?")
      String message,
      @Schema(
          description = "Contexto clínico desidentificado. El servidor conserva como máximo 350000 caracteres.",
          maxLength = 350_000)
      String clinicalText,
      @Schema(
          description = "Conversación previa en orden cronológico. Sólo se envían al LLM los últimos 12 mensajes no vacíos.")
      List<AgentHistoryMessage> history,
      @Schema(
          description = "Campo estructurado aceptado por compatibilidad; no se incorpora al prompt en esta versión.")
      JsonNode timelineEvents,
      @Schema(
          description = "Campo aceptado por compatibilidad; no activa otros agentes en esta versión.",
          defaultValue = "false")
      Boolean consultAgents) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  @Schema(name = "AgentHistoryMessage", description = "Mensaje previo de la conversación clínica.")
  public record AgentHistoryMessage(
      @Schema(
          description = "Rol conversacional. Otros valores se normalizan como user por compatibilidad.",
          allowableValues = {"user", "assistant"},
          example = "assistant")
      String role,
      @Schema(
          description = "Contenido previo. Se ignoran valores vacíos y se conservan como máximo 8000 caracteres.",
          maxLength = 8_000)
      String content) {
  }

  @Schema(name = "AgentChatResponse", description = "Respuesta estable del agente clínico.")
  public record AgentChatResponse(
      @Schema(description = "Siempre true cuando la consulta fue completada.", example = "true")
      boolean ok,
      @Schema(description = "Respuesta clínica en español generada por el modelo.")
      String answer,
      @Schema(description = "Modelo informado por el proveedor LLM.")
      String model,
      @Schema(description = "Tablas y gráficos opcionales validados por el servidor.")
      List<JsonNode> artifacts,
      @Schema(description = "Hasta 8 preguntas breves de seguimiento, sin duplicados.")
      List<String> followUps,
      @Schema(description = "Hasta 20 grupos de términos literales para enfocar la historia.")
      List<JsonNode> highlights) {
  }

  @Schema(name = "LlmStatusResponse", description = "Estado no sensible de la integración LLM.")
  public record LlmStatusResponse(
      @Schema(description = "Siempre true cuando el estado pudo consultarse.", example = "true")
      boolean ok,
      @Schema(description = "Indica si el uso del LLM está habilitado.")
      boolean enabled,
      @Schema(description = "Modelo configurado.")
      String model,
      @Schema(description = "Proveedor configurado.")
      String provider,
      @Schema(description = "Indica si existe un endpoint base configurado; no prueba conectividad.")
      boolean configured) {
  }
}
