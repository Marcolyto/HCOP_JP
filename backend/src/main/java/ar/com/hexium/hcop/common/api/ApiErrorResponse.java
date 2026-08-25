package ar.com.hexium.hcop.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/**
 * Contrato estable para errores HTTP que alcanzan a los clientes de HCOP.
 *
 * <p>El campo {@code code} es opcional para conservar la compatibilidad con las respuestas
 * históricas. Cuando existe, permite que Angular y otras integraciones reaccionen sin interpretar
 * el texto destinado al usuario.
 */
public record ApiErrorResponse(
    boolean ok,
    String error,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) String code,
    int status) {

  public ApiErrorResponse {
    if (ok) throw new IllegalArgumentException("Una respuesta de error no puede indicar ok=true.");
    error = Objects.requireNonNull(error, "error").strip();
    code = code == null ? "" : code.strip();
    if (error.isEmpty()) throw new IllegalArgumentException("El mensaje de error es obligatorio.");
    if (status < 400 || status > 599) {
      throw new IllegalArgumentException("El estado HTTP de un error debe estar entre 400 y 599.");
    }
  }

  public static ApiErrorResponse of(int status, String error) {
    return new ApiErrorResponse(false, error, "", status);
  }

  public static ApiErrorResponse of(int status, String error, String code) {
    return new ApiErrorResponse(false, error, code, status);
  }
}
