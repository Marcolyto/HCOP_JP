package ar.com.hexium.hcop.bff.auth;

import java.time.Duration;
import java.util.Optional;

/**
 * Parser mínimo y deliberado (no {@code java.net.HttpCookie.parse}, cuyo manejo de atributos
 * no reconocidos como {@code SameSite} varía entre JDKs) para leer el {@code Set-Cookie} que
 * emite hoy el backend y sacarle el valor y el {@code Max-Age}.
 */
final class SetCookieParser {

    private SetCookieParser() {}

    record ParsedCookie(String value, Duration maxAge) {}

    static Optional<ParsedCookie> parse(String setCookieHeader, String cookieName) {
        if (setCookieHeader == null || setCookieHeader.isBlank()) return Optional.empty();
        String[] parts = setCookieHeader.split(";");
        String[] nameValue = parts[0].trim().split("=", 2);
        if (nameValue.length != 2 || !nameValue[0].trim().equals(cookieName)) return Optional.empty();
        String value = nameValue[1].trim();

        Duration maxAge = null;
        for (int i = 1; i < parts.length; i++) {
            String attribute = parts[i].trim();
            if (attribute.regionMatches(true, 0, "Max-Age=", 0, 8)) {
                try {
                    maxAge = Duration.ofSeconds(Long.parseLong(attribute.substring(8).trim()));
                } catch (NumberFormatException ignored) {
                    // Max-Age ilegible: se cae al default del caller.
                }
            }
        }
        return Optional.of(new ParsedCookie(value, maxAge));
    }
}
