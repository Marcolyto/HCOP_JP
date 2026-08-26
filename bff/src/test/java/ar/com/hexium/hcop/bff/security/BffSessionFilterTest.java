package ar.com.hexium.hcop.bff.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.bff.auth.BackendAuthClient;
import ar.com.hexium.hcop.bff.auth.BackendAuthResponse;
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
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class BffSessionFilterTest {

    private final BffSessionResolver sessionResolver = mock(BffSessionResolver.class);
    private final BffSessionService sessions = mock(BffSessionService.class);
    private final SessionCookieFactory cookies = mock(SessionCookieFactory.class);
    private final BackendAuthClient backend = mock(BackendAuthClient.class);
    private final BffSessionFilter filter = new BffSessionFilter(sessionResolver, sessions, cookies, backend);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void conAccessTokenVigenteLoDejaEnElAtributoYNoRefresca() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clinical/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        BffSession session = new BffSession(
                "tok", Instant.now().plus(Duration.ofMinutes(10)),
                "refresh", Instant.now().plus(Duration.ofDays(20)));
        when(sessionResolver.resolve(request)).thenReturn(Optional.of(session));
        when(sessionResolver.sessionId(request)).thenReturn("sid-1");

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(request.getAttribute(BffSessionFilter.SESSION_ATTRIBUTE)).isEqualTo(session);
        verify(sessions, never()).tryAcquireRefreshLock(any());
        verify(backend, never()).refresh(anyString(), any(), any());
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
    void cercaDelVencimientoConLockGanadoRefrescaContraElBackendYReemiteLaCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clinical/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        BffSession session = new BffSession(
                "tok-viejo", Instant.now().plusSeconds(30),
                "refresh-viejo", Instant.now().plus(Duration.ofDays(20)));
        when(sessionResolver.resolve(request)).thenReturn(Optional.of(session));
        when(sessionResolver.sessionId(request)).thenReturn("sid-1");
        when(sessions.tryAcquireRefreshLock("sid-1")).thenReturn(true);
        ObjectNode refreshedBody = mapper.createObjectNode()
                .put("ok", true).put("accessToken", "tok-nuevo").put("refreshToken", "refresh-nuevo")
                .put("expiresIn", 900).put("refreshExpiresIn", 2_592_000)
                .set("session", mapper.createObjectNode());
        when(backend.refresh(eq("refresh-viejo"), any(), any()))
                .thenReturn(new BackendAuthResponse(200, refreshedBody));
        when(cookies.create(eq("sid-1"), any(Duration.class), eq(request)))
                .thenReturn(ResponseCookie.from("BFF_SESSION", "sid-1").build());

        filter.doFilter(request, response, new MockFilterChain());

        verify(sessions).replace(eq("sid-1"), org.mockito.ArgumentMatchers.argThat(
                s -> s.accessToken().equals("tok-nuevo") && s.refreshToken().equals("refresh-nuevo")));
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("BFF_SESSION=sid-1");
        assertThat(((BffSession) request.getAttribute(BffSessionFilter.SESSION_ATTRIBUTE)).accessToken())
                .isEqualTo("tok-nuevo");
    }

    @Test
    void cercaDelVencimientoSinGanarElLockUsaElTokenQueYaTeniaSinPegarleAlBackend() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clinical/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        BffSession session = new BffSession(
                "tok-viejo", Instant.now().plusSeconds(30),
                "refresh-viejo", Instant.now().plus(Duration.ofDays(20)));
        when(sessionResolver.resolve(request)).thenReturn(Optional.of(session));
        when(sessionResolver.sessionId(request)).thenReturn("sid-1");
        when(sessions.tryAcquireRefreshLock("sid-1")).thenReturn(false);

        filter.doFilter(request, response, new MockFilterChain());

        verify(backend, never()).refresh(anyString(), any(), any());
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(request.getAttribute(BffSessionFilter.SESSION_ATTRIBUTE)).isEqualTo(session);
    }

    @Test
    void siElRefreshFallaBorraLaSesionDeRedisYNoDejaAtributo() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clinical/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        BffSession session = new BffSession(
                "tok-viejo", Instant.now().plusSeconds(30),
                "refresh-revocado", Instant.now().plus(Duration.ofDays(20)));
        when(sessionResolver.resolve(request)).thenReturn(Optional.of(session));
        when(sessionResolver.sessionId(request)).thenReturn("sid-1");
        when(sessions.tryAcquireRefreshLock("sid-1")).thenReturn(true);
        when(backend.refresh(eq("refresh-revocado"), any(), any()))
                .thenReturn(new BackendAuthResponse(401, mapper.createObjectNode()));

        filter.doFilter(request, response, new MockFilterChain());

        verify(sessions).delete("sid-1");
        verify(sessions, never()).replace(any(), any());
        assertThat(request.getAttribute(BffSessionFilter.SESSION_ATTRIBUTE)).isNull();
    }
}
