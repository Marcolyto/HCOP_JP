package ar.com.hexium.hcop.catalog.infrastructure.web;

import ar.com.hexium.hcop.catalog.application.service.CatalogFailure;
import ar.com.hexium.hcop.platform.web.api.ApiErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
    AjccCatalogController.class,
    SeerTnmCatalogController.class,
    SystemicFormController.class,
    DiagnosisCatalogController.class,
    LegacyCatalogController.class
})
public class CatalogFailureAdvice {
  @ExceptionHandler(CatalogFailure.class)
  ResponseEntity<ApiErrorResponse> handle(CatalogFailure failure) {
    HttpStatus status = switch (failure.type()) {
      case INVALID -> HttpStatus.BAD_REQUEST;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
    };
    return ResponseEntity.status(status).body(ApiErrorResponse.of(status.value(), failure.getMessage(), ""));
  }
}
