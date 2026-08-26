package ar.com.hexium.hcop.bff.logging;

import ar.com.hexium.hcop.bff.proxy.BackendApiClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Una línea por request bajo {@link LoggingPolicy}. Nunca loguea bodies — cuerpos clínicos y
 * credenciales pasan por acá, solo metadata (método, path, status, duración, correlation id).
 */
@Component
@Order(20)
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestResponseLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!LoggingPolicy.shouldLog(path)) {
            chain.doFilter(request, response);
            return;
        }

        long startNanos = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            LogEvent event = new LogEvent(
                    correlationId(request), request.getMethod(), path, response.getStatus(), durationMs);
            log.info(
                    "{} {} -> {} ({} ms) [{}]",
                    event.method(), event.path(), event.status(), event.durationMs(), event.correlationId());
        }
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(BackendApiClient.CORRELATION_ID_ATTRIBUTE);
        return value instanceof String s ? s : "";
    }
}
