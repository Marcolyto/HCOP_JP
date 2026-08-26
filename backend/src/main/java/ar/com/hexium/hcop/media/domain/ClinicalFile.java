package ar.com.hexium.hcop.media.domain;

import java.time.Instant;
import java.util.UUID;

/** {@code metadata} es el árbol opaco asociado al archivo (studyId/category/previewable, o kind). */
public record ClinicalFile(
    UUID id,
    Long patientId,
    String treatmentId,
    String kind,
    String originalName,
    String storageKey,
    String contentType,
    long size,
    String sha256,
    Object metadata,
    long createdBy,
    Instant createdAt,
    String uploadSessionHash,
    Instant deletableUntil) {
}
