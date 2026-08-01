package ar.com.hexium.hcop.guide.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * Nombre seguro y portable de un archivo de guía.
 */
public record GuideFileName(String value) {
  public GuideFileName {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isBlank() || value.length() > 240) {
      throw new IllegalArgumentException("Nombre inválido.");
    }
  }

  public static GuideFileName fromRaw(String rawName) {
    String candidate = rawName == null ? "" : rawName.strip().replace('\\', '/');
    int separator = candidate.lastIndexOf('/');
    if (separator >= 0) candidate = candidate.substring(separator + 1);
    candidate = candidate.replaceAll("[^\\p{L}\\p{N}._ ()-]", "_");
    return new GuideFileName(candidate);
  }

  public boolean pdf() {
    return value.toLowerCase(Locale.ROOT).endsWith(".pdf");
  }
}
