package ar.com.hexium.hcop.system.application.port.in;

import java.time.Instant;

public interface SystemStatusUseCase {

  ClinicalStatusView clinicalStatus();

  RuntimeStatusView runtimeStatus();

  record ClinicalStatusView(boolean databaseUp, String version, Instant timestamp) {
  }

  record RuntimeStatusView(String version, Instant timestamp) {
  }
}
