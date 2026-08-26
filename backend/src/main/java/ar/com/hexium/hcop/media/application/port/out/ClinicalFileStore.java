package ar.com.hexium.hcop.media.application.port.out;

import ar.com.hexium.hcop.media.domain.ClinicalFile;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ClinicalFileStore {

  ClinicalFile insert(NewClinicalFile file);

  Optional<ClinicalFile> find(UUID id);

  Optional<ClinicalFile> findByStorageKey(String key);

  Optional<ClinicalFile> findLatestByTreatment(String treatmentId, String kind);

  boolean deleteGranted(UUID id, String sessionHash, Instant now);

  void delete(UUID id);

  record NewClinicalFile(
      UUID id, Long patientId, String treatmentId, String kind, String originalName,
      String storageKey, String contentType, long size, String sha256, Object metadata,
      long createdBy, String sessionHash, Instant deletableUntil, Instant createdAt) {
  }
}
