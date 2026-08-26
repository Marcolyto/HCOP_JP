package ar.com.hexium.hcop.bff.proxy;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class DocsProxyControllerTest {

    private final BackendApiClient backend = mock(BackendApiClient.class);
    private final DocsProxyController controller = new DocsProxyController(backend);

    @Test
    void reenviaSinTokenAunqueElRequestTraigaSesion() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v3/api-docs");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.proxy(request, response);

        verify(backend).forward(eq(request), eq(response), isNull());
    }
}
