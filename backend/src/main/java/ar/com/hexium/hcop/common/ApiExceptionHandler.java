package ar.com.hexium.hcop.common;

import ar.com.hexium.hcop.common.api.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiErrorResponse> api(ApiException exception) {
    return response(exception.status(), exception.getMessage(), exception.code());
  }

  @ExceptionHandler({
      MethodArgumentNotValidException.class,
      ConstraintViolationException.class,
      HttpMessageNotReadableException.class
  })
  ResponseEntity<ApiErrorResponse> invalid(Exception exception) {
    return response(HttpStatus.BAD_REQUEST, "La solicitud contiene datos inválidos.");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiErrorResponse> conflict(DataIntegrityViolationException exception) {
    return response(HttpStatus.CONFLICT, "La operación entra en conflicto con datos existentes.");
  }

  @ExceptionHandler(ConcurrencyFailureException.class)
  ResponseEntity<ApiErrorResponse> concurrency(ConcurrencyFailureException exception) {
    return response(HttpStatus.CONFLICT, "El registro fue modificado por otra operación.");
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ApiErrorResponse> notFound(NoResourceFoundException exception) {
    return response(HttpStatus.NOT_FOUND, "El recurso solicitado no existe.");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiErrorResponse> unexpected(Exception exception) {
    log.error("Unhandled API error", exception);
    return response(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo completar la operación.");
  }

  private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String message) {
    return response(status, message, "");
  }

  private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String message, String code) {
    return ResponseEntity.status(status)
        .body(ApiErrorResponse.of(status.value(), message, code));
  }
}
