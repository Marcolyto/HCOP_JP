package ar.com.hexium.hcop.bff.logging;

import ar.com.hexium.hcop.bff.proxy.BackendApiClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Primer filtro de la cadena: si ya viene {@code X-Correlation-Id} (nginx, otro salto), lo
 * respeta; si no, genera uno. Lo deja en {@link BackendApiClient#CORRELATION_ID_ATTRIBUTE} (así
 * el proxy lo reenvía al backend en vez de generar el suyo) y en el {@code MDC} (así
 * {@code logstash-logback-encoder} lo saca en cada línea sin tener que pasarlo a mano).
 */
@Component
@Order(10)
public class CorrelationIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String correlationId = (incoming != null && !incoming.isBlank()) ? incoming : UUID.randomUUID().toString();

        request.setAttribute(BackendApiClient.CORRELATION_ID_ATTRIBUTE, correlationId);
        response.setHeader(HEADER, correlationId);
        MDC.put(MDC_KEY, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
