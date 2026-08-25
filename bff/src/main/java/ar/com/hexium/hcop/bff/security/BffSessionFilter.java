package ar.com.hexium.hcop.bff.security;

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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resuelve la cookie {@code BFF_SESSION} una vez por request y la deja en
 * {@link #SESSION_ATTRIBUTE} — de ahí la leen {@code CurrentSessionArgumentResolver},
 * {@code SessionRequiredFilter} y cualquier controller. Corre para TODO {@code /api/**}, tenga
 * o no sesión: quién exige sesión es {@code SessionRequiredFilter}, más adelante en la cadena.
 *
 * <p>Refresh transparente: si a la sesión le queda menos de {@link #REFRESH_THRESHOLD}, la
 * extiende de vuelta a {@link #DEFAULT_SESSION_TTL} (Redis + cookie del navegador). Con
 * {@code tryAcquireRefreshLock} (SETNX), de N requests concurrentes que cruzan el umbral a la
 * vez solo uno hace el refresh — no hay carrera de escrituras redundantes a Redis. El backend
 * sigue siendo la autoridad real: su {@code local_sessions.expires_at} no se toca acá, así que
 * en el peor caso esta sesión "sobrevive" en el BFF un poco más de lo que el backend acepta, y
 * el próximo proxy a {@code /api/**} vuelve con el 401 real del backend igual.
 */
@Component
@Order(30)
public class BffSessionFilter extends OncePerRequestFilter {

    public static final String SESSION_ATTRIBUTE = BffSessionFilter.class.getName() + ".SESSION";

    private static final Duration DEFAULT_SESSION_TTL = Duration.ofMinutes(43_200);
    private static final Duration REFRESH_THRESHOLD = Duration.ofDays(1);

    private final BffSessionResolver sessionResolver;
    private final BffSessionService sessions;
    private final SessionCookieFactory cookies;

    public BffSessionFilter(BffSessionResolver sessionResolver, BffSessionService sessions, SessionCookieFactory cookies) {
        this.sessionResolver = sessionResolver;
        this.sessions = sessions;
        this.cookies = cookies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        sessionResolver.resolve(request).ifPresent(session -> {
            request.setAttribute(SESSION_ATTRIBUTE, session);
            maybeRefresh(sessionResolver.sessionId(request), session, request, response);
        });
        chain.doFilter(request, response);
    }

    private void maybeRefresh(String sessionId, BffSession session, HttpServletRequest request, HttpServletResponse response) {
        Duration remaining = Duration.between(Instant.now(), session.expiresAt());
        if (remaining.compareTo(REFRESH_THRESHOLD) >= 0) return;
        if (!sessions.tryAcquireRefreshLock(sessionId)) return;

        sessions.refresh(sessionId, DEFAULT_SESSION_TTL);
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.create(sessionId, DEFAULT_SESSION_TTL, request).toString());
    }
}
