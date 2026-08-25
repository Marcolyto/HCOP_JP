package ar.com.hexium.hcop.bff.auth;

import ar.com.hexium.hcop.bff.config.BffProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie {@code BFF_SESSION} que ve el navegador. A diferencia de la cookie de hoy del backend
 * (que solo mira {@code request.isSecure()}), acá {@code secure} también respeta
 * {@code X-Forwarded-Proto} porque el BFF sí vive detrás de nginx/TLS-termination.
 */
@Component
public class SessionCookieFactory {

    private final String cookieName;

    public SessionCookieFactory(BffProperties properties) {
        this.cookieName = properties.sessionCookieName();
    }

    public ResponseCookie create(String sessionId, Duration maxAge, HttpServletRequest request) {
        return build(sessionId, maxAge, request);
    }

    public ResponseCookie expire(HttpServletRequest request) {
        return build("", Duration.ZERO, request);
    }

    private ResponseCookie build(String value, Duration maxAge, HttpServletRequest request) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .sameSite("Strict")
                .secure(isSecure(request))
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private boolean isSecure(HttpServletRequest request) {
        if (request.isSecure()) return true;
        return "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
    }
}
