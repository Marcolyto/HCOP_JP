package ar.com.hexium.hcop.bff.proxy;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.bff.auth.BffSession;
import ar.com.hexium.hcop.bff.auth.BffSessionResolver;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiProxyControllerTest {

    private final BackendApiClient backend = mock(BackendApiClient.class);
    private final BffSessionResolver sessionResolver = mock(BffSessionResolver.class);
    private final ApiProxyController controller = new ApiProxyController(backend, sessionResolver);

    @Test
    void conSesionValidaReenviaElTokenDelBackend() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/auth/password");
        request.setCookies(new Cookie("BFF_SESSION", "session-1"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(sessionResolver.resolve(request)).thenReturn(Optional.of(new BffSession("tok-abc", Instant.now().plusSeconds(600))));

        controller.proxy(request, response);

        verify(backend).forward(eq(request), eq(response), eq("tok-abc"));
    }

    @Test
    void sinSesionReenviaSinToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clinical/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(sessionResolver.resolve(request)).thenReturn(Optional.empty());

        controller.proxy(request, response);

        verify(backend).forward(eq(request), eq(response), isNull());
    }
}
