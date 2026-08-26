package ar.com.hexium.hcop.diagnosis.infrastructure.web;

import ar.com.hexium.hcop.platform.web.api.ApiErrorResponse;
import ar.com.hexium.hcop.diagnosis.application.service.DiagnosisFailure;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = DiagnosisController.class)
public class DiagnosisFailureAdvice {
  @ExceptionHandler(DiagnosisFailure.class)
  ResponseEntity<ApiErrorResponse> handle(DiagnosisFailure failure) {
    HttpStatus status = switch (failure.type()) {
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case CONFLICT -> HttpStatus.CONFLICT;
      case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
    };
    return ResponseEntity.status(status)
        .body(ApiErrorResponse.of(status.value(), failure.getMessage(), failure.code()));
  }
}
