package ar.com.hexium.hcop.bff.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Token Handler de la sesión actual (F2.7.5 — modo JWT): el navegador solo ve
 * {@code BFF_SESSION}; el par access+refresh que emite el backend nunca sale de acá — ni
 * siquiera en el body de {@code /login}, que se re-arma a partir de {@code session} (el mismo
 * shape de siempre) descartando {@code accessToken}/{@code refreshToken} del body del backend.
 *
 * <p>{@code PUT /api/auth/password} y {@code PUT /api/auth/active-patient} NO viven acá:
 * son pass-through simple y los cubre el proxy genérico ({@code ApiProxyController}).
 * {@code POST /api/auth/refresh} tampoco — no está expuesto al navegador, lo maneja
 * {@code BffSessionFilter} server-to-server.
 */
@RestController
@RequestMapping("/api/auth")
public class BffAuthController {

    private final BackendAuthClient backend;
    private final BffSessionService sessions;
    private final BffSessionResolver sessionResolver;
    private final SessionCookieFactory cookies;
    private final ObjectMapper mapper;

    public BffAuthController(
            BackendAuthClient backend,
            BffSessionService sessions,
            BffSessionResolver sessionResolver,
            SessionCookieFactory cookies,
            ObjectMapper mapper) {
        this.backend = backend;
        this.sessions = sessions;
        this.sessionResolver = sessionResolver;
        this.cookies = cookies;
        this.mapper = mapper;
    }

    @PostMapping("/login")
    public ResponseEntity<JsonNode> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        BackendAuthResponse result = backend.login(request, clientAddress(servletRequest), servletRequest.getHeader(HttpHeaders.USER_AGENT));
        if (result.status() != HttpStatus.OK.value()) {
            return ResponseEntity.status(result.status()).body(result.body());
        }

        BffSession session = BffSession.from(result.body());
        String sessionId = sessions.create(session);
        Duration cookieTtl = Duration.between(Instant.now(), session.refreshExpiresAt());
        ResponseCookie cookie = cookies.create(sessionId, cookieTtl, servletRequest);

        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(result.body().path("session"));
    }

    @PostMapping("/logout")
    public ResponseEntity<JsonNode> logout(HttpServletRequest servletRequest) {
        sessionResolver.resolve(servletRequest).ifPresent(session -> backend.logout(session.refreshToken()));
        sessions.delete(sessionResolver.sessionId(servletRequest));

        ResponseCookie expired = cookies.expire(servletRequest);
        JsonNode body = mapper.createObjectNode().put("ok", true).put("authenticated", false);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, expired.toString()).body(body);
    }

    @GetMapping("/me")
    public ResponseEntity<JsonNode> me(HttpServletRequest servletRequest) {
        String accessToken = sessionResolver.resolve(servletRequest).map(BffSession::accessToken).orElse(null);
        // Sin sesión (o vencida en Redis), el propio backend ya devuelve 200
        // {ok:true,authenticated:false,...} — no hace falta replicar ese shape acá.
        BackendAuthResponse result = backend.me(accessToken);
        return ResponseEntity.status(result.status()).body(result.body());
    }

    private String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
