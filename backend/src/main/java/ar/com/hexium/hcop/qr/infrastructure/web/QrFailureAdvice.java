package ar.com.hexium.hcop.qr.infrastructure.web;

import ar.com.hexium.hcop.common.api.ApiErrorResponse;
import ar.com.hexium.hcop.qr.application.service.QrFailure;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = QrWorkflowController.class)
public class QrFailureAdvice {
  @ExceptionHandler(QrFailure.class)
  ResponseEntity<ApiErrorResponse> handle(QrFailure failure) {
    HttpStatus status = switch (failure.type()) {
      case INVALID -> HttpStatus.BAD_REQUEST;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case CONFLICT -> HttpStatus.CONFLICT;
    };
    return ResponseEntity.status(status).body(ApiErrorResponse.of(status.value(), failure.getMessage()));
  }
}
