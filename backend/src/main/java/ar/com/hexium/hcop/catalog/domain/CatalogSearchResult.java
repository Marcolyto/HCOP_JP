package ar.com.hexium.hcop.catalog.domain;

/** {@code sourceConceptId} solo aplica a SNOMED/CIE-10 — null en resultados AJCC. */
public record CatalogSearchResult(
    String system, String code, String display, String group, String version, String source,
    String sourceConceptId) {
}
