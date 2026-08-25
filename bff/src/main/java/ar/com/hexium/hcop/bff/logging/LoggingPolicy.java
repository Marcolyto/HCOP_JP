package ar.com.hexium.hcop.bff.logging;

/**
 * Qué queda afuera del log de acceso. Hoy solo el healthcheck del propio contenedor
 * ({@code /actuator/health}, pegado cada ~15s por Docker) — todo lo demás bajo {@code /api/**}
 * se audita.
 */
public final class LoggingPolicy {

    private LoggingPolicy() {}

    public static boolean shouldLog(String path) {
        return !"/actuator/health".equals(path);
    }
}
