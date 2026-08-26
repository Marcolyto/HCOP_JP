package ar.com.hexium.hcop.patient.infrastructure.web;

import ar.com.hexium.hcop.patient.domain.Patient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class PatientJsonMapper {

  public Map<String, Object> patientView(Patient patient) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("fullName", patient.fullName());
    view.put("dni", patient.dni());
    view.put("medicalRecord", patient.medicalRecord());
    view.put("birthDate", patient.birthDate() == null ? "" : patient.birthDate().toString());
    view.put("birthDatePrecision", "day");
    view.put("phone", patient.phone());
    view.put("insurance", patient.insurance());
    view.put("affiliateNumber", patient.affiliateNumber());
    view.put("address", patient.address());
    view.put("email", patient.email());
    view.put("sex", patient.sex());
    view.put("deathDate", "");
    view.put("deathDatePrecision", "day");
    view.put("liraId", Long.toString(patient.id()));
    view.put("coverages", patient.insurance().isBlank() && patient.affiliateNumber().isBlank()
        ? List.of()
        : List.of(Map.of(
            "id", "local:primary",
            "name", patient.insurance(),
            "affiliateNumber", patient.affiliateNumber(),
            "primary", true)));
    return view;
  }

  public Map<String, Object> searchView(Patient patient) {
    return Map.of(
        "id", Long.toString(patient.id()),
        "fullName", patient.fullName(),
        "dni", patient.dni(),
        "medicalRecord", patient.medicalRecord(),
        "numeroDocumento", patient.dni(),
        "numeroHC", patient.medicalRecord(),
        "birthDate", patient.birthDate() == null ? "" : patient.birthDate().toString(),
        "migrationState", "complete",
        "origin", patient.localOnly() ? "local" : "migration");
  }

  public Map<String, Integer> counts(Object state) {
    JsonNode node = (JsonNode) state;
    return Map.of(
        "diagnoses", arraySize(node.path("oncology").path("diagnosisRecords")),
        "antecedents", arraySize(node.path("personalHistory")),
        "evolutions", arraySize(node.path("evolutions")),
        "treatments", arraySize(node.path("oncology").path("systemicTreatments")),
        "studies", arraySize(node.path("studies")));
  }

  public Map<String, Object> completeness() {
    return Map.of(
        "available", true,
        "importable", true,
        "percent", 100,
        "status", "Ficha local",
        "missing", List.of(),
        "message", "Historia clínica disponible en HCOP JP.");
  }

  private int arraySize(JsonNode value) {
    return value.isArray() ? value.size() : 0;
  }
}
