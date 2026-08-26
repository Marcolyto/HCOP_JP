package ar.com.hexium.hcop.infusion.infrastructure.web;

import ar.com.hexium.hcop.platform.web.api.ApiErrorResponse;
import ar.com.hexium.hcop.infusion.application.service.InfusionFailure;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {InfusionController.class, InfusionApplicationWorkflowController.class})
public class InfusionFailureAdvice {
  @ExceptionHandler(InfusionFailure.class)
  ResponseEntity<ApiErrorResponse> handle(InfusionFailure failure) {
    HttpStatus status = switch (failure.type()) {
      case INVALID -> HttpStatus.BAD_REQUEST;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case CONFLICT -> HttpStatus.CONFLICT;
    };
    return ResponseEntity.status(status).body(ApiErrorResponse.of(status.value(), failure.getMessage(), failure.code()));
  }
}
