package ar.com.hexium.hcop.clinicalhistory.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.hexium.hcop.clinicalhistory.application.port.in.ClinicalHistorySaveUseCase.SaveCommand;
import ar.com.hexium.hcop.clinicalhistory.application.port.out.ClinicalHistorySavePort;
import ar.com.hexium.hcop.clinicalhistory.application.port.out.ClinicalHistorySavePort.SavedDocument;
import ar.com.hexium.hcop.clinicalhistory.application.service.ClinicalHistorySaveApplicationService.ClinicalHistorySaveFailure;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClinicalHistorySaveApplicationServiceTest {
  private final InMemoryStore store = new InMemoryStore();
  private final ClinicalHistorySaveApplicationService service = new ClinicalHistorySaveApplicationService(store);

  @Test
  void savesMatchingPatientWithOptimisticRevision() {
    store.next = Optional.of(new SavedDocument("{}", 3L));

    var saved = service.save(commandFor(12L));

    assertThat(saved.revision()).isEqualTo(3L);
    assertThat(store.patientId).isEqualTo(12L);
    assertThat(store.expectedRevision).isEqualTo(2L);
  }

  @Test
  void rejectsDocumentForAnotherPatientBeforePersistence() {
    assertThatThrownBy(() -> service.save(commandFor(13L)))
        .isInstanceOf(ClinicalHistorySaveFailure.class)
        .hasMessage("La historia pertenece a otro paciente.");
    assertThat(store.called).isFalse();
  }

  @Test
  void reportsVersionConflictWithoutOverwriting() {
    store.next = Optional.empty();

    assertThatThrownBy(() -> service.save(commandFor(12L)))
        .isInstanceOf(ClinicalHistorySaveFailure.class)
        .hasMessage("La historia fue modificada en otra ventana.");
  }

  private SaveCommand commandFor(long documentPatientId) {
    return new SaveCommand(12L, "{}", Long.toString(documentPatientId),
        Long.toString(documentPatientId), 2L, 99L);
  }

  private static final class InMemoryStore implements ClinicalHistorySavePort {
    private Optional<SavedDocument> next = Optional.empty();
    private boolean called;
    private long patientId;
    private long expectedRevision;

    @Override
    public Optional<SavedDocument> update(long patientId, String documentJson, long expectedRevision, long actorId) {
      called = true;
      this.patientId = patientId;
      this.expectedRevision = expectedRevision;
      return next;
    }
  }
}
