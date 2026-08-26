package ar.com.hexium.hcop.patient.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.auth.AuthService;
import ar.com.hexium.hcop.patient.application.port.out.PatientDocumentStore;
import ar.com.hexium.hcop.patient.application.port.out.PatientStore;
import ar.com.hexium.hcop.patient.domain.Patient;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PatientApplicationServiceSearchTest {
  private final PatientStore repository = mock(PatientStore.class);
  private final PatientDocumentStore documents = mock(PatientDocumentStore.class);
  private final AuthService auth = mock(AuthService.class);
  private final PatientApplicationService service = new PatientApplicationService(
      repository, documents, auth, Clock.systemUTC());

  @Test
  void returnsRecentPatientsWhenTheSearchIsBlank() {
    when(repository.recent()).thenReturn(List.of(patient(12, "PACIENTE", "PRUEBA")));

    var result = service.search("");

    assertThat(result).singleElement().satisfies(item -> {
      assertThat(item.id()).isEqualTo(12L);
      assertThat(item.fullName()).isEqualTo("PACIENTE, PRUEBA");
      assertThat(item.dni()).isEqualTo("00000000");
      assertThat(item.medicalRecord()).isEqualTo("HC-12");
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
