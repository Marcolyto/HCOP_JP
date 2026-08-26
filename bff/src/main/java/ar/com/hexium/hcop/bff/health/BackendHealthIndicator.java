package ar.com.hexium.hcop.bff.health;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@code /actuator/health} del BFF solo da UP si el backend también está UP — nginx sigue
 * mandando ahí su healthcheck (F0.4), y el significado del check publicado no puede cambiar
 * silenciosamente al insertar el BFF en el medio.
 */
@Component
public class BackendHealthIndicator implements HealthIndicator {

    private final RestClient backend;

    public BackendHealthIndicator(@Qualifier("backendJsonClient") RestClient backendJsonClient) {
        this.backend = backendJsonClient;
    }

    @Override
    public Health health() {
        try {
            String body = backend.get().uri("/actuator/health").retrieve().body(String.class);
            boolean up = body != null && body.contains("\"status\":\"UP\"");
            return up ? Health.up().build() : Health.down().withDetail("backend", body).build();
        } catch (RestClientException failure) {
            return Health.down(failure).build();
        }
    }
}
