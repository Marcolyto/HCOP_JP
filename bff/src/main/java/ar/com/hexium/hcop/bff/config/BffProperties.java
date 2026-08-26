package ar.com.hexium.hcop.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param backendUrl base URL del backend (sin trailing slash), ej. {@code http://backend:5180}.
 * @param sessionCookieName cookie que el navegador ve, {@code BFF_SESSION}.
 */
@ConfigurationProperties(prefix = "hcop.bff")
public record BffProperties(String backendUrl, String sessionCookieName) {}
