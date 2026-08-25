package ar.com.hexium.hcop.bff.auth;

import java.time.Instant;

/**
 * Lo único que el BFF guarda en Redis por sesión: el token opaco que el backend emitió
 * ({@code HCOP_SESSION}) y cuándo vence. Nada de datos de usuario/permisos — esos se piden
 * en vivo al backend en cada {@code /api/auth/me} para no duplicar la fuente de verdad.
 */
public record BffSession(String backendToken, Instant expiresAt) {}
