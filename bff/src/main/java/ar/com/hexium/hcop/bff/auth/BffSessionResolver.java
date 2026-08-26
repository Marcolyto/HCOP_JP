package ar.com.hexium.hcop.bff.auth;

import ar.com.hexium.hcop.bff.config.BffProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Lee la cookie {@code BFF_SESSION} y resuelve la {@link BffSession} en Redis. Lo comparten
 * {@code BffAuthController} y {@code ApiProxyController} — en F1.4 lo va a reemplazar
 * {@code BffSessionFilter} (puebla un atributo de request una sola vez por request en vez de
 * pegarle a Redis en cada controller), sin cambiar el contrato hacia afuera.
 */
@Component
public class BffSessionResolver {

    private final BffSessionService sessions;
    private final String cookieName;

    public BffSessionResolver(BffSessionService sessions, BffProperties properties) {
        this.sessions = sessions;
        this.cookieName = properties.sessionCookieName();
    }

    public Optional<BffSession> resolve(HttpServletRequest request) {
        String sessionId = readCookie(request);
        return sessionId == null ? Optional.empty() : sessions.find(sessionId);
    }

    public String sessionId(HttpServletRequest request) {
        return readCookie(request);
    }

    private String readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
