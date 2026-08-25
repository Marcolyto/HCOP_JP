package ar.com.hexium.hcop.bff.proxy;

import ar.com.hexium.hcop.bff.config.BffProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * El motor genérico de proxy que usan {@code ApiProxyController} y {@code DocsProxyController}.
 * Streaming en ambas direcciones, nunca {@code byte[]} (hallazgo 4: hasta 250 MB crudos en
 * {@code POST /api/media/studies}). Pass-through literal de status + body del backend — el
 * único cuerpo que este componente genera es al no poder ni siquiera llegar al backend
 * ({@link ProxyException}).
 */
@Component
public class BackendApiClient {

    /** F1.4: {@code CorrelationIdFilter} va a poblar este atributo una vez por request. */
    public static final String CORRELATION_ID_ATTRIBUTE = "hcop.bff.correlationId";

    private static final Set<String> BLACKLISTED_REQUEST_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade",
            "content-length", "cookie", "authorization", "host");

    private static final Set<String> BLACKLISTED_RESPONSE_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "set-cookie");

    private final RestClient backend;
    private final String backendUrl;

    public BackendApiClient(@Qualifier("backendStreamClient") RestClient backendStreamClient, BffProperties properties) {
        this.backend = backendStreamClient;
        this.backendUrl = properties.backendUrl();
    }

    public void forward(HttpServletRequest request, HttpServletResponse response, String backendToken) throws IOException {
        // fromUriString(...).path(...).query(...).build(true): los componentes ya vienen
        // encodeados desde el servlet container (getRequestURI()/getQueryString() nunca
        // decodifican), build(true) le dice a UriComponentsBuilder que no los toque. Con
        // build() a secas un "tx%2017%2F165" real se vuelve "tx 17/165" y el backend lo pierde
        // (hallazgo 3). Nunca reemplazar por getServletPath()/getPathInfo()/URLDecoder.
        URI targetUri = UriComponentsBuilder.fromUriString(backendUrl)
                .path(request.getRequestURI())
                .query(request.getQueryString())
                .build(true)
                .toUri();

        RestClient.RequestBodySpec spec = backend
                .method(HttpMethod.valueOf(request.getMethod()))
                .uri(targetUri)
                .headers(headers -> copyRequestHeaders(request, headers, backendToken));

        if (hasBody(request)) {
            spec = spec.body(out -> request.getInputStream().transferTo(out));
        }

        try {
            spec.exchange((clientRequest, clientResponse) -> {
                response.setStatus(clientResponse.getStatusCode().value());
                copyResponseHeaders(clientResponse.getHeaders(), response);
                clientResponse.getBody().transferTo(response.getOutputStream());
                response.flushBuffer();
                return null;
            });
        } catch (ResourceAccessException failure) {
            throw translate(failure);
        }
    }

    private boolean hasBody(HttpServletRequest request) {
        return request.getContentLengthLong() > 0
                || "chunked".equalsIgnoreCase(request.getHeader(HttpHeaders.TRANSFER_ENCODING));
    }

    private void copyRequestHeaders(HttpServletRequest request, HttpHeaders headers, String backendToken) {
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            if (BLACKLISTED_REQUEST_HEADERS.contains(name.toLowerCase(Locale.ROOT))) continue;
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) headers.add(name, values.nextElement());
        }
        if (backendToken != null && !backendToken.isBlank()) headers.setBearerAuth(backendToken);
        headers.set("X-Correlation-Id", correlationId(request));
    }

    private void copyResponseHeaders(HttpHeaders backendHeaders, HttpServletResponse response) {
        backendHeaders.forEach((name, values) -> {
            if (BLACKLISTED_RESPONSE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) return;
            for (String value : values) response.addHeader(name, value);
        });
    }

    private String correlationId(HttpServletRequest request) {
        Object existing = request.getAttribute(CORRELATION_ID_ATTRIBUTE);
        if (existing instanceof String value && !value.isBlank()) return value;
        return UUID.randomUUID().toString();
    }

    private ProxyException translate(ResourceAccessException failure) {
        Throwable cause = failure.getCause();
        if (cause instanceof HttpTimeoutException || cause instanceof java.net.SocketTimeoutException) {
            return new ProxyException(ProxyException.Kind.TIMEOUT, "El backend no respondió a tiempo.", failure);
        }
        if (cause instanceof ConnectException || cause instanceof UnknownHostException) {
            return new ProxyException(ProxyException.Kind.UNREACHABLE, "No se pudo conectar con el backend.", failure);
        }
        return new ProxyException(ProxyException.Kind.UNREACHABLE, "Falla de red hacia el backend.", failure);
    }
}
