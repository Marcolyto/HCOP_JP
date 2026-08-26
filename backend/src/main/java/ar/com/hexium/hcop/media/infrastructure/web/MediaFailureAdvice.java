package ar.com.hexium.hcop.media.infrastructure.web;

import ar.com.hexium.hcop.platform.web.api.ApiErrorResponse;
import ar.com.hexium.hcop.media.application.service.MediaFailure;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {ClinicalFileController.class, StudyTemplateController.class})
public class MediaFailureAdvice {
  @ExceptionHandler(MediaFailure.class)
  ResponseEntity<ApiErrorResponse> handle(MediaFailure failure) {
    HttpStatus status = switch (failure.type()) {
      case INVALID -> HttpStatus.BAD_REQUEST;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case CONFLICT -> HttpStatus.CONFLICT;
      case UNSUPPORTED_FORMAT -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
      case FORBIDDEN -> HttpStatus.FORBIDDEN;
      case TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
      case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
    return ResponseEntity.status(status).body(ApiErrorResponse.of(status.value(), failure.getMessage(), failure.code()));
  }
}
