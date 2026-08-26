package ar.com.hexium.hcop.integration.infrastructure.web;

import ar.com.hexium.hcop.common.api.ApiErrorResponse;
import ar.com.hexium.hcop.integration.application.service.IntegrationFailure;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = LlmController.class)
public class IntegrationFailureAdvice {
  @ExceptionHandler(IntegrationFailure.class)
  ResponseEntity<ApiErrorResponse> handle(IntegrationFailure failure) {
    HttpStatus status = switch (failure.type()) {
      case INVALID -> HttpStatus.BAD_REQUEST;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
    };
    return ResponseEntity.status(status).body(ApiErrorResponse.of(status.value(), failure.getMessage(), failure.code()));
  }
}
