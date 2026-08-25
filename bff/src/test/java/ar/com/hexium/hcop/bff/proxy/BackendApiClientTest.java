package ar.com.hexium.hcop.bff.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.hexium.hcop.bff.config.BffProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.RestClient;

/**
 * Contra un servidor HTTP real (JDK {@code com.sun.net.httpserver}, sin dependencias nuevas) en
 * vez de mockear {@code RestClient} — la garantía que importa acá es sobre bytes en el cable:
 * URI cruda, headers, streaming, no sobre cómo se llama internamente a Spring.
 */
class BackendApiClientTest {

    private HttpServer fakeBackend;
    private BackendApiClient client;

    @BeforeEach
    void startFakeBackend() throws IOException {
        fakeBackend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeBackend.start();
        int port = fakeBackend.getAddress().getPort();

        HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));
        RestClient restClient = RestClient.builder().requestFactory(factory).build();
        BffProperties properties = new BffProperties("http://127.0.0.1:" + port, "BFF_SESSION", "HCOP_SESSION");
        client = new BackendApiClient(restClient, properties);
    }

    @AfterEach
    void stopFakeBackend() {
        fakeBackend.stop(0);
    }

    @Test
    void preservaLaUriCrudaConSegmentosUrlEncodeados() throws IOException {
        String[] capturedPath = new String[1];
        fakeBackend.createContext("/api/clinical/application-workflows", exchange -> {
            capturedPath[0] = exchange.getRequestURI().getRawPath();
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/api/clinical/application-workflows/21976/tx%2017%2F165/4/8/preparation-label");
        MockHttpServletResponse response = new MockHttpServletResponse();

        client.forward(request, response, null);

        assertThat(capturedPath[0]).isEqualTo(
                "/api/clinical/application-workflows/21976/tx%2017%2F165/4/8/preparation-label");
    }

    @Test
    void agregaAuthorizationBearerYCorrelationIdYSacaLaCookieDelNavegador() throws IOException {
        String[] authHeader = new String[1];
        String[] cookieHeader = new String[1];
        String[] correlationHeader = new String[1];
        fakeBackend.createContext("/api/clinical/status", exchange -> {
            authHeader[0] = exchange.getRequestHeaders().getFirst("Authorization");
            cookieHeader[0] = exchange.getRequestHeaders().getFirst("Cookie");
            correlationHeader[0] = exchange.getRequestHeaders().getFirst("X-Correlation-Id");
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clinical/status");
        request.addHeader("Cookie", "BFF_SESSION=should-never-cross");
        MockHttpServletResponse response = new MockHttpServletResponse();

        client.forward(request, response, "backend-token-xyz");

        assertThat(authHeader[0]).isEqualTo("Bearer backend-token-xyz");
        assertThat(cookieHeader[0]).isNull();
        assertThat(correlationHeader[0]).isNotBlank();
    }

    @Test
    void filtraSetCookieDeLaRespuestaYPreservaElRestoDeHeaders() throws IOException {
        fakeBackend.createContext("/api/clinical/status", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "HCOP_SESSION=leaked; Path=/");
            exchange.getResponseHeaders().add("ETag", "\"abc123\"");
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clinical/status");
        MockHttpServletResponse response = new MockHttpServletResponse();

        client.forward(request, response, null);

        assertThat(response.getHeader("Set-Cookie")).isNull();
        assertThat(response.getHeader("ETag")).isEqualTo("\"abc123\"");
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("{\"ok\":true}");
    }

    @Test
    void hacePassThroughLiteralDeUnStatusDeErrorConSuBody() throws IOException {
        fakeBackend.createContext("/api/hc", exchange -> {
            byte[] body = "{\"ok\":false,\"code\":\"VERSION_CONFLICT\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(409, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/hc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        client.forward(request, response, null);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo("{\"ok\":false,\"code\":\"VERSION_CONFLICT\"}");
    }

    @Test
    void streameaElBodyDeLaRequestSinMaterializarloEntero() throws IOException {
        byte[][] receivedBody = new byte[1][];
        fakeBackend.createContext("/api/media/studies", exchange -> {
            receivedBody[0] = exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(201, 0);
            exchange.close();
        });
        byte[] payload = "contenido-del-estudio-simulado".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/media/studies");
        request.setContent(payload);
        MockHttpServletResponse response = new MockHttpServletResponse();

        client.forward(request, response, null);

        assertThat(receivedBody[0]).isEqualTo(payload);
        assertThat(response.getStatus()).isEqualTo(201);
    }

    @Test
    void unBackendInalcanzableSeTraduceEnProxyExceptionUnreachable() {
        BffProperties properties = new BffProperties("http://127.0.0.1:1", "BFF_SESSION", "HCOP_SESSION");
        HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(2));
        RestClient restClient = RestClient.builder().requestFactory(factory).build();
        BackendApiClient unreachableClient = new BackendApiClient(restClient, properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clinical/status");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> unreachableClient.forward(request, response, null))
                .isInstanceOf(ProxyException.class)
                .extracting(ex -> ((ProxyException) ex).kind())
                .isEqualTo(ProxyException.Kind.UNREACHABLE);
    }
}
