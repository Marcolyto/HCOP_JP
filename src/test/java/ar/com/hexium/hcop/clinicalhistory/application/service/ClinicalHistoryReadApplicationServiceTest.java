package ar.com.hexium.hcop.clinicalhistory.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.hexium.hcop.clinicalhistory.application.port.in.ClinicalHistoryReadUseCase.HistorySnapshot;
import ar.com.hexium.hcop.clinicalhistory.application.port.out.ClinicalHistoryReadPort;
import ar.com.hexium.hcop.clinicalhistory.application.service.ClinicalHistoryReadApplicationService.ClinicalHistoryReadFailure;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClinicalHistoryReadApplicationServiceTest {
  private final InMemoryStore store = new InMemoryStore();
  private final ClinicalHistoryReadApplicationService service = new ClinicalHistoryReadApplicationService(store);

  @Test
  void returnsStoredSnapshotWithoutJsonDependency() {
    store.value = Optional.of(snapshot(12L));

    var value = service.require(12L);

    assertThat(value.documentJson()).isEqualTo("{\"patient\":{}}");
    assertThat(value.revision()).isEqualTo(4L);
  }

  @Test
  void rejectsMissingHistory() {
    assertThatThrownBy(() -> service.require(12L))
        .isInstanceOf(ClinicalHistoryReadFailure.class)
        .hasMessage("La historia clínica no está disponible.");
  }

  @Test
  void rejectsInvalidPatientId() {
    assertThatThrownBy(() -> service.require(0L))
        .isInstanceOf(ClinicalHistoryReadFailure.class)
        .hasMessage("Paciente inválido.");
  }

  private HistorySnapshot snapshot(long patientId) {
    Instant now = Instant.parse("2026-07-31T00:00:00Z");
    return new HistorySnapshot(patientId, "{\"patient\":{}}", 4L, null, now, now);
  }

  private static final class InMemoryStore implements ClinicalHistoryReadPort {
    private Optional<HistorySnapshot> value = Optional.empty();

    @Override
    public Optional<HistorySnapshot> find(long patientId) {
      return value;
    }
  }
}
