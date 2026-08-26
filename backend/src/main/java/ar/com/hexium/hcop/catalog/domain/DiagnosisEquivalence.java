package ar.com.hexium.hcop.catalog.domain;

public record DiagnosisEquivalence(
    String ajccCode, String ajccDisplay, String snomedCode, String snomedDisplay,
    String cie10Code, String relation) {
}
