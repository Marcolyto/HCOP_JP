package ar.com.hexium.hcop.integration;

import ar.com.hexium.hcop.common.api.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Acota el JSON del Agente antes de la deserialización de Spring/Jackson.
 *
 * <p>El límite es deliberadamente mayor que el contexto clínico admitido por el controlador
 * para contemplar UTF-8, historial y estructura JSON, pero evita materializar cuerpos arbitrarios.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AgentChatRequestSizeFilter extends OncePerRequestFilter {
  static final int MAX_REQUEST_BYTES = 2 * 1024 * 1024;
  private final ObjectMapper mapper;

  public AgentChatRequestSizeFilter(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !"POST".equalsIgnoreCase(request.getMethod())
        || !"/api/agent/chat".equals(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    long declared = request.getContentLengthLong();
    if (declared > MAX_REQUEST_BYTES) {
      reject(response);
      return;
    }

    byte[] body = request.getInputStream().readNBytes(MAX_REQUEST_BYTES + 1);
    if (body.length > MAX_REQUEST_BYTES) {
      reject(response);
      return;
    }
    filterChain.doFilter(new CachedBodyRequest(request, body), response);
  }

  private void reject(HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
    response.setContentType("application/json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    mapper.writeValue(
        response.getOutputStream(),
        ApiErrorResponse.of(
            HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
            "La consulta del Agente supera el tamaño permitido.",
            "AGENT_REQUEST_TOO_LARGE"));
  }

  private static final class CachedBodyRequest extends HttpServletRequestWrapper {
    private final byte[] body;

    private CachedBodyRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body.clone();
    }

    @Override
    public int getContentLength() {
      return body.length;
    }

    @Override
    public long getContentLengthLong() {
      return body.length;
    }

    @Override
    public ServletInputStream getInputStream() {
      ByteArrayInputStream input = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public boolean isFinished() {
          return input.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
          // La copia en memoria está disponible de forma sincrónica.
        }

        @Override
        public int read() {
          return input.read();
        }

        @Override
        public int read(byte[] target, int offset, int length) {
          return input.read(target, offset, length);
        }
      };
    }

    @Override
    public BufferedReader getReader() {
      String encoding = getCharacterEncoding();
      Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
      return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }
  }
}
