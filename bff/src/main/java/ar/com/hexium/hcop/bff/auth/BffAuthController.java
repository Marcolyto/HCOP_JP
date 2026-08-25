package ar.com.hexium.hcop.bff.auth;

import ar.com.hexium.hcop.bff.config.BffProperties;
import jakarta.servlet.http.Cookie;
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
 * Token Handler de la sesión actual (F1, sin JWT): el navegador solo ve {@code BFF_SESSION};
 * el token opaco {@code HCOP_SESSION} que hoy emite el backend nunca sale de acá. Contrato de
 * {@code /api/auth/**} idéntico al de hoy — ver {@code base/03-bff.md} y el tracker F1.
 *
 * <p>{@code PUT /api/auth/password} y {@code PUT /api/auth/active-patient} NO viven acá:
 * son pass-through simple y los cubre el proxy genérico en F1.3.
 */
@RestController
@RequestMapping("/api/auth")
public class BffAuthController {

    private static final Duration DEFAULT_SESSION_TTL = Duration.ofMinutes(43_200);

    private final BackendAuthClient backend;
    private final BffSessionService sessions;
    private final SessionCookieFactory cookies;
    private final BffProperties properties;
    private final ObjectMapper mapper;

    public BffAuthController(
            BackendAuthClient backend,
            BffSessionService sessions,
            SessionCookieFactory cookies,
            BffProperties properties,
            ObjectMapper mapper) {
        this.backend = backend;
        this.sessions = sessions;
        this.cookies = cookies;
        this.properties = properties;
        this.mapper = mapper;
    }

    @PostMapping("/login")
    public ResponseEntity<JsonNode> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        BackendAuthResponse result = backend.login(request, clientAddress(servletRequest), servletRequest.getHeader(HttpHeaders.USER_AGENT));
        if (result.status() != HttpStatus.OK.value()) {
            return ResponseEntity.status(result.status()).body(result.body());
        }

        SetCookieParser.ParsedCookie parsed = SetCookieParser
                .parse(result.setCookieHeader(), properties.backendSessionCookieName())
                .orElseThrow(() -> new IllegalStateException(
                        "El backend respondió 200 en /api/auth/login sin emitir la cookie de sesión esperada."));
        Duration ttl = parsed.maxAge() != null ? parsed.maxAge() : DEFAULT_SESSION_TTL;
        String sessionId = sessions.create(new BffSession(parsed.value(), Instant.now().plus(ttl)));
        ResponseCookie cookie = cookies.create(sessionId, ttl, servletRequest);

        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(result.body());
    }

    @PostMapping("/logout")
    public ResponseEntity<JsonNode> logout(HttpServletRequest servletRequest) {
        String sessionId = readCookie(servletRequest, properties.sessionCookieName());
        sessions.find(sessionId).ifPresent(session -> backend.logout(session.backendToken()));
        sessions.delete(sessionId);

        ResponseCookie expired = cookies.expire(servletRequest);
        JsonNode body = mapper.createObjectNode().put("ok", true).put("authenticated", false);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, expired.toString()).body(body);
    }

    @GetMapping("/me")
    public ResponseEntity<JsonNode> me(HttpServletRequest servletRequest) {
        String sessionId = readCookie(servletRequest, properties.sessionCookieName());
        String backendToken = sessions.find(sessionId).map(BffSession::backendToken).orElse(null);
        // Sin sesión (o vencida en Redis), el propio backend ya devuelve 200
        // {ok:true,authenticated:false,...} — no hace falta replicar ese shape acá.
        BackendAuthResponse result = backend.me(backendToken);
        return ResponseEntity.status(result.status()).body(result.body());
    }

    private String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    private String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
