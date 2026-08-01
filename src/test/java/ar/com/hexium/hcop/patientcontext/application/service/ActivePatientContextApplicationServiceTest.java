package ar.com.hexium.hcop.patientcontext.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.hexium.hcop.patientcontext.application.port.in.ActivePatientContextUseCase.SelectCommand;
import ar.com.hexium.hcop.patientcontext.application.port.out.PatientContextPatientPort;
import ar.com.hexium.hcop.patientcontext.application.port.out.SessionActivePatientPort;
import ar.com.hexium.hcop.patientcontext.domain.ActivePatientId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActivePatientContextApplicationServiceTest {
  private final Clock clock = Clock.fixed(Instant.parse("2026-07-31T12:00:00Z"), ZoneOffset.UTC);

  @Test
  void assignsExistingPatientToTheCurrentSession() {
    RecordingSessions sessions = new RecordingSessions();
    var service = service(id -> id.value() == 42L, sessions);

    service.select(new SelectCommand("session-token", 42L));

    assertThat(sessions.assignments).containsExactly(new Assignment("session-token", 42L, clock.instant()));
  }

  @Test
  void clearsTheActivePatientWithoutQueryingThePatientCatalog() {
    RecordingSessions sessions = new RecordingSessions();
    var service = service(id -> { throw new AssertionError("No debe consultar al limpiar"); }, sessions);

    service.select(new SelectCommand("session-token", null));

    assertThat(sessions.assignments).containsExactly(new Assignment("session-token", null, clock.instant()));
  }

  @Test
  void rejectsAnUnknownPatientBeforeChangingTheSession() {
    RecordingSessions sessions = new RecordingSessions();
    var service = service(id -> false, sessions);

    assertThatThrownBy(() -> service.select(new SelectCommand("session-token", 99L)))
        .isInstanceOf(ActivePatientContextFailure.class)
        .hasMessage("Paciente no encontrado.");
    assertThat(sessions.assignments).isEmpty();
  }

  @Test
  void rejectsMissingSessionToken() {
    RecordingSessions sessions = new RecordingSessions();
    var service = service(id -> true, sessions);

    assertThatThrownBy(() -> service.select(new SelectCommand(" ", 42L)))
        .isInstanceOf(ActivePatientContextFailure.class)
        .hasMessageContaining("sesión válida");
    assertThat(sessions.assignments).isEmpty();
  }

  private ActivePatientContextApplicationService service(
      PatientContextPatientPort patients, RecordingSessions sessions) {
    return new ActivePatientContextApplicationService(patients, sessions, clock);
  }

  private record Assignment(String token, Long patientId, Instant occurredAt) {
  }

  private static final class RecordingSessions implements SessionActivePatientPort {
    private final List<Assignment> assignments = new ArrayList<>();

    @Override
    public void assign(String sessionToken, ActivePatientId patientId, Instant occurredAt) {
      assignments.add(new Assignment(
          sessionToken,
          patientId == null ? null : patientId.value(),
          occurredAt));
    }
  }
}
