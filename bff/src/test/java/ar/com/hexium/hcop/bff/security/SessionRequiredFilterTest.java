package ar.com.hexium.hcop.bff.security;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.hexium.hcop.bff.auth.BffSession;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SessionRequiredFilterTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final SessionRequiredFilter filter = new SessionRequiredFilter(mapper);

    @Test
    void unPathPublicoPasaSinSesion() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clinical/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void unPathProtegidoSinSesionDevuelve401ConElShapeExactoDelBackend() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clinical/protocols");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        JsonNode body = mapper.readTree(response.getContentAsByteArray());
        assertThat(body.path("ok").asBoolean()).isFalse();
        assertThat(body.path("authenticated").asBoolean()).isFalse();
        assertThat(body.path("loginRequired").asBoolean()).isTrue();
        assertThat(body.path("error").asText()).isEqualTo("Debe iniciar sesión.");
        assertThat(body.path("code").asText()).isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(body.path("status").asInt()).isEqualTo(401);
    }

    @Test
    void unPathProtegidoConSesionPasa() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clinical/protocols");
        request.setAttribute(BffSessionFilter.SESSION_ATTRIBUTE, new BffSession("tok", Instant.now().plus(Duration.ofDays(1))));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void unPathFueraDeApiPasaSiempre() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v3/api-docs");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }
}
