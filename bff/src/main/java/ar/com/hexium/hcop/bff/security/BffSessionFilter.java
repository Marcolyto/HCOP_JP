package ar.com.hexium.hcop.bff.security;

import ar.com.hexium.hcop.bff.auth.BackendAuthClient;
import ar.com.hexium.hcop.bff.auth.BackendAuthResponse;
import ar.com.hexium.hcop.bff.auth.BffSession;
import ar.com.hexium.hcop.bff.auth.BffSessionResolver;
import ar.com.hexium.hcop.bff.auth.BffSessionService;
import ar.com.hexium.hcop.bff.auth.SessionCookieFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resuelve la cookie {@code BFF_SESSION} una vez por request y la deja en
 * {@link #SESSION_ATTRIBUTE} — de ahí la leen {@code CurrentSessionArgumentResolver},
 * {@code SessionRequiredFilter} y cualquier controller. Corre para TODO {@code /api/**}, tenga
 * o no sesión: quién exige sesión es {@code SessionRequiredFilter}, más adelante en la cadena.
 *
 * <p><b>Refresh transparente (F2.7.5 — modo JWT):</b> el access token vive poco
 * ({@code HCOP_JWT_ACCESS_MINUTES}, 15 min por default) — a diferencia del refresh "cosmético"
 * de F1 (que solo extendía un TTL de Redis), acá el refresh es una llamada real y síncrona a
 * {@code POST /api/auth/refresh} del backend, que rota el {@code jti} y devuelve un par
 * access+refresh nuevo. Con {@code tryAcquireRefreshLock} (SETNX), de N requests concurrentes
 * que cruzan el umbral a la vez solo uno pega contra el backend; el resto sigue con el access
 * token que ya tenía — si venció mientras tanto, esa request puntual se resuelve con el 401
 * pass-through del backend (no hay retry, es una ventana aceptable). Si el refresh falla (401
 * del backend — sesión revocada o refresh token vencido), la sesión se borra de Redis: la
 * request sigue sin sesión, {@code SessionRequiredFilter} la corta con el 401 propio.
 */
@Component
@Order(30)
public class BffSessionFilter extends OncePerRequestFilter {

    public static final String SESSION_ATTRIBUTE = BffSessionFilter.class.getName() + ".SESSION";

    private static final Duration REFRESH_THRESHOLD = Duration.ofMinutes(2);

    private final BffSessionResolver sessionResolver;
    private final BffSessionService sessions;
    private final SessionCookieFactory cookies;
    private final BackendAuthClient backend;

    public BffSessionFilter(
            BffSessionResolver sessionResolver,
            BffSessionService sessions,
            SessionCookieFactory cookies,
            BackendAuthClient backend) {
        this.sessionResolver = sessionResolver;
        this.sessions = sessions;
        this.cookies = cookies;
        this.backend = backend;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        sessionResolver.resolve(request).ifPresent(session -> {
            BffSession current = maybeRefresh(sessionResolver.sessionId(request), session, request, response);
            if (current != null) request.setAttribute(SESSION_ATTRIBUTE, current);
        });
        chain.doFilter(request, response);
    }

    /** @return la sesión a usar para esta request — la original, la renovada, o {@code null} si
     * la renovación descubrió que la sesión ya no es válida (borrada de Redis en ese caso). */
    private BffSession maybeRefresh(
            String sessionId, BffSession session, HttpServletRequest request, HttpServletResponse response) {
        Duration remaining = Duration.between(Instant.now(), session.accessExpiresAt());
        if (remaining.compareTo(REFRESH_THRESHOLD) >= 0) return session;
        if (!sessions.tryAcquireRefreshLock(sessionId)) return session;

        BackendAuthResponse result = backend.refresh(
                session.refreshToken(), clientAddress(request), request.getHeader(HttpHeaders.USER_AGENT));
        if (result.status() != HttpStatus.OK.value()) {
            sessions.delete(sessionId);
            return null;
        }

        BffSession refreshed = BffSession.from(result.body());
        sessions.replace(sessionId, refreshed);
        Duration cookieTtl = Duration.between(Instant.now(), refreshed.refreshExpiresAt());
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.create(sessionId, cookieTtl, request).toString());
        return refreshed;
    }

    private String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
