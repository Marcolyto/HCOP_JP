package ar.com.hexium.hcop.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param backendUrl base URL del backend (sin trailing slash), ej. {@code http://backend:5180}.
 * @param sessionCookieName cookie que el navegador ve, {@code BFF_SESSION}.
 * @param backendSessionCookieName cookie que el backend emite hoy en {@code Set-Cookie}
 *     ({@code HCOP_SESSION}) — el BFF la lee para extraer el token opaco, nunca la reenvía
 *     al navegador.
 */
@ConfigurationProperties(prefix = "hcop.bff")
public record BffProperties(String backendUrl, String sessionCookieName, String backendSessionCookieName) {}
