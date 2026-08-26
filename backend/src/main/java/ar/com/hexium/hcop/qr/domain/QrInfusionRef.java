package ar.com.hexium.hcop.qr.domain;

import java.time.Instant;

public record QrInfusionRef(
    long id, int cycleNumber, int applicationDay, String scheme, Instant scheduledAt) {
}
