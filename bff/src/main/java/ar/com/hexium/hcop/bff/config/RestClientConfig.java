package ar.com.hexium.hcop.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * {@code backendJsonClient}: llamadas JSON de tamaño acotado (auth). El cliente de streaming
 * sin buffering para {@code ApiProxyController} (250 MB de estudios, hallazgo 4) se agrega en
 * F1.3, no antes — acá todavía no hace falta.
 */
@Configuration
public class RestClientConfig {

    @Bean
    RestClient backendJsonClient(RestClient.Builder builder, BffProperties properties) {
        return builder.baseUrl(properties.backendUrl()).build();
    }
}
