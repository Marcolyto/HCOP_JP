package ar.com.hexium.hcop.treatment.infrastructure.web;

import ar.com.hexium.hcop.platform.web.api.ApiErrorResponse;
import ar.com.hexium.hcop.treatment.application.service.TreatmentFailure;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {TreatmentController.class, TreatmentDocumentController.class})
public class TreatmentFailureAdvice {
  @ExceptionHandler(TreatmentFailure.class)
  ResponseEntity<ApiErrorResponse> handle(TreatmentFailure failure) {
    HttpStatus status = switch (failure.type()) {
      case INVALID -> HttpStatus.BAD_REQUEST;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
    };
    return ResponseEntity.status(status).body(ApiErrorResponse.of(status.value(), failure.getMessage()));
  }
}
