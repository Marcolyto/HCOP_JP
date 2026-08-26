package ar.com.hexium.hcop.integration.application.service;

/** Acota el texto clínico enviado al LLM — compartido por los cinco casos de uso del módulo. */
final class ClinicalTextLimits {
  static final int MAX_CLINICAL_TEXT = 350_000;

  private ClinicalTextLimits() {
  }

  static String limit(String value) {
    String text = value == null ? "" : value;
    return text.length() <= MAX_CLINICAL_TEXT ? text : text.substring(0, MAX_CLINICAL_TEXT);
  }

  static String limit(String value, int maximum) {
    String text = value == null ? "" : value;
    return text.length() <= maximum ? text : text.substring(0, maximum);
  }
}
