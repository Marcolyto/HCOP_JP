package ar.com.hexium.hcop.bff.logging;

/** Una línea de acceso del proxy. Con {@code logstash-logback-encoder} sale como JSON estructurado. */
public record LogEvent(String correlationId, String method, String path, int status, long durationMs) {}
