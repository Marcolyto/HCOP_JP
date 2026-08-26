package ar.com.hexium.hcop.system.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.system.application.port.out.ApplicationVersionPort;
import ar.com.hexium.hcop.system.application.port.out.DatabaseHealthStore;
import ar.com.hexium.hcop.system.domain.DatabaseHealth;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SystemStatusApplicationServiceTest {

  @Test
  void clinicalStatusArmaLaVistaConLoQueDevuelvenLosPuertos() {
    DatabaseHealthStore health = mock(DatabaseHealthStore.class);
    ApplicationVersionPort version = mock(ApplicationVersionPort.class);
    Instant now = Instant.parse("2026-08-25T10:00:00Z");
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    when(health.check()).thenReturn(new DatabaseHealth(true));
    when(version.current()).thenReturn("2.3.4");
    SystemStatusApplicationService service = new SystemStatusApplicationService(health, version, clock);

    var view = service.clinicalStatus();

    assertThat(view.databaseUp()).isTrue();
    assertThat(view.version()).isEqualTo("2.3.4");
    assertThat(view.timestamp()).isEqualTo(now);
  }

  @Test
  void clinicalStatusReflejaLaBaseCaida() {
    DatabaseHealthStore health = mock(DatabaseHealthStore.class);
    ApplicationVersionPort version = mock(ApplicationVersionPort.class);
    when(health.check()).thenReturn(new DatabaseHealth(false));
    when(version.current()).thenReturn("2.3.4");
    SystemStatusApplicationService service = new SystemStatusApplicationService(
        health, version, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

    var view = service.clinicalStatus();

    assertThat(view.databaseUp()).isFalse();
  }

  @Test
  void runtimeStatusNoConsultaLaBase() {
    DatabaseHealthStore health = mock(DatabaseHealthStore.class);
    ApplicationVersionPort version = mock(ApplicationVersionPort.class);
    Instant now = Instant.parse("2026-08-25T10:00:00Z");
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    when(version.current()).thenReturn("2.3.4");
    SystemStatusApplicationService service = new SystemStatusApplicationService(health, version, clock);

    var view = service.runtimeStatus();

    assertThat(view.version()).isEqualTo("2.3.4");
    assertThat(view.timestamp()).isEqualTo(now);
  }
}
