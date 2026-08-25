package ar.com.hexium.hcop.configuration.infrastructure.web;

import ar.com.hexium.hcop.common.api.ApiErrorResponse;
import ar.com.hexium.hcop.configuration.application.service.ConfigurationFailure;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ConfigurationController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ConfigurationFailureAdvice {

  @ExceptionHandler(ConfigurationFailure.class)
  ResponseEntity<ApiErrorResponse> configuration(ConfigurationFailure failure) {
    HttpStatus status = switch (failure.type()) {
      case INVALID -> HttpStatus.BAD_REQUEST;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case CONFLICT -> HttpStatus.CONFLICT;
    };
    return ResponseEntity.status(status)
        .body(ApiErrorResponse.of(status.value(), failure.getMessage(), failure.code()));
  }
}
