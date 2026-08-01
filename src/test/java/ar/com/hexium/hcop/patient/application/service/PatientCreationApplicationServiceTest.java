package ar.com.hexium.hcop.patient.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.hexium.hcop.patient.application.port.in.PatientCreationUseCase.NewPatientData;
import ar.com.hexium.hcop.patient.application.port.in.PatientCreationUseCase.PatientProfile;
import ar.com.hexium.hcop.patient.application.port.out.PatientCreationStorePort;
import ar.com.hexium.hcop.patient.application.service.PatientCreationApplicationService.PatientCreationFailure;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PatientCreationApplicationServiceTest {
  private final InMemoryStore store = new InMemoryStore();
  private final PatientCreationApplicationService service = new PatientCreationApplicationService(
      store, Clock.fixed(Instant.parse("2026-07-31T00:00:00Z"), ZoneOffset.UTC));

  @Test
  void createsAValidPatientThroughThePort() {
    var created = service.create(input("30111222", "HC-1"));

    assertThat(created.patient().id()).isEqualTo(9001L);
    assertThat(created.patient().fullName()).isEqualTo("PRUEBA, PACIENTE");
  }

  @Test
  void rejectsDuplicateIdentityBeforeWriting() {
    store.duplicate = profile();

    assertThatThrownBy(() -> service.create(input("30111222", "HC-1")))
        .isInstanceOf(PatientCreationFailure.class)
        .hasMessage("Ya existe un paciente con ese DNI o historia clínica.");
    assertThat(store.inserted).isFalse();
  }

  @Test
  void requiresIdentityAndNames() {
    assertThatThrownBy(() -> service.create(input("", "")))
        .isInstanceOf(PatientCreationFailure.class)
        .hasMessage("Informe el DNI o la historia clínica.");
  }

  private NewPatientData input(String dni, String record) {
    return new NewPatientData("PACIENTE", "PRUEBA", dni, record, LocalDate.of(1980, 1, 2),
        "Masculino", "", "", "", "", "");
  }

  private PatientProfile profile() {
    Instant now = Instant.parse("2026-07-31T00:00:00Z");
    return new PatientProfile(9000L, "30111222", "HC-1", "PACIENTE", "PRUEBA",
        LocalDate.of(1980, 1, 2), "Masculino", "", "", "", "", "", true, now, now);
  }

  private final class InMemoryStore implements PatientCreationStorePort {
    private PatientProfile duplicate;
    private boolean inserted;

    @Override
    public Optional<PatientProfile> findDuplicate(String dni, String medicalRecord) {
      return Optional.ofNullable(duplicate);
    }

    @Override
    public PatientProfile insert(NewPatientData input) {
      inserted = true;
      Instant now = Instant.parse("2026-07-31T00:00:00Z");
      return new PatientProfile(9001L, input.dni(), input.medicalRecord(), input.firstName(), input.lastName(),
          input.birthDate(), input.sex(), input.insurance(), input.affiliateNumber(), input.phone(), input.email(),
          input.address(), true, now, now);
    }
  }
}
