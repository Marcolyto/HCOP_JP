package ar.com.hexium.hcop.qr.application.port.out;

import ar.com.hexium.hcop.qr.domain.QrScan;
import java.time.Instant;
import java.util.Optional;

public interface QrScanStore {

  Optional<QrScan> findOperation(String operationId);

  boolean insertIfAbsent(
      String operationId, String hash, long patientId, String treatmentId, int cycle,
      int applicationDay, long infusionId, long actorId, Instant now);
}
