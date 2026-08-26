package ar.com.hexium.hcop.integration.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.integration.domain.AgentAnswer;
import ar.com.hexium.hcop.integration.domain.ChartArtifact;
import ar.com.hexium.hcop.integration.domain.ChatMessage;
import ar.com.hexium.hcop.integration.domain.LlmConfiguration;
import ar.com.hexium.hcop.integration.domain.TableArtifact;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class HttpLlmClientTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final AtomicInteger requestCount = new AtomicInteger();
  private final AtomicReference<String> requestBody = new AtomicReference<>("");
  private final AtomicReference<String> authorization = new AtomicReference<>("");
  private final AtomicReference<ResponseSpec> response = new AtomicReference<>(new ResponseSpec(
      200,
      """
          {"model":"test-model","choices":[{"message":{"content":"OK"}}]}
          """));
  private HttpServer server;
  private HttpLlmClient client;

  @BeforeEach
  void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", exchange -> {
      requestCount.incrementAndGet();
      requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
      ResponseSpec current = response.get();
      byte[] bytes = current.body().getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
      exchange.sendResponseHeaders(current.status(), bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    server.start();
    client = new HttpLlmClient(mapper);
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void rechazaGeminiYElEndpointGoogleSinClaveAntesDeConectar() {
    assertApiKeyRequired(config("gemini", localBaseUrl(), ""));
    assertApiKeyRequired(new LlmConfiguration(
        true,
        "openai-compatible",
        "https://generativelanguage.googleapis.com/v1beta/openai",
        "gemini-3.5-flash",
        0.2,
        1200,
        60_000,
        ""));

    assertThat(requestCount).hasValue(0);
  }

  @Test
  void conservaElMensajeDeErrorCuandoElProveedorRespondeConUnArray() {
    response.set(new ResponseSpec(400, """
        [{"error":{"code":400,"message":"Missing or invalid Authorization header.","status":"INVALID_ARGUMENT"}}]
        """));

    assertThatThrownBy(() -> client.complete(
        config("openai-compatible", localBaseUrl(), "secret"),
        List.of(new ChatMessage("user", "Prueba")),
        true))
        .isInstanceOf(ApiException.class)
        .satisfies(error -> {
          ApiException api = (ApiException) error;
          assertThat(api.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
          assertThat(api.code()).isEqualTo("LLM_UPSTREAM_ERROR");
          assertThat(api.getMessage()).contains("Missing or invalid Authorization header.");
        });
  }

  @Test
  void conservaElMetodoExistenteSinForzarResponseFormat() throws Exception {
    var completion = client.complete(
        config("openai-compatible", localBaseUrl(), ""),
        List.of(new ChatMessage("user", "Consulta tradicional")),
        true);

    assertThat(completion.content()).isEqualTo("OK");
    JsonNode body = mapper.readTree(requestBody.get());
    assertThat(body.has("response_format")).isFalse();
    assertThat(body.has("reasoning_effort")).isFalse();
    assertThat(body.path("max_tokens").asInt()).isEqualTo(1200);
  }

  @Test
  void agregaEsfuerzoBajoYEsquemaEstructuradoFijoParaGemini() throws Exception {
    response.set(new ResponseSpec(200, """
        {"model":"test-model","choices":[{"message":{"content":
          "{\\"answer\\":\\"ok\\",\\"artifacts\\":[],\\"followUps\\":[],\\"highlights\\":[]}"}}]}
        """));

    AgentAnswer answer = client.completeAgentChat(
        config("gemini", localBaseUrl(), "secret"),
        List.of(new ChatMessage("system", "Sistema"), new ChatMessage("user", "Consulta")));

    assertThat(answer.answer()).isEqualTo("ok");
    assertThat(authorization).hasValue("Bearer secret");
    JsonNode body = mapper.readTree(requestBody.get());
    assertThat(body.path("reasoning_effort").asText()).isEqualTo("low");
    assertThat(body.path("max_tokens").asInt()).isEqualTo(4096);
    JsonNode schema = body.path("response_format").path("json_schema");
    assertThat(schema.path("name").asText()).isEqualTo("hcop_agent_response");
    assertThat(schema.path("strict").asBoolean()).isTrue();
    assertThat(schema.path("schema").path("additionalProperties").asBoolean()).isFalse();
    assertThat(schema.path("schema").path("required"))
        .extracting(JsonNode::asText)
        .containsExactly("answer", "artifacts", "followUps", "highlights");
  }

  @Test
  void informaElTruncadoEstructuradoSinEntregarElJsonParcial() {
    response.set(new ResponseSpec(200, """
        {"model":"test-model","choices":[{"finish_reason":"length","message":{"content":"{\\"answer\\":\\"parcial"}}]}
        """));

    assertThatThrownBy(() -> client.completeAgentChat(
        config("openai-compatible", localBaseUrl(), ""),
        List.of(new ChatMessage("user", "Consulta"))))
        .isInstanceOf(ApiException.class)
        .satisfies(error -> {
          ApiException api = (ApiException) error;
          assertThat(api.code()).isEqualTo("LLM_STRUCTURED_RESPONSE_TRUNCATED");
          assertThat(api.getMessage()).contains("truncó").doesNotContain("parcial");
        });
  }

  @Test
  void interpretaJsonCercadoYSanitizaTodosLosElementosEstructurados() {
    stubAgentContent("""
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

    AgentAnswer answer = client.completeAgentChat(
        config("openai-compatible", localBaseUrl(), ""), List.of(new ChatMessage("user", "consulta")));

    assertThat(answer.answer()).isEqualTo("Hallazgo documentado");
    assertThat(answer.artifacts()).hasSize(2);
    TableArtifact table = (TableArtifact) answer.artifacts().get(0);
    assertThat(table.title()).isEqualTo("Evolución");
    assertThat(table.columns()).containsExactly("Fecha", "2", "");
    assertThat(table.rows()).hasSize(1);
    assertThat(table.rows().get(0)).containsExactly("01/08", "4", "");

    ChartArtifact chart = (ChartArtifact) answer.artifacts().get(1);
    assertThat(chart.chartType()).isEqualTo("line");
    assertThat(chart.series()).hasSize(1);
    assertThat(chart.series().get(0).color()).isNull();
    assertThat(chart.series().get(0).points()).hasSize(1);
    assertThat(chart.series().get(0).points().get(0).y()).isEqualTo(3.5);

    assertThat(answer.followUps()).containsExactly("¿Revisar laboratorio?");
    // El adapter extrae los términos sin acotar ni deduplicar — eso es regla de negocio de
    // AgentChatApplicationService (containment contra el texto clínico), no de este parsing.
    assertThat(answer.highlights()).hasSize(1);
    assertThat(answer.highlights().get(0).terms()).containsExactly("neutropenia", "neutropenia", "x");
    assertThat(answer.highlights().get(0).color()).isEqualTo("evil");
  }

  @Test
  void conservaTextoPlanoYNoExponeJsonIncompleto() {
    stubAgentContent("Respuesta tradicional sin estructura", "modelo-proveedor");

    AgentAnswer plain = client.completeAgentChat(
        config("openai-compatible", localBaseUrl(), ""), List.of(new ChatMessage("user", "consulta")));

    assertThat(plain.answer()).isEqualTo("Respuesta tradicional sin estructura");
    assertThat(plain.artifacts()).isEmpty();

    stubAgentContent("{\"artifacts\":[{\"type\":\"table\",\"columns\":[\"A\"],\"rows\":[]}]}", "modelo-proveedor");
    assertThatThrownBy(() -> client.completeAgentChat(
        config("openai-compatible", localBaseUrl(), ""), List.of(new ChatMessage("user", "otra"))))
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
  void aplicaLimitesDeterministasALaRespuestaEstructurada() {
    var root = mapper.createObjectNode();
    root.put("answer", "x".repeat(32_000 + 50));
    var artifacts = root.putArray("artifacts");
    for (int artifactIndex = 0; artifactIndex < 2 + 3; artifactIndex++) {
      var table = artifacts.addObject();
      table.put("type", "table");
      var columns = table.putArray("columns");
      for (int column = 0; column < 15; column++) columns.add("C" + column);
      var rows = table.putArray("rows");
      for (int row = 0; row < 105; row++) {
        var cells = rows.addArray();
        for (int column = 0; column < 15; column++) cells.add("v".repeat(600));
      }
    }
    var followUps = root.putArray("followUps");
    for (int index = 0; index < 12; index++) followUps.add("Seguimiento " + index);
    root.putArray("highlights");
    stubAgentContent(root.toString(), "m".repeat(500));

    AgentAnswer answer = client.completeAgentChat(
        config("openai-compatible", localBaseUrl(), ""), List.of(new ChatMessage("user", "consulta")));

    assertThat(answer.answer()).hasSize(32_000);
    assertThat(answer.model()).hasSize(160);
    assertThat(answer.artifacts()).hasSize(2);
    TableArtifact firstTable = (TableArtifact) answer.artifacts().get(0);
    assertThat(firstTable.columns()).hasSize(8);
    assertThat(firstTable.rows()).hasSize(20);
    assertThat(firstTable.rows().get(0)).hasSize(8);
    assertThat(firstTable.rows().get(0).get(0)).hasSize(500);
    assertThat(answer.followUps()).hasSize(3);
  }

  private void assertApiKeyRequired(LlmConfiguration config) {
    assertThatThrownBy(() -> client.complete(config, List.of(new ChatMessage("user", "Prueba")), true))
        .isInstanceOf(ApiException.class)
        .satisfies(error -> {
          ApiException api = (ApiException) error;
          assertThat(api.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
          assertThat(api.code()).isEqualTo("LLM_API_KEY_REQUIRED");
        });
  }

  private void stubAgentContent(String content, String model) {
    String escaped = mapper.valueToTree(content).toString();
    response.set(new ResponseSpec(200, "{\"model\":\"" + model + "\",\"choices\":[{\"message\":{\"content\":" + escaped + "}}]}"));
  }

  private LlmConfiguration config(String provider, String baseUrl, String apiKey) {
    return new LlmConfiguration(true, provider, baseUrl, "test-model", 0.2, 1200, 10_000, apiKey);
  }

  private String localBaseUrl() {
    return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/v1";
  }

  private record ResponseSpec(int status, String body) {
  }
}
