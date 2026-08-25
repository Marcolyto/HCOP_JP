package ar.com.hexium.hcop.common.api;

/**
 * Respuesta de compatibilidad para una solicitud protegida sin sesión válida.
 *
 * <p>Conserva {@code authenticated} y {@code loginRequired}, utilizados por la interfaz actual, y
 * agrega el código estable y el estado HTTP del contrato común.
 */
public record AuthenticationRequiredResponse(
    boolean ok,
    boolean authenticated,
    boolean loginRequired,
    String error,
    String code,
    int status) {

  public static AuthenticationRequiredResponse required() {
    return new AuthenticationRequiredResponse(
        false,
        false,
        true,
        "Debe iniciar sesión.",
        "AUTHENTICATION_REQUIRED",
        401);
  }
}
