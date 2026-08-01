package ar.com.hexium.hcop.guide.infrastructure.web;

import ar.com.hexium.hcop.common.api.ApiErrorResponse;
import ar.com.hexium.hcop.guide.application.service.GuideFailure;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = GuideCatalogController.class)
public class GuideFailureAdvice {
  @ExceptionHandler(GuideFailure.class)
  ResponseEntity<ApiErrorResponse> handle(GuideFailure failure) {
    HttpStatus status = switch (failure.type()) {
      case INVALID -> HttpStatus.BAD_REQUEST;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
      case STORAGE -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
    return ResponseEntity.status(status)
        .body(ApiErrorResponse.of(status.value(), failure.getMessage(), failure.code()));
  }
}
