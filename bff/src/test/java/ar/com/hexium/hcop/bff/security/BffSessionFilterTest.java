package ar.com.hexium.hcop.bff.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.bff.auth.BffSession;
import ar.com.hexium.hcop.bff.auth.BffSessionResolver;
import ar.com.hexium.hcop.bff.auth.BffSessionService;
import ar.com.hexium.hcop.bff.auth.SessionCookieFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

class BffSessionFilterTest {

    private final BffSessionResolver sessionResolver = mock(BffSessionResolver.class);
    private final BffSessionService sessions = mock(BffSessionService.class);
    private final SessionCookieFactory cookies = mock(SessionCookieFactory.class);
    private final BffSessionFilter filter = new BffSessionFilter(sessionResolver, sessions, cookies);

    @Test
    void conSesionVigenteLaDejaEnElAtributoYNoRefresca() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clinical/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        BffSession session = new BffSession("tok", Instant.now().plus(Duration.ofDays(20)));
        when(sessionResolver.resolve(request)).thenReturn(Optional.of(session));
        when(sessionResolver.sessionId(request)).thenReturn("sid-1");

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(request.getAttribute(BffSessionFilter.SESSION_ATTRIBUTE)).isEqualTo(session);
        verify(sessions, never()).tryAcquireRefreshLock(any());
        verify(sessions, never()).refresh(any(), any());
    }

    @Test
    void sinCookieNoDejaAtributoYSigueLaCadena() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clinical/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(sessionResolver.resolve(request)).thenReturn(Optional.empty());
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute(BffSessionFilter.SESSION_ATTRIBUTE)).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void cercaDelVencimientoConLockGanadoRefrescaRedisYReemiteLaCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clinical/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        BffSession session = new BffSession("tok", Instant.now().plus(Duration.ofHours(2)));
        when(sessionResolver.resolve(request)).thenReturn(Optional.of(session));
        when(sessionResolver.sessionId(request)).thenReturn("sid-1");
        when(sessions.tryAcquireRefreshLock("sid-1")).thenReturn(true);
        when(cookies.create(eq("sid-1"), any(Duration.class), eq(request)))
                .thenReturn(ResponseCookie.from("BFF_SESSION", "sid-1").build());

        filter.doFilter(request, response, new MockFilterChain());

        verify(sessions).refresh(eq("sid-1"), any(Duration.class));
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("BFF_SESSION=sid-1");
    }

    @Test
    void cercaDelVencimientoSinGanarElLockNoRefrescaNiReemiteCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clinical/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        BffSession session = new BffSession("tok", Instant.now().plus(Duration.ofHours(2)));
        when(sessionResolver.resolve(request)).thenReturn(Optional.of(session));
        when(sessionResolver.sessionId(request)).thenReturn("sid-1");
        when(sessions.tryAcquireRefreshLock("sid-1")).thenReturn(false);

        filter.doFilter(request, response, new MockFilterChain());

        verify(sessions, never()).refresh(any(), any());
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }
}
