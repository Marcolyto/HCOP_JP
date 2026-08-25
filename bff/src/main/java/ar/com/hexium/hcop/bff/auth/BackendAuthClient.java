package ar.com.hexium.hcop.bff.auth;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Único punto que le habla a {@code /api/auth/**} del backend (modo JWT — F2.7.5). Nunca lanza
 * por status ≠ 2xx — pass-through literal de status + body, igual que hace
 * {@code BackendApiClient} para el resto de {@code /api/**}.
 */
@Component
public class BackendAuthClient {

    private final RestClient backend;
    private final ObjectMapper mapper;

    public BackendAuthClient(@Qualifier("backendJsonClient") RestClient backendJsonClient, ObjectMapper mapper) {
        this.backend = backendJsonClient;
        this.mapper = mapper;
    }

    public BackendAuthResponse login(LoginRequest request, String clientAddress, String userAgent) {
        return exchange(backend.post()
                .uri("/api/auth/login")
                .headers(headers -> forwardClientInfo(headers, clientAddress, userAgent))
                .body(request));
    }

    /** {@code /api/auth/refresh} no está expuesto al navegador — solo lo llama
     * {@code BffSessionFilter} server-to-server para renovar el access token transparentemente. */
    public BackendAuthResponse refresh(String refreshToken, String clientAddress, String userAgent) {
        JsonNode body = mapper.createObjectNode().put("refreshToken", refreshToken);
        return exchange(backend.post()
                .uri("/api/auth/refresh")
                .headers(headers -> forwardClientInfo(headers, clientAddress, userAgent))
                .body(body));
    }

    public BackendAuthResponse logout(String refreshToken) {
        JsonNode body = mapper.createObjectNode().put("refreshToken", refreshToken == null ? "" : refreshToken);
        return exchange(backend.post().uri("/api/auth/logout").body(body));
    }

    public BackendAuthResponse me(String accessToken) {
        return exchange(backend.get()
                .uri("/api/auth/me")
                .headers(headers -> authorize(headers, accessToken)));
    }

    private void authorize(HttpHeaders headers, String accessToken) {
        if (accessToken != null && !accessToken.isBlank()) headers.setBearerAuth(accessToken);
    }

    private void forwardClientInfo(HttpHeaders headers, String clientAddress, String userAgent) {
        if (clientAddress != null && !clientAddress.isBlank()) headers.set("X-Forwarded-For", clientAddress);
        if (userAgent != null && !userAgent.isBlank()) headers.set(HttpHeaders.USER_AGENT, userAgent);
    }

    private BackendAuthResponse exchange(RestClient.RequestHeadersSpec<?> spec) {
        return spec.exchange((request, response) -> {
            byte[] raw = readAll(response.getBody());
            JsonNode body = raw.length == 0 ? mapper.createObjectNode() : mapper.readValue(raw, JsonNode.class);
            return new BackendAuthResponse(response.getStatusCode().value(), body);
        });
    }

    private byte[] readAll(InputStream body) throws IOException {
        return body.readAllBytes();
    }
}
