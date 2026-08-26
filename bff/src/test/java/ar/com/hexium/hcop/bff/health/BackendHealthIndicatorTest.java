package ar.com.hexium.hcop.bff.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.web.client.RestClient;

class BackendHealthIndicatorTest {

    private HttpServer fakeBackend;

    @AfterEach
    void stopFakeBackend() {
        if (fakeBackend != null) fakeBackend.stop(0);
    }

    @Test
    void upCuandoElBackendRespondeUp() throws Exception {
        BackendHealthIndicator indicator = indicatorFor(200, "{\"status\":\"UP\"}");

        Health health = indicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("UP");
    }

    @Test
    void downCuandoElBackendRespondeDown() throws Exception {
        BackendHealthIndicator indicator = indicatorFor(200, "{\"status\":\"DOWN\"}");

        Health health = indicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("DOWN");
    }

    @Test
    void downCuandoElBackendEsInalcanzable() {
        RestClient client = RestClient.builder().baseUrl("http://127.0.0.1:1").build();
        BackendHealthIndicator indicator = new BackendHealthIndicator(client);

        Health health = indicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("DOWN");
    }

    private BackendHealthIndicator indicatorFor(int status, String body) throws Exception {
        fakeBackend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeBackend.createContext("/actuator/health", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        fakeBackend.start();
        int port = fakeBackend.getAddress().getPort();
        RestClient client = RestClient.builder().baseUrl("http://127.0.0.1:" + port).build();
        return new BackendHealthIndicator(client);
    }
}
