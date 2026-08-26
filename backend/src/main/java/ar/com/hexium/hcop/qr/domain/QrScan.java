package ar.com.hexium.hcop.qr.domain;

import java.time.Instant;

public record QrScan(
    String operationId, String codeHash, long patientId, String treatmentId, int cycleNumber,
    int applicationDay, long infusionId, long actorId, Instant scannedAt) {
}
