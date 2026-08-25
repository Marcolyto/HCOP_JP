package ar.com.hexium.hcop.bff.auth;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Único punto que le habla a {@code /api/auth/**} del backend. Nunca lanza por status ≠ 2xx —
 * pass-through literal de status + body, igual que hará {@code ApiProxyController} en F1.3.
 */
@Component
public class BackendAuthClient {

    private final RestClient backend;
    private final ObjectMapper mapper;

    public BackendAuthClient(RestClient backendJsonClient, ObjectMapper mapper) {
        this.backend = backendJsonClient;
        this.mapper = mapper;
    }

    public BackendAuthResponse login(LoginRequest request, String clientAddress, String userAgent) {
        return exchange(backend.post()
                .uri("/api/auth/login")
                .headers(headers -> forwardClientInfo(headers, clientAddress, userAgent))
                .body(request));
    }

    public BackendAuthResponse logout(String backendToken) {
        return exchange(backend.post()
                .uri("/api/auth/logout")
                .headers(headers -> authorize(headers, backendToken)));
    }

    public BackendAuthResponse me(String backendToken) {
        return exchange(backend.get()
                .uri("/api/auth/me")
                .headers(headers -> authorize(headers, backendToken)));
    }

    private void authorize(HttpHeaders headers, String backendToken) {
        if (backendToken != null && !backendToken.isBlank()) headers.setBearerAuth(backendToken);
    }

    private void forwardClientInfo(HttpHeaders headers, String clientAddress, String userAgent) {
        if (clientAddress != null && !clientAddress.isBlank()) headers.set("X-Forwarded-For", clientAddress);
        if (userAgent != null && !userAgent.isBlank()) headers.set(HttpHeaders.USER_AGENT, userAgent);
    }

    private BackendAuthResponse exchange(RestClient.RequestHeadersSpec<?> spec) {
        return spec.exchange((request, response) -> {
            byte[] raw = readAll(response.getBody());
            JsonNode body = raw.length == 0 ? mapper.createObjectNode() : mapper.readValue(raw, JsonNode.class);
            String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
            return new BackendAuthResponse(response.getStatusCode().value(), body, setCookie);
        });
    }

    private byte[] readAll(InputStream body) throws IOException {
        return body.readAllBytes();
    }
}
