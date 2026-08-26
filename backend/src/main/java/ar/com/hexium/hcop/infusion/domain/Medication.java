package ar.com.hexium.hcop.infusion.domain;

public record Medication(
    String sourceItemRef, String drugId, String drugName, String prescribedDoseText,
    String doseUnit, String route, String preparationStatus, String administrationStatus,
    String notes) {
}
