package ar.com.hexium.hcop.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.auth.AuthService;
import ar.com.hexium.hcop.patient.PatientRepository.Patient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PatientServiceSearchTest {
  private final PatientRepository repository = mock(PatientRepository.class);
  private final PatientDocumentService documents = mock(PatientDocumentService.class);
  private final AuthService auth = mock(AuthService.class);
  private final PatientService service = new PatientService(repository, documents, auth);

  @Test
  void returnsRecentPatientsWhenTheSearchIsBlank() {
    when(repository.recent()).thenReturn(List.of(patient(12, "PACIENTE", "PRUEBA")));

    var result = service.search("");

    assertThat(result).singleElement().satisfies(item -> {
      assertThat(item.get("id")).isEqualTo("12");
      assertThat(item.get("fullName")).isEqualTo("PACIENTE, PRUEBA");
      assertThat(item)
          .containsEntry("dni", "00000000")
          .containsEntry("medicalRecord", "HC-12")
          .containsEntry("numeroDocumento", "00000000")
          .containsEntry("numeroHC", "HC-12");
    });
    verify(repository).recent();
    verify(repository, never()).search("");
  }

  @Test
  void allowsFilteringWithOneCharacter() {
    when(repository.search("p")).thenReturn(List.of(patient(12, "PACIENTE", "PRUEBA")));

    assertThat(service.search("p")).hasSize(1);

    verify(repository).search("p");
    verify(repository, never()).recent();
  }

  private Patient patient(long id, String lastName, String firstName) {
    return new Patient(
        id,
        "00000000",
        "HC-" + id,
        firstName,
        lastName,
        LocalDate.of(1980, 1, 2),
        "Masculino",
        "",
        "",
        "",
        "",
        "",
        true,
        Instant.parse("2026-07-29T10:00:00Z"),
        Instant.parse("2026-07-29T10:00:00Z"));
  }
}
