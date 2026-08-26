package ar.com.hexium.hcop.infusion.domain;

public record MedicationView(
    long id, String sourceItemRef, String drugId, String drugName, String prescribedDoseText,
    String doseUnit, String route, String preparationStatus, String administrationStatus,
    String notes, long revision) {
}
