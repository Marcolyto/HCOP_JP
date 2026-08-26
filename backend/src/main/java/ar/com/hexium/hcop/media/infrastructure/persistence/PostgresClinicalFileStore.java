package ar.com.hexium.hcop.media.infrastructure.persistence;

import ar.com.hexium.hcop.media.application.port.out.ClinicalFileStore;
import ar.com.hexium.hcop.media.domain.ClinicalFile;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class PostgresClinicalFileStore implements ClinicalFileStore {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public PostgresClinicalFileStore(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Override
  public ClinicalFile insert(NewClinicalFile file) {
    jdbc.update("""
        INSERT INTO clinical_files (
          id, patient_id, treatment_id, file_kind, original_filename, storage_key,
          content_type, byte_size, sha256, metadata, created_by, created_at,
          upload_session_hash, deletable_until
        ) VALUES (?, ?, NULLIF(?, ''), ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?)
        """, file.id(), file.patientId(), file.treatmentId(), file.kind(), file.originalName(),
        file.storageKey(), file.contentType(), file.size(), file.sha256(),
        mapper.valueToTree(file.metadata()).toString(), file.createdBy(),
        Timestamp.from(file.createdAt()), file.sessionHash(),
        file.deletableUntil() == null ? null : Timestamp.from(file.deletableUntil()));
    return find(file.id()).orElseThrow();
  }

  @Override
  public Optional<ClinicalFile> find(UUID id) {
    return jdbc.query(select() + " WHERE id = ?", this::map, id).stream().findFirst();
  }

  @Override
  public Optional<ClinicalFile> findByStorageKey(String key) {
    return jdbc.query(select() + " WHERE storage_key = ?", this::map, key).stream().findFirst();
  }

  @Override
  public Optional<ClinicalFile> findLatestByTreatment(String treatmentId, String kind) {
    return jdbc.query(select() + """
         WHERE treatment_id = ? AND file_kind = ?
         ORDER BY created_at DESC
         LIMIT 1
        """, this::map, treatmentId, kind).stream().findFirst();
  }

  @Override
  public boolean deleteGranted(UUID id, String sessionHash, Instant now) {
    return jdbc.update("""
        DELETE FROM clinical_files
         WHERE id = ?
           AND upload_session_hash = ?
           AND deletable_until >= ?
        """, id, sessionHash, Timestamp.from(now)) == 1;
  }

  @Override
  public void delete(UUID id) {
    jdbc.update("DELETE FROM clinical_files WHERE id = ?", id);
  }

  private String select() {
    return """
        SELECT id, patient_id, treatment_id, file_kind, original_filename, storage_key,
               content_type, byte_size, sha256, metadata::text, created_by, created_at,
               upload_session_hash, deletable_until
          FROM clinical_files
        """;
  }

  private ClinicalFile map(ResultSet result, int row) throws SQLException {
    Timestamp deletable = result.getTimestamp("deletable_until");
    return new ClinicalFile(
        result.getObject("id", UUID.class),
        nullableLong(result, "patient_id"),
        text(result, "treatment_id"),
        result.getString("file_kind"),
        result.getString("original_filename"),
        result.getString("storage_key"),
        result.getString("content_type"),
        result.getLong("byte_size"),
        result.getString("sha256"),
        mapper.convertValue(mapper.readTree(result.getString("metadata")), Object.class),
        result.getLong("created_by"),
        result.getTimestamp("created_at").toInstant(),
        text(result, "upload_session_hash"),
        deletable == null ? null : deletable.toInstant());
  }

  private Long nullableLong(ResultSet result, String column) throws SQLException {
    long value = result.getLong(column);
    return result.wasNull() ? null : value;
  }

  private String text(ResultSet result, String column) throws SQLException {
    String value = result.getString(column);
    return value == null ? "" : value;
  }
}
