package ar.com.hexium.hcop.bff.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.hexium.hcop.bff.proxy.BackendApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void generaUnCorrelationIdSiNoVieneEnLaRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clinical/status");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String generated = response.getHeader("X-Correlation-Id");
        assertThat(generated).isNotBlank();
        assertThat(request.getAttribute(BackendApiClient.CORRELATION_ID_ATTRIBUTE)).isEqualTo(generated);
    }

    @Test
    void respetaUnCorrelationIdQueYaVieneDeNginx() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clinical/status");
        request.addHeader("X-Correlation-Id", "ya-existente-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("ya-existente-123");
        assertThat(request.getAttribute(BackendApiClient.CORRELATION_ID_ATTRIBUTE)).isEqualTo("ya-existente-123");
    }
}
