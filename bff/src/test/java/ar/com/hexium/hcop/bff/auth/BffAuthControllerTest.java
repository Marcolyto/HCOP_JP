package ar.com.hexium.hcop.bff.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.bff.config.BffProperties;
import java.time.Duration;
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
    private final BffProperties properties = new BffProperties("http://backend:5180", "BFF_SESSION", "HCOP_SESSION");
    private final ObjectMapper mapper = new ObjectMapper();
    private final BffAuthController controller = new BffAuthController(backend, sessions, cookies, properties, mapper);

    @Test
    void loginExitosoCreaSesionEnRedisYSeteaLaCookieBff() {
        LoginRequest request = new LoginRequest("marcolyto", null, "secreto");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/api/auth/login");
        servletRequest.setRemoteAddr("10.0.0.5");
        JsonNode backendBody = mapper.createObjectNode().put("ok", true).put("authenticated", true);
        when(backend.login(eq(request), eq("10.0.0.5"), any()))
                .thenReturn(new BackendAuthResponse(200, backendBody,
                        "HCOP_SESSION=tok123; Path=/; Max-Age=120; HttpOnly; SameSite=Strict"));
        when(sessions.create(any())).thenReturn("session-id-1");
        when(cookies.create(eq("session-id-1"), eq(Duration.ofSeconds(120)), eq(servletRequest)))
                .thenReturn(ResponseCookie.from("BFF_SESSION", "session-id-1").build());

        ResponseEntity<JsonNode> response = controller.login(request, servletRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(backendBody);
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("BFF_SESSION=session-id-1");
        verify(sessions).create(argThat(session ->
                session.backendToken().equals("tok123") && session.expiresAt().isAfter(Instant.now())));
    }

    @Test
    void loginConCredencialesInvalidasHacePassThroughDelStatusYBodyDelBackend() {
        LoginRequest request = new LoginRequest("marcolyto", null, "mala-clave");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/api/auth/login");
        JsonNode errorBody = mapper.createObjectNode().put("ok", false).put("error", "Usuario o contraseña incorrectos.").put("status", 401);
        when(backend.login(eq(request), any(), any())).thenReturn(new BackendAuthResponse(401, errorBody, null));

        ResponseEntity<JsonNode> response = controller.login(request, servletRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).isEqualTo(errorBody);
        verify(sessions, never()).create(any());
    }

    @Test
    void loginConFallaDeBackendUsaElPrimerXForwardedForComoClientAddress() {
        LoginRequest request = new LoginRequest("marcolyto", null, "secreto");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/api/auth/login");
        servletRequest.setRemoteAddr("10.0.0.9");
        servletRequest.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
        when(backend.login(eq(request), eq("203.0.113.5"), any()))
                .thenReturn(new BackendAuthResponse(200, mapper.createObjectNode(),
                        "HCOP_SESSION=tok; Max-Age=60"));
        when(sessions.create(any())).thenReturn("sid");
        when(cookies.create(any(), any(), any())).thenReturn(ResponseCookie.from("BFF_SESSION", "sid").build());

        controller.login(request, servletRequest);

        verify(backend).login(eq(request), eq("203.0.113.5"), any());
    }

    @Test
    void loginExitosoSinCookieDeSesionDelBackendFallaFuerte() {
        LoginRequest request = new LoginRequest("marcolyto", null, "secreto");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/api/auth/login");
        when(backend.login(eq(request), any(), any()))
                .thenReturn(new BackendAuthResponse(200, mapper.createObjectNode(), null));

        assertThatThrownBy(() -> controller.login(request, servletRequest))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void logoutConSesionValidaLaBorraDeRedisYLlamaAlBackend() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/api/auth/logout");
        servletRequest.setCookies(new Cookie("BFF_SESSION", "session-id-1"));
        when(sessions.find("session-id-1"))
                .thenReturn(java.util.Optional.of(new BffSession("tok-abc", Instant.now().plusSeconds(600))));
        when(cookies.expire(servletRequest)).thenReturn(ResponseCookie.from("BFF_SESSION", "").maxAge(0).build());

        ResponseEntity<JsonNode> response = controller.logout(servletRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().path("ok").asBoolean()).isTrue();
        assertThat(response.getBody().path("authenticated").asBoolean()).isFalse();
        verify(backend).logout("tok-abc");
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
    void meConSesionValidaReenviaElTokenAlBackend() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/auth/me");
        servletRequest.setCookies(new Cookie("BFF_SESSION", "session-id-1"));
        when(sessions.find("session-id-1"))
                .thenReturn(java.util.Optional.of(new BffSession("tok-me", Instant.now().plusSeconds(600))));
        JsonNode body = mapper.createObjectNode().put("ok", true).put("authenticated", true);
        when(backend.me("tok-me")).thenReturn(new BackendAuthResponse(200, body, null));

        ResponseEntity<JsonNode> response = controller.me(servletRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(body);
    }

    @Test
    void meSinSesionLlamaAlBackendSinTokenYDejaQueElBackendDigaNoAutenticado() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/auth/me");
        JsonNode body = mapper.createObjectNode().put("ok", true).put("authenticated", false)
                .put("loginRequired", true).put("autoLoginEnabled", false);
        when(backend.me(isNull())).thenReturn(new BackendAuthResponse(200, body, null));

        ResponseEntity<JsonNode> response = controller.me(servletRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(body);
        verify(backend).me(isNull());
    }
}
