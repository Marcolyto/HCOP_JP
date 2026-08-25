package ar.com.hexium.hcop.integration;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AgentChatRequestSizeFilterTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final AgentChatRequestSizeFilter filter = new AgentChatRequestSizeFilter(mapper);

  @Test
  void conservaElCuerpoValidoParaJackson() throws Exception {
    byte[] body = "{\"message\":\"resumir\"}".getBytes(StandardCharsets.UTF_8);
    MockHttpServletRequest request = request("/api/agent/chat", body);
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> received = new AtomicReference<>();

    filter.doFilter(request, response, (wrapped, ignored) -> received.set(
        new String(wrapped.getInputStream().readAllBytes(), StandardCharsets.UTF_8)));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(received.get()).isEqualTo(new String(body, StandardCharsets.UTF_8));
  }

  @Test
  void rechazaContentLengthExcesivoConContratoEstable() throws Exception {
    byte[] body = new byte[AgentChatRequestSizeFilter.MAX_REQUEST_BYTES + 1];
    MockHttpServletRequest request = request("/api/agent/chat", body);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
      throw new AssertionError("La cadena no debe continuar.");
    });

    assertTooLarge(response);
  }

  @Test
  void rechazaTransferenciaSinLongitudQueSuperaElLimite() throws Exception {
    byte[] body = new byte[AgentChatRequestSizeFilter.MAX_REQUEST_BYTES + 1];
    MockHttpServletRequest source = request("/api/agent/chat", body);
    HttpServletRequest unknownLength = new HttpServletRequestWrapper(source) {
      @Override public int getContentLength() { return -1; }
      @Override public long getContentLengthLong() { return -1; }
    };
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(unknownLength, response, (ignoredRequest, ignoredResponse) -> {
      throw new AssertionError("La cadena no debe continuar.");
    });

    assertTooLarge(response);
  }

  @Test
  void noInterfiereConOtrosEndpoints() throws Exception {
    byte[] body = new byte[AgentChatRequestSizeFilter.MAX_REQUEST_BYTES + 1];
    MockHttpServletRequest request = request("/api/media/other", body);
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<HttpServletRequest> received = new AtomicReference<>();

    filter.doFilter(request, response, (wrapped, ignored) ->
        received.set((HttpServletRequest) wrapped));

    assertThat(received.get()).isSameAs(request);
  }

  private MockHttpServletRequest request(String uri, byte[] body) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
    request.setContentType("application/json");
    request.setContent(body);
    return request;
  }

  private void assertTooLarge(MockHttpServletResponse response) throws Exception {
    assertThat(response.getStatus()).isEqualTo(413);
    JsonNode json = mapper.readTree(response.getContentAsByteArray());
    assertThat(json.path("code").asText()).isEqualTo("AGENT_REQUEST_TOO_LARGE");
    assertThat(json.path("ok").asBoolean()).isFalse();
  }
}
