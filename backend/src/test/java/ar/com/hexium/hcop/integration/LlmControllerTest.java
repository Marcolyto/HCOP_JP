package ar.com.hexium.hcop.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.catalog.SystemicFormCatalogService;
import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.integration.LlmClient.Completion;
import ar.com.hexium.hcop.integration.LlmClient.Message;
import ar.com.hexium.hcop.integration.LlmClient.StructuredOutput;
import ar.com.hexium.hcop.integration.LlmController.AgentChatRequest;
import ar.com.hexium.hcop.integration.LlmController.AgentHistoryMessage;
import ar.com.hexium.hcop.integration.SystemConfigService.Config;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class LlmControllerTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private SystemConfigService configuration;
  private LlmClient llm;
  private AuthContext auth;
  private HttpServletRequest request;
  private LlmController controller;
  private Config config;

  @BeforeEach
  void setUp() {
    configuration = mock(SystemConfigService.class);
    llm = mock(LlmClient.class);
    auth = mock(AuthContext.class);
    request = mock(HttpServletRequest.class);
    controller = new LlmController(
        configuration,
        llm,
        mock(SystemicFormCatalogService.class),
        auth,
        mapper);
    config = new Config(
        true,
        "openai-compatible",
        "https://llm.example.test/v1",
        "modelo-clinico",
        0.2,
        1200,
        60_000,
        "secret");
    when(configuration.internal()).thenReturn(config);
  }

  @Test
  void exigePermisoDeAgenteAntesDeConsultarElEstado() {
    ApiException forbidden = new ApiException(HttpStatus.FORBIDDEN, "Sin permiso");
    doThrow(forbidden).when(auth).requirePermission(request, "section.agent.view");

    assertThatThrownBy(() -> controller.status(request)).isSameAs(forbidden);

    verifyNoInteractions(configuration, llm);
  }

  @Test
  void exigePermisoDeAgenteAntesDeEnviarLaConsulta() {
    ApiException forbidden = new ApiException(HttpStatus.FORBIDDEN, "Sin permiso");
    doThrow(forbidden).when(auth).requirePermission(request, "section.agent.view");

    assertThatThrownBy(() -> controller.agent(chat("consulta"), request)).isSameAs(forbidden);

    verifyNoInteractions(configuration, llm);
  }

  @Test
  void conservaElErrorExistenteParaUnaConsultaVacia() {
    assertThatThrownBy(() -> controller.agent(chat(" \n\t "), request))
        .isInstanceOf(ApiException.class)
        .satisfies(error -> {
          ApiException apiError = (ApiException) error;
          assertThat(apiError.status()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(apiError.getMessage()).isEqualTo("Escriba una consulta.");
        });

    verify(configuration, never()).internal();
    verifyNoInteractions(llm);
  }

  @Test
  void rechazaUnaConsultaQueSuperaElLimiteSeguro() {
    String oversized = "x".repeat(LlmController.MAX_AGENT_MESSAGE_CHARS + 1);

    assertThatThrownBy(() -> controller.agent(chat(oversized), request))
        .isInstanceOf(ApiException.class)
        .satisfies(error -> {
          ApiException apiError = (ApiException) error;
          assertThat(apiError.status()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(apiError.getMessage()).contains("8000");
        });

    verify(configuration, never()).internal();
    verifyNoInteractions(llm);
  }

  @Test
  void limitaElHistorialALosMensajesRecientesYAcotaCadaContenido() {
    List<AgentHistoryMessage> history = new ArrayList<>();
    for (int index = 0; index < LlmController.MAX_AGENT_HISTORY_MESSAGES + 3; index++) {
      String content = index == 3
          ? "h-3-" + "x".repeat(LlmController.MAX_AGENT_HISTORY_MESSAGE_CHARS)
          : "h-" + index;
      history.add(new AgentHistoryMessage(index % 2 == 0 ? "user" : "assistant", content));
    }
    stubCompletion("Respuesta acotada", "modelo-respuesta");

    controller.agent(new AgentChatRequest(
        "consulta final", "contexto", history, null, false), request);

    List<Message> sent = capturedMessages();
    assertThat(sent).hasSize(3 + LlmController.MAX_AGENT_HISTORY_MESSAGES + 1);
    assertThat(sent.get(3).content())
        .startsWith("h-3-")
        .hasSize(LlmController.MAX_AGENT_HISTORY_MESSAGE_CHARS);
    assertThat(sent.subList(3, sent.size() - 1))
        .extracting(Message::content)
        .hasSize(LlmController.MAX_AGENT_HISTORY_MESSAGES)
        .endsWith("h-14");
    assertThat(sent.get(sent.size() - 1)).isEqualTo(new Message("user", "consulta final"));
  }

  @Test
  void noDuplicaLaConsultaActualCuandoYaEsElUltimoMensajeDelHistorial() {
    stubCompletion("Respuesta", "modelo-respuesta");
    List<AgentHistoryMessage> history = List.of(
        new AgentHistoryMessage("user", "consulta anterior"),
        new AgentHistoryMessage("assistant", "respuesta anterior"),
        new AgentHistoryMessage("user", "  consulta actual  "));

    controller.agent(new AgentChatRequest(
        "consulta actual", "contexto", history, null, false), request);

    List<Message> sent = capturedMessages();
    assertThat(sent)
        .filteredOn(message -> "user".equals(message.role())
            && "consulta actual".equals(message.content()))
        .hasSize(1);
    assertThat(sent.get(sent.size() - 1)).isEqualTo(new Message("user", "consulta actual"));
  }

  @Test
  void devuelveElContratoTipadoEIgnoraCamposEstructuradosDeCompatibilidad() {
    stubCompletion("Hallazgo documentado", "modelo-proveedor");
    var timelineEvents = mapper.createArrayNode();
    timelineEvents.addObject().put("marker", "NO_ENVIAR_AL_LLM");

    var response = controller.agent(new AgentChatRequest(
        "consulta", "contexto", List.of(), timelineEvents, true), request);

    assertThat(response.ok()).isTrue();
    assertThat(response.answer()).isEqualTo("Hallazgo documentado");
    assertThat(response.model()).isEqualTo("modelo-proveedor");
    assertThat(response.artifacts()).isEmpty();
    assertThat(response.followUps()).isEmpty();
    assertThat(response.highlights()).isEmpty();
    assertThat(capturedMessages())
        .noneMatch(message -> message.content().contains("NO_ENVIAR_AL_LLM"));
    verify(auth).requirePermission(request, "section.agent.view");
  }

  @Test
  void interpretaJsonCercadoYSanitizaTodosLosElementosEstructurados() throws Exception {
    stubCompletion("""
        ```json
        {
          "answer": "  Hallazgo documentado  ",
          "artifacts": [
            {
              "type": "table",
              "title": "  Evolución  ",
              "columns": ["Fecha", 2, {"ignorar": true}],
              "rows": [["01/08", 4, {"ignorar": true}, "extra"], "fila inválida"]
            },
            {
              "type": "chart",
              "title": "Tendencia",
              "chartType": "desconocido",
              "xLabel": "Día",
              "series": [
                {
                  "name": "Serie A",
                  "color": "javascript:alert(1)",
                  "points": [
                    {"x": "D1", "y": 3.5, "label": "válido"},
                    {"x": "D2", "y": "no numérico"}
                  ]
                },
                {"name": "Sin puntos", "points": []}
              ]
            },
            {"type": "html", "content": "<script>alert(1)</script>"}
          ],
          "followUps": [" ¿Revisar laboratorio? ", "¿REVISAR LABORATORIO?", 7, ""],
          "highlights": [
            {"terms": [" neutropenia ", "neutropenia", "x", 9], "color": "evil"},
            {"terms": []}
          ],
          "unknown": {"ignored": true}
        }
        ```
        """, "modelo-proveedor");

    var response = controller.agent(new AgentChatRequest(
        "consulta",
        "Se documentó neutropenia durante el tratamiento.",
        List.of(),
        null,
        false), request);

    assertThat(response.answer()).isEqualTo("Hallazgo documentado");
    assertThat(response.artifacts()).hasSize(2);
    JsonNode table = response.artifacts().get(0);
    assertThat(table.path("type").asText()).isEqualTo("table");
    assertThat(table.path("title").asText()).isEqualTo("Evolución");
    assertThat(table.path("columns")).hasSize(3);
    assertThat(table.path("columns").path(1).asText()).isEqualTo("2");
    assertThat(table.path("columns").path(2).asText()).isEmpty();
    assertThat(table.path("rows")).hasSize(1);
    assertThat(table.path("rows").path(0)).hasSize(3);
    assertThat(table.path("rows").path(0).path(2).asText()).isEmpty();

    JsonNode chart = response.artifacts().get(1);
    assertThat(chart.path("chartType").asText()).isEqualTo("line");
    assertThat(chart.path("series")).hasSize(1);
    assertThat(chart.path("series").path(0).has("color")).isFalse();
    assertThat(chart.path("series").path(0).path("points")).hasSize(1);
    assertThat(chart.path("series").path(0).path("points").path(0).path("y").asDouble())
        .isEqualTo(3.5);

    assertThat(response.followUps()).containsExactly("¿Revisar laboratorio?");
    assertThat(response.highlights()).hasSize(1);
    assertThat(response.highlights().get(0).path("terms")).hasSize(1);
    assertThat(response.highlights().get(0).path("terms").path(0).asText())
        .isEqualTo("neutropenia");
    assertThat(response.highlights().get(0).path("color").asText()).isEqualTo("study");
    assertThat(capturedMessages())
        .anyMatch(item -> "system".equals(item.role())
            && item.content().contains("objeto JSON válido")
            && item.content().contains("followUps"));
  }

  @Test
  void conservaTextoPlanoYNoExponeJsonIncompleto() {
    String plain = "Respuesta tradicional sin estructura";
    stubCompletion(plain, "modelo-proveedor");

    var plainResponse = controller.agent(chat("consulta"), request);

    assertThat(plainResponse.answer()).isEqualTo(plain);
    assertThat(plainResponse.artifacts()).isEmpty();
    assertThat(plainResponse.followUps()).isEmpty();
    assertThat(plainResponse.highlights()).isEmpty();

    when(llm.complete(eq(config), anyList(), eq(true), any(StructuredOutput.class)))
        .thenReturn(new Completion(
            "{\"artifacts\":[{\"type\":\"table\",\"columns\":[\"A\"],\"rows\":[]}]}",
            "modelo-proveedor",
            mapper.createObjectNode()));
    assertThatThrownBy(() -> controller.agent(chat("otra consulta"), request))
        .isInstanceOf(ApiException.class)
        .satisfies(error -> {
          ApiException api = (ApiException) error;
          assertThat(api.code()).isEqualTo("LLM_INVALID_STRUCTURED_RESPONSE");
          assertThat(api.getMessage())
              .contains("respuesta estructurada incompleta")
              .doesNotContain("artifacts", "columns");
        });
  }

  @Test
  void conservaSoloHighlightsLiteralesDelContextoNormalizado() {
    stubCompletion("""
        {
          "answer":"Valores documentados.",
          "artifacts":[],
          "followUps":[],
          "highlights":[
            {"terms":["PSA TOTAL 4,2 ng/mL","PSA total 99 ng/mL"],"color":"study"},
            {"terms":["metástasis ósea"],"color":"pathology"}
          ]
        }
        """, "modelo-proveedor");

    var response = controller.agent(new AgentChatRequest(
        "consulta",
        "Laboratorio: PSA total 4,2 ng/mL.",
        List.of(),
        null,
        false), request);

    assertThat(response.highlights()).hasSize(1);
    assertThat(response.highlights().get(0).path("terms"))
        .extracting(JsonNode::asText)
        .containsExactly("PSA TOTAL 4,2 ng/mL");
    assertThat(response.highlights().get(0).path("color").asText()).isEqualTo("study");
  }

  @Test
  void aplicaLimitesDeterministasALaRespuestaEstructurada() {
    var root = mapper.createObjectNode();
    root.put("answer", "x".repeat(LlmController.MAX_AGENT_ANSWER_CHARS + 50));
    var artifacts = root.putArray("artifacts");
    for (int artifactIndex = 0; artifactIndex < LlmController.MAX_AGENT_ARTIFACTS + 3; artifactIndex++) {
      var table = artifacts.addObject();
      table.put("type", "table");
      var columns = table.putArray("columns");
      for (int column = 0; column < 15; column++) columns.add("C" + column);
      var rows = table.putArray("rows");
      for (int row = 0; row < 105; row++) {
        var cells = rows.addArray();
        for (int column = 0; column < 15; column++) {
          cells.add("v".repeat(600));
        }
      }
    }
    var followUps = root.putArray("followUps");
    for (int index = 0; index < 12; index++) followUps.add("Seguimiento " + index);
    var highlights = root.putArray("highlights");
    for (int index = 0; index < 25; index++) {
      var highlight = highlights.addObject();
      var terms = highlight.putArray("terms");
      for (int term = 0; term < 25; term++) terms.add("término-" + index + "-" + term);
      highlight.put("color", "chemotherapy");
    }
    stubCompletion(root.toString(), "m".repeat(500));

    var response = controller.agent(new AgentChatRequest(
        "consulta", root.path("highlights").toString(), List.of(), null, false), request);

    assertThat(response.answer()).hasSize(LlmController.MAX_AGENT_ANSWER_CHARS);
    assertThat(response.model()).hasSize(160);
    assertThat(response.artifacts()).hasSize(LlmController.MAX_AGENT_ARTIFACTS);
    JsonNode firstTable = response.artifacts().get(0);
    assertThat(firstTable.path("columns")).hasSize(8);
    assertThat(firstTable.path("rows")).hasSize(20);
    assertThat(firstTable.path("rows").path(0)).hasSize(8);
    assertThat(firstTable.path("rows").path(0).path(0).asText()).hasSize(500);
    assertThat(response.followUps()).hasSize(LlmController.MAX_AGENT_FOLLOW_UPS);
    assertThat(response.highlights()).hasSize(LlmController.MAX_AGENT_HIGHLIGHTS);
    assertThat(response.highlights().get(0).path("terms")).hasSize(5);
  }

  @Test
  void aceptaCamposDesconocidosSinRomperElContratoCompatible() throws Exception {
    AgentChatRequest mapped = mapper.readValue("""
        {
          "message": "consulta",
          "timelineEvents": [{"date": "2026-08-01"}],
          "consultAgents": false,
          "legacyField": {"stillAccepted": true}
        }
        """, AgentChatRequest.class);

    assertThat(mapped.message()).isEqualTo("consulta");
    assertThat(mapped.timelineEvents()).isNotNull();
    assertThat(mapped.consultAgents()).isFalse();
  }

  @Test
  void devuelveElEstadoTipadoSinExponerLaConfiguracionSensible() {
    var response = controller.status(request);

    assertThat(response.ok()).isTrue();
    assertThat(response.enabled()).isTrue();
    assertThat(response.model()).isEqualTo("modelo-clinico");
    assertThat(response.provider()).isEqualTo("openai-compatible");
    assertThat(response.configured()).isTrue();
    verify(auth).requirePermission(request, "section.agent.view");
  }

  @Test
  void informaNoConfiguradoCuandoGeminiNoTieneApiKey() {
    Config geminiWithoutKey = new Config(
        true,
        "gemini",
        "https://generativelanguage.googleapis.com/v1beta/openai",
        "gemini-3.5-flash",
        0.2,
        1200,
        60_000,
        "");
    when(configuration.internal()).thenReturn(geminiWithoutKey);

    var response = controller.status(request);

    assertThat(response.enabled()).isTrue();
    assertThat(response.configured()).isFalse();
  }

  @Test
  void solicitaRespuestaEstructuradaCompletaYProhibeTablasMarkdown() {
    stubCompletion("Respuesta", "modelo-respuesta");

    controller.agent(chat("Armame una tabla de PSA"), request);

    @SuppressWarnings("rawtypes")
    ArgumentCaptor<List> messages = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<StructuredOutput> structured = ArgumentCaptor.forClass(StructuredOutput.class);
    verify(llm).complete(eq(config), messages.capture(), eq(true), structured.capture());

    assertThat((List<Message>) messages.getValue())
        .anyMatch(message -> message.content().contains("No uses tablas Markdown")
            && message.content().contains("20 puntos")
            && message.content().contains("No clasifiques valores"));
    StructuredOutput output = structured.getValue();
    assertThat(output.name()).isEqualTo("hcop_agent_response");
    assertThat(output.minimumOutputTokens()).isEqualTo(4096);
    JsonNode schema = output.schema();
    assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
    assertThat(schema.path("required"))
        .extracting(JsonNode::asText)
        .containsExactly("answer", "artifacts", "followUps", "highlights");
    JsonNode artifact = schema.path("properties").path("artifacts").path("items");
    assertThat(artifact.has("oneOf")).isFalse();
    assertThat(artifact.path("required"))
        .extracting(JsonNode::asText)
        .containsExactly("type", "title", "columns", "rows", "chartType", "xLabel", "series");
    assertThat(schema.toString()).doesNotContain("pattern", "maxLength");
  }

  @Test
  void propagaSinTraducirLosErroresExistentesDelClienteLlm() {
    ApiException upstream = new ApiException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "El servicio LLM está desactivado.",
        "LLM_DISABLED");
    when(llm.complete(eq(config), anyList(), eq(true), any(StructuredOutput.class)))
        .thenThrow(upstream);

    assertThatThrownBy(() -> controller.agent(chat("consulta"), request)).isSameAs(upstream);
  }

  private AgentChatRequest chat(String message) {
    return new AgentChatRequest(message, "", List.of(), null, false);
  }

  private void stubCompletion(String content, String model) {
    when(llm.complete(eq(config), anyList(), eq(true), any(StructuredOutput.class)))
        .thenReturn(new Completion(content, model, mapper.createObjectNode()));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private List<Message> capturedMessages() {
    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(llm).complete(eq(config), captor.capture(), eq(true), any(StructuredOutput.class));
    return (List<Message>) captor.getValue();
  }
}
