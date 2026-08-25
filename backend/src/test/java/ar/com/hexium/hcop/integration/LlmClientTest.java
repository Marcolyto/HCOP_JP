package ar.com.hexium.hcop.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.integration.LlmClient.Message;
import ar.com.hexium.hcop.integration.LlmClient.StructuredOutput;
import ar.com.hexium.hcop.integration.SystemConfigService.Config;
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

class LlmClientTest {
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
  private LlmClient client;

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
    client = new LlmClient(mapper);
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void rechazaGeminiYElEndpointGoogleSinClaveAntesDeConectar() {
    assertApiKeyRequired(config("gemini", localBaseUrl(), ""));
    assertApiKeyRequired(new Config(
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
        List.of(new Message("user", "Prueba")),
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
  void agregaEsfuerzoBajoYResponseFormatEstructuradoParaGemini() throws Exception {
    JsonNode schema = mapper.readTree("""
        {"type":"object","required":["answer"],"properties":{"answer":{"type":"string"}}}
        """);

    var completion = client.complete(
        config("gemini", localBaseUrl(), "secret"),
        List.of(new Message("system", "Sistema"), new Message("user", "Consulta")),
        true,
        new StructuredOutput("hcop_agent_response", schema));

    assertThat(completion.content()).isEqualTo("OK");
    assertThat(authorization).hasValue("Bearer secret");
    JsonNode body = mapper.readTree(requestBody.get());
    assertThat(body.path("reasoning_effort").asText()).isEqualTo("low");
    assertThat(body.path("max_tokens").asInt()).isEqualTo(4096);
    assertThat(body.path("response_format").path("type").asText()).isEqualTo("json_schema");
    assertThat(body.path("response_format").path("json_schema").path("name").asText())
        .isEqualTo("hcop_agent_response");
    assertThat(body.path("response_format").path("json_schema").path("strict").asBoolean())
        .isTrue();
    assertThat(body.path("response_format").path("json_schema").path("schema"))
        .isEqualTo(schema);
  }

  @Test
  void conservaElMetodoExistenteSinForzarResponseFormat() throws Exception {
    var completion = client.complete(
        config("openai-compatible", localBaseUrl(), ""),
        List.of(new Message("user", "Consulta tradicional")),
        true);

    assertThat(completion.content()).isEqualTo("OK");
    JsonNode body = mapper.readTree(requestBody.get());
    assertThat(body.has("response_format")).isFalse();
    assertThat(body.has("reasoning_effort")).isFalse();
    assertThat(body.path("max_tokens").asInt()).isEqualTo(1200);
  }

  @Test
  void informaElTruncadoEstructuradoSinEntregarElJsonParcial() throws Exception {
    response.set(new ResponseSpec(200, """
        {"model":"test-model","choices":[{"finish_reason":"length","message":{"content":"{\\\"answer\\\":\\\"parcial"}}]}
        """));
    JsonNode schema = mapper.readTree("""
        {"type":"object","properties":{"answer":{"type":"string"}}}
        """);

    assertThatThrownBy(() -> client.complete(
        config("openai-compatible", localBaseUrl(), ""),
        List.of(new Message("user", "Consulta")),
        true,
        new StructuredOutput("hcop_agent_response", schema)))
        .isInstanceOf(ApiException.class)
        .satisfies(error -> {
          ApiException api = (ApiException) error;
          assertThat(api.code()).isEqualTo("LLM_STRUCTURED_RESPONSE_TRUNCATED");
          assertThat(api.getMessage()).contains("truncó").doesNotContain("parcial");
        });
  }

  private void assertApiKeyRequired(Config config) {
    assertThatThrownBy(() -> client.complete(
        config,
        List.of(new Message("user", "Prueba")),
        true))
        .isInstanceOf(ApiException.class)
        .satisfies(error -> {
          ApiException api = (ApiException) error;
          assertThat(api.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
          assertThat(api.code()).isEqualTo("LLM_API_KEY_REQUIRED");
        });
  }

  private Config config(String provider, String baseUrl, String apiKey) {
    return new Config(true, provider, baseUrl, "test-model", 0.2, 1200, 10_000, apiKey);
  }

  private String localBaseUrl() {
    return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/v1";
  }

  private record ResponseSpec(int status, String body) {
  }
}
