package ar.com.hexium.hcop.bff.proxy;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestControllerAdvice(basePackageClasses = ApiProxyController.class)
public class ProxyExceptionHandler {

    private final ObjectMapper mapper;

    public ProxyExceptionHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @ExceptionHandler(ProxyException.class)
    public ResponseEntity<JsonNode> handle(ProxyException exception) {
        HttpStatus status = exception.kind() == ProxyException.Kind.TIMEOUT
                ? HttpStatus.GATEWAY_TIMEOUT
                : HttpStatus.BAD_GATEWAY;
        JsonNode body = mapper.createObjectNode()
                .put("ok", false)
                .put("error", exception.getMessage())
                .put("status", status.value());
        return ResponseEntity.status(status).body(body);
    }
}
