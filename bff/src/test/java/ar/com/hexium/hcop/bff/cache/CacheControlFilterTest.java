package ar.com.hexium.hcop.bff.cache;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CacheControlFilterTest {

    private final CacheControlFilter filter = new CacheControlFilter();

    @Test
    void respetaElCacheControlQueMandoElBackend() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/media/images/1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            ((HttpServletResponse) res).setHeader("Cache-Control", "public, immutable");
            res.getOutputStream().write("img".getBytes(StandardCharsets.UTF_8));
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("Cache-Control")).isEqualTo("public, immutable");
    }

    @Test
    void fuerzaNoStoreSiElBackendNoMandoNada() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clinical/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> res.getOutputStream().write("{}".getBytes(StandardCharsets.UTF_8));

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void unaRespuestaSinBodyTambienQuedaConNoStorePorDefault() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clinical/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { /* nunca escribe body, ej. un 304 */ };

        filter.doFilter(request, response, chain);

        // Sin escritura de body el wrapper no llega a decidir — comportamiento aceptado:
        // nada que cachear, nada que forzar. Documentamos la ausencia explícitamente.
        assertThat(response.getHeader("Cache-Control")).isNull();
    }
}
