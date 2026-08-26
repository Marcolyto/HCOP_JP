package ar.com.hexium.hcop.bff.proxy;

/**
 * Único cuerpo propio que genera el BFF fuera del 401 de sesión ausente (F1.4): el backend no
 * respondió. {@link Kind#TIMEOUT} → 504, {@link Kind#UNREACHABLE} (rechazo de conexión, host
 * desconocido) → 502. Cualquier respuesta que el backend sí llegó a dar — 2xx, 4xx, 5xx propio —
 * es pass-through literal, nunca pasa por acá.
 */
public class ProxyException extends RuntimeException {

    public enum Kind {
        TIMEOUT,
        UNREACHABLE
    }

    private final Kind kind;

    public ProxyException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
