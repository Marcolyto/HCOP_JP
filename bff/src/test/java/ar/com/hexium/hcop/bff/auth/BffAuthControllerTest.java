package ar.com.hexium.hcop.bff.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.bff.config.BffProperties;
import java.time.Instant;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class BffAuthControllerTest {

    private final BackendAuthClient backend = mock(BackendAuthClient.class);
    private final BffSessionService sessions = mock(BffSessionService.class);
    private final SessionCookieFactory cookies = mock(SessionCookieFactory.class);
    private final BffProperties properties = new BffProperties("http://backend:5180", "BFF_SESSION");
    private final BffSessionResolver sessionResolver = new BffSessionResolver(sessions, properties);
    private final ObjectMapper mapper = new ObjectMapper();
    private final BffAuthController controller =
            new BffAuthController(backend, sessions, sessionResolver, cookies, mapper);

    @Test
    void loginExitosoCreaSesionEnRedisYSeteaLaCookieBffSinExponerLosTokens() {
        LoginRequest request = new LoginRequest("marcolyto", null, "secreto");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/api/auth/login");
        servletRequest.setRemoteAddr("10.0.0.5");
        JsonNode sessionView = mapper.createObjectNode().put("ok", true).put("authenticated", true);
        JsonNode backendBody = mapper.createObjectNode()
                .put("ok", true)
                .put("accessToken", "access-123")
                .put("refreshToken", "refresh-123")
                .put("expiresIn", 900)
                .put("refreshExpiresIn", 2_592_000)
                .set("session", sessionView);
        when(backend.login(eq(request), eq("10.0.0.5"), any()))
                .thenReturn(new BackendAuthResponse(200, backendBody));
        when(sessions.create(any())).thenReturn("session-id-1");
        when(cookies.create(eq("session-id-1"), any(), eq(servletRequest)))
                .thenReturn(ResponseCookie.from("BFF_SESSION", "session-id-1").build());

        ResponseEntity<JsonNode> response = controller.login(request, servletRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(sessionView);
        assertThat(mapper.writeValueAsString(response.getBody())).doesNotContain("access-123", "refresh-123");
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("BFF_SESSION=session-id-1");
        verify(sessions).create(argThat(session ->
                session.accessToken().equals("access-123")
                        && session.refreshToken().equals("refresh-123")
                        && session.accessExpiresAt().isAfter(Instant.now())
                        && session.refreshExpiresAt().isAfter(session.accessExpiresAt())));
    }

    @Test
    void loginConCredencialesInvalidasHacePassThroughDelStatusYBodyDelBackend() {
        LoginRequest request = new LoginRequest("marcolyto", null, "mala-clave");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/api/auth/login");
        JsonNode errorBody = mapper.createObjectNode().put("ok", false).put("error", "Usuario o contraseña incorrectos.").put("status", 401);
        when(backend.login(eq(request), any(), any())).thenReturn(new BackendAuthResponse(401, errorBody));

        ResponseEntity<JsonNode> response = controller.login(request, servletRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).isEqualTo(errorBody);
        verify(sessions, never()).create(any());
    }

    @Test
    void loginUsaElPrimerXForwardedForComoClientAddress() {
        LoginRequest request = new LoginRequest("marcolyto", null, "secreto");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/api/auth/login");
        servletRequest.setRemoteAddr("10.0.0.9");
        servletRequest.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
        JsonNode backendBody = mapper.createObjectNode()
                .put("ok", true).put("accessToken", "a").put("refreshToken", "r")
                .put("expiresIn", 60).put("refreshExpiresIn", 120)
                .set("session", mapper.createObjectNode());
        when(backend.login(eq(request), eq("203.0.113.5"), any()))
                .thenReturn(new BackendAuthResponse(200, backendBody));
        when(sessions.create(any())).thenReturn("sid");
        when(cookies.create(any(), any(), any())).thenReturn(ResponseCookie.from("BFF_SESSION", "sid").build());

        controller.login(request, servletRequest);

        verify(backend).login(eq(request), eq("203.0.113.5"), any());
    }

    @Test
    void logoutConSesionValidaLaBorraDeRedisYLlamaAlBackendConElRefreshToken() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/api/auth/logout");
        servletRequest.setCookies(new Cookie("BFF_SESSION", "session-id-1"));
        when(sessions.find("session-id-1")).thenReturn(java.util.Optional.of(new BffSession(
                "access-abc", Instant.now().plusSeconds(600),
                "refresh-abc", Instant.now().plusSeconds(2_000_000))));
        when(cookies.expire(servletRequest)).thenReturn(ResponseCookie.from("BFF_SESSION", "").maxAge(0).build());

        ResponseEntity<JsonNode> response = controller.logout(servletRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().path("ok").asBoolean()).isTrue();
        assertThat(response.getBody().path("authenticated").asBoolean()).isFalse();
        verify(backend).logout("refresh-abc");
        verify(sessions).delete("session-id-1");
    }

    @Test
    void logoutSinCookieNoLlamaAlBackendPeroSigueRespondiendoOk() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/api/auth/logout");
        when(cookies.expire(servletRequest)).thenReturn(ResponseCookie.from("BFF_SESSION", "").maxAge(0).build());

        ResponseEntity<JsonNode> response = controller.logout(servletRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().path("ok").asBoolean()).isTrue();
        verify(backend, never()).logout(any());
    }

    @Test
    void meConSesionValidaReenviaElAccessTokenAlBackend() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/auth/me");
        servletRequest.setCookies(new Cookie("BFF_SESSION", "session-id-1"));
        when(sessions.find("session-id-1")).thenReturn(java.util.Optional.of(new BffSession(
                "access-me", Instant.now().plusSeconds(600),
                "refresh-me", Instant.now().plusSeconds(2_000_000))));
        JsonNode body = mapper.createObjectNode().put("ok", true).put("authenticated", true);
        when(backend.me("access-me")).thenReturn(new BackendAuthResponse(200, body));

        ResponseEntity<JsonNode> response = controller.me(servletRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(body);
    }

    @Test
    void meSinSesionLlamaAlBackendSinTokenYDejaQueElBackendDigaNoAutenticado() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/auth/me");
        JsonNode body = mapper.createObjectNode().put("ok", true).put("authenticated", false)
                .put("loginRequired", true).put("autoLoginEnabled", false);
        when(backend.me(isNull())).thenReturn(new BackendAuthResponse(200, body));

        ResponseEntity<JsonNode> response = controller.me(servletRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(body);
        verify(backend).me(isNull());
    }
}
