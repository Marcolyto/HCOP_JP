package ar.com.hexium.hcop.integration.studies;

import org.springframework.core.env.Environment;

final class ExternalStudyConfiguration {
  private final Environment environment;
  private final java.util.Map<String, String> overrides;
  ExternalStudyConfiguration(Environment environment) { this(environment, java.util.Map.of()); }
  ExternalStudyConfiguration(Environment environment, java.util.Map<String, String> overrides) {
    this.environment = environment; this.overrides = java.util.Map.copyOf(overrides);
  }
  String setting(String key, String fallback) {
    String value = overrides.containsKey(key) ? overrides.get(key) : environment.getProperty(key);
    return value == null || value.isBlank() ? fallback : value.trim();
  }
  String provider(String id, String key, String fallback) {
    return setting("EXTERNAL_STUDIES_" + id.toUpperCase(java.util.Locale.ROOT) + "_" + key, fallback);
  }
  String credential(String id, String key) {
    String name = "EXTERNAL_STUDIES_" + id.toUpperCase(java.util.Locale.ROOT) + "_" + key;
    String value = overrides.containsKey(name) ? overrides.get(name) : environment.getProperty(name);
    if (value == null || value.isBlank()) throw new ExternalStudyFailure("Faltan configurar las credenciales de esta fuente.");
    return key.equals("PASSWORD") ? value : value.trim();
  }
  int sourceTimeoutSeconds() { return number("EXTERNAL_STUDIES_SOURCE_TIMEOUT_SECONDS", 30, 1, 30); }
  int globalTimeoutSeconds() { return number("EXTERNAL_STUDIES_GLOBAL_TIMEOUT_SECONDS", 80, 1, 85); }
  int maxBytes() { return number("EXTERNAL_STUDIES_MAX_RESPONSE_BYTES", 2_097_152, 4_096, 8_388_608); }
  int maxDocumentBytes() { return number("EXTERNAL_STUDIES_MAX_DOCUMENT_BYTES", 26_214_400, 4_096, 52_428_800); }
  int maxResults() { return number("EXTERNAL_STUDIES_MAX_RESULTS", 500, 1, 1000); }
  int maxPages() { return number("EXTERNAL_STUDIES_MAX_PAGES", 5, 1, 10); }
  private int number(String key, int fallback, int minimum, int maximum) {
    try { return Math.max(minimum, Math.min(maximum, Integer.parseInt(setting(key, String.valueOf(fallback))))); }
    catch (NumberFormatException invalid) { return fallback; }
  }
}
