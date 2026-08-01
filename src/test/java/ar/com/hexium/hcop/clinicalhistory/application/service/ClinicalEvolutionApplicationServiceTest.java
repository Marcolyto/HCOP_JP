package ar.com.hexium.hcop.clinicalhistory.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.hexium.hcop.clinicalhistory.application.port.in.ClinicalEvolutionUseCase.AppendCommand;
import ar.com.hexium.hcop.clinicalhistory.application.port.out.ClinicalEvolutionPort;
import ar.com.hexium.hcop.clinicalhistory.application.service.ClinicalEvolutionApplicationService.ClinicalEvolutionFailure;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class ClinicalEvolutionApplicationServiceTest {
  private final InMemoryStore store = new InMemoryStore();
  private final ClinicalEvolutionApplicationService service = new ClinicalEvolutionApplicationService(
      store, Clock.fixed(Instant.parse("2026-07-31T00:00:00Z"), ZoneOffset.UTC));

  @Test
  void appendsSerializedEvolutionThroughThePort() {
    store.result = OptionalLong.of(7L);

    var result = service.append(command(12L, "{\"id\":\"ev-1\",\"immutable\":true}"));

    assertThat(result.revision()).isEqualTo(7L);
    assertThat(store.patientId).isEqualTo(12L);
    assertThat(store.occurredAt).isEqualTo(Instant.parse("2026-07-31T00:00:00Z"));
  }

  @Test
  void rejectsEmptyEvolutionBeforePersistence() {
    assertThatThrownBy(() -> service.append(command(12L, "")))
        .isInstanceOf(ClinicalEvolutionFailure.class)
        .hasMessage("La evolución no puede estar vacía.");
    assertThat(store.called).isFalse();
  }

  @Test
  void reportsMissingClinicalHistory() {
    assertThatThrownBy(() -> service.append(command(12L, "{}")))
        .isInstanceOf(ClinicalEvolutionFailure.class)
        .hasMessage("La historia clínica no está disponible.");
  }

  private AppendCommand command(long patientId, String json) {
    return new AppendCommand(patientId, "ev-1", json, 99L);
  }

  private static final class InMemoryStore implements ClinicalEvolutionPort {
    private OptionalLong result = OptionalLong.empty();
    private boolean called;
    private long patientId;
    private Instant occurredAt;

    @Override
    public OptionalLong append(long patientId, String evolutionId, String immutableEvolutionJson, long actorId, Instant occurredAt) {
      called = true;
      this.patientId = patientId;
      this.occurredAt = occurredAt;
      return result;
    }
  }
}
