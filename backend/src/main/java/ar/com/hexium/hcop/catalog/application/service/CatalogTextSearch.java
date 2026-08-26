package ar.com.hexium.hcop.catalog.application.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Normalización de texto compartida por las búsquedas de AJCC y de diagnóstico. */
final class CatalogTextSearch {
  private CatalogTextSearch() {
  }

  static List<String> normalizedTerms(String value) {
    if (value == null) return List.of();
    String normalized = normalize(value);
    return List.of(normalized.split(" ")).stream().filter(term -> !term.isBlank()).distinct().toList();
  }

  static boolean matchesAll(String value, List<String> terms) {
    String normalized = normalize(value);
    return terms.stream().allMatch(normalized::contains);
  }

  static String normalize(String value) {
    return Normalizer.normalize(String.valueOf(value), Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^\\p{L}\\p{N}]+", " ")
        .replaceAll("\\b(?:tumor|neoplasia)\\s+malign[oa]s?\\b", "carcinoma")
        .replaceAll("\\bcancer\\b", "carcinoma")
        .replaceAll("\\s+", " ")
        .trim();
  }
}
