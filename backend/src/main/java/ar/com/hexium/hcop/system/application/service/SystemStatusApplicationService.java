package ar.com.hexium.hcop.system.application.service;

import ar.com.hexium.hcop.system.application.port.in.SystemStatusUseCase;
import ar.com.hexium.hcop.system.application.port.out.ApplicationVersionPort;
import ar.com.hexium.hcop.system.application.port.out.DatabaseHealthStore;
import java.time.Clock;
import java.time.Instant;

public final class SystemStatusApplicationService implements SystemStatusUseCase {
  private final DatabaseHealthStore health;
  private final ApplicationVersionPort version;
  private final Clock clock;

  public SystemStatusApplicationService(
      DatabaseHealthStore health, ApplicationVersionPort version, Clock clock) {
    this.health = health;
    this.version = version;
    this.clock = clock;
  }

  @Override
  public ClinicalStatusView clinicalStatus() {
    return new ClinicalStatusView(health.check().up(), version.current(), Instant.now(clock));
  }

  @Override
  public RuntimeStatusView runtimeStatus() {
    return new RuntimeStatusView(version.current(), Instant.now(clock));
  }
}
