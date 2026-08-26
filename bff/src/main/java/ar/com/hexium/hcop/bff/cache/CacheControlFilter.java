package ar.com.hexium.hcop.bff.cache;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.PrintWriter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Si el backend mandó {@code Cache-Control} (ej. {@code /api/media/images/**} responde
 * {@code immutable}), se respeta. Si no mandó nada, {@code no-store} por default.
 *
 * <p>{@code @Order(50)}: el más interno de la cadena, corre justo antes del controller — hace
 * falta un {@link HttpServletResponseWrapper} en vez de un chequeo post-{@code chain.doFilter}
 * porque {@code BackendApiClient} escribe el body y hace {@code flushBuffer()} él mismo; para
 * cuando el filtro recupera el control la respuesta ya está comprometida y {@code setHeader} no
 * haría nada. El wrapper decide justo antes de que el primer byte del body salga.
 */
@Component
@Order(50)
public class CacheControlFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, new DefaultCacheControlResponse(response));
    }

    private static final class DefaultCacheControlResponse extends HttpServletResponseWrapper {

        private boolean decided;

        DefaultCacheControlResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            applyDefault();
            return super.getOutputStream();
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            applyDefault();
            return super.getWriter();
        }

        private void applyDefault() {
            if (decided) return;
            decided = true;
            if (getHeader(HttpHeaders.CACHE_CONTROL) == null) {
                setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            }
        }
    }
}
