package ar.com.hexium.hcop.bff.auth;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

/**
 * Lo que el BFF guarda en Redis por sesión (F2.7.5 — modo JWT): el par access+refresh que emitió
 * el backend, y cuándo vence cada uno. Nada de datos de usuario/permisos — esos viajan en el
 * propio access token (claims, ver {@code TokenIssuer} del backend) o se piden en vivo a
 * {@code /api/auth/me} cuando hace falta el shape completo.
 */
public record BffSession(String accessToken, Instant accessExpiresAt, String refreshToken, Instant refreshExpiresAt) {

    /** Parsea el body de {@code /api/auth/login} o {@code /api/auth/refresh} del backend — mismo
     * shape en los dos. Lo usan {@code BffAuthController} (login) y {@code BffSessionFilter}
     * (refresh transparente), sin duplicar el parseo. */
    public static BffSession from(JsonNode body) {
        Instant now = Instant.now();
        return new BffSession(
                body.path("accessToken").asText(""),
                now.plusSeconds(body.path("expiresIn").asLong(0)),
                body.path("refreshToken").asText(""),
                now.plusSeconds(body.path("refreshExpiresIn").asLong(0)));
    }
}
