package ar.com.hexium.hcop.patient.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.hexium.hcop.patient.domain.Patient;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PatientJsonMapperTest {
  private final PatientJsonMapper mapper = new PatientJsonMapper();

  private Patient patient() {
    return new Patient(
        12L, "00000000", "HC-12", "PRUEBA", "PACIENTE", LocalDate.of(1980, 1, 2), "Masculino",
        "", "", "", "", "", true,
        Instant.parse("2026-07-29T10:00:00Z"), Instant.parse("2026-07-29T10:00:00Z"));
  }

  @Test
  void searchViewProyectaLosAliasLegacy() {
    var view = mapper.searchView(patient());

    assertThat(view)
        .containsEntry("id", "12")
        .containsEntry("fullName", "PACIENTE, PRUEBA")
        .containsEntry("dni", "00000000")
        .containsEntry("medicalRecord", "HC-12")
        .containsEntry("numeroDocumento", "00000000")
        .containsEntry("numeroHC", "HC-12")
        .containsEntry("migrationState", "complete")
        .containsEntry("origin", "local");
  }

  @Test
  void completenessSiempreDisponible() {
    assertThat(mapper.completeness()).containsEntry("available", true).containsEntry("percent", 100);
  }
}
