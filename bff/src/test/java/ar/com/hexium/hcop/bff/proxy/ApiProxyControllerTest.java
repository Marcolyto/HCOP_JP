package ar.com.hexium.hcop.bff.proxy;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ar.com.hexium.hcop.bff.auth.BffSession;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiProxyControllerTest {

    private final BackendApiClient backend = mock(BackendApiClient.class);
    private final ApiProxyController controller = new ApiProxyController(backend);

    @Test
    void conSesionResueltaReenviaElTokenDelBackend() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/auth/password");
        MockHttpServletResponse response = new MockHttpServletResponse();
        BffSession session = new BffSession(
                "tok-abc", Instant.now().plusSeconds(600),
                "refresh-abc", Instant.now().plusSeconds(2_000_000));

        controller.proxy(request, response, Optional.of(session));

        verify(backend).forward(eq(request), eq(response), eq("tok-abc"));
    }

    @Test
    void sinSesionReenviaSinToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clinical/status");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.proxy(request, response, Optional.empty());

        verify(backend).forward(eq(request), eq(response), isNull());
    }
}
