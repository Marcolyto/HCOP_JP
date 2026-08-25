package ar.com.hexium.hcop.bff.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * {@code backendJsonClient}: llamadas JSON de tamaño acotado (auth), con buffering — el default
 * de Boot alcanza.
 *
 * <p>{@code backendStreamClient}: el que usa {@code BackendApiClient} para {@code /api/**}
 * (hallazgo 4: {@code POST /api/media/studies} lee hasta 250 MB crudos, un cliente con buffer
 * completo sería un OOM garantizado). {@link JdkClientHttpRequestFactory} porque su
 * {@code ClientHttpRequest} extiende {@code AbstractStreamingClientHttpRequest} — el body sale
 * por {@code StreamingHttpOutputMessage.Body} sin materializarse en memoria. Redirects en
 * {@code NEVER}: seguir un redirect del backend por su cuenta rompería el pass-through literal
 * de status que exige el proxy.
 */
@Configuration
public class RestClientConfig {

    @Bean
    RestClient backendJsonClient(RestClient.Builder builder, BffProperties properties) {
        return builder.baseUrl(properties.backendUrl()).build();
    }

    @Bean
    RestClient backendStreamClient(RestClient.Builder builder) {
        HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(300));
        // Sin baseUrl a propósito: BackendApiClient arma la URI absoluta a mano con
        // UriComponentsBuilder.build(true) para no perder %2F/%20 crudos (hallazgo 3).
        return builder.requestFactory(factory).build();
    }
}
