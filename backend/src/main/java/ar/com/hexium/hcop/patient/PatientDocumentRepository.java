package ar.com.hexium.hcop.patient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class PatientDocumentRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final Clock clock;

  public PatientDocumentRepository(JdbcTemplate jdbc, ObjectMapper mapper, Clock clock) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.clock = clock;
  }

  public Optional<StoredDocument> find(long patientId) {
    return jdbc.query("""
        SELECT patient_id, document_json::text, revision, imported_at, created_at, updated_at
          FROM hcop_patient_documents
         WHERE patient_id = ?
        """, this::map, patientId).stream().findFirst();
  }

  public Optional<StoredDocument> lock(long patientId) {
    return jdbc.query("""
        SELECT patient_id, document_json::text, revision, imported_at, created_at, updated_at
          FROM hcop_patient_documents
         WHERE patient_id = ?
         FOR UPDATE
        """, this::map, patientId).stream().findFirst();
  }

  public StoredDocument insert(long patientId, JsonNode document, long actorId, boolean imported) {
    Instant now = clock.instant();
    return jdbc.queryForObject("""
        INSERT INTO hcop_patient_documents
          (patient_id, document_json, revision, imported_at, created_by, updated_by, created_at, updated_at)
        VALUES (?, CAST(? AS jsonb), 1, ?, ?, ?, ?, ?)
        RETURNING patient_id, document_json::text, revision, imported_at, created_at, updated_at
        """, this::map, patientId, document.toString(),
        imported ? java.sql.Timestamp.from(now) : null, actorId, actorId,
        java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
  }

  public Optional<StoredDocument> insertIfMissing(
      long patientId,
      JsonNode document,
      long actorId,
      boolean imported) {
    Instant now = clock.instant();
    return jdbc.query("""
        INSERT INTO hcop_patient_documents
          (patient_id, document_json, revision, imported_at, created_by, updated_by, created_at, updated_at)
        VALUES (?, CAST(? AS jsonb), 1, ?, ?, ?, ?, ?)
        ON CONFLICT (patient_id) DO NOTHING
        RETURNING patient_id, document_json::text, revision, imported_at, created_at, updated_at
        """, this::map, patientId, document.toString(),
        imported ? java.sql.Timestamp.from(now) : null, actorId, actorId,
        java.sql.Timestamp.from(now), java.sql.Timestamp.from(now)).stream().findFirst();
  }

  public Optional<StoredDocument> update(
      long patientId,
      JsonNode document,
      long expectedRevision,
      long actorId) {
    Instant now = clock.instant();
    return jdbc.query("""
        UPDATE hcop_patient_documents
           SET document_json = CAST(? AS jsonb),
               revision = revision + 1,
               updated_by = ?,
               updated_at = ?
         WHERE patient_id = ? AND revision = ?
        RETURNING patient_id, document_json::text, revision, imported_at, created_at, updated_at
        """, this::map, document.toString(), actorId, java.sql.Timestamp.from(now), patientId, expectedRevision)
        .stream().findFirst();
  }

  private StoredDocument map(ResultSet result, int rowNumber) throws SQLException {
    return new StoredDocument(
        result.getLong("patient_id"),
        mapper.readTree(result.getString("document_json")),
        result.getLong("revision"),
        result.getTimestamp("imported_at") == null ? null : result.getTimestamp("imported_at").toInstant(),
        result.getTimestamp("created_at").toInstant(),
        result.getTimestamp("updated_at").toInstant());
  }

  public record StoredDocument(
      long patientId,
      JsonNode document,
      long revision,
      Instant importedAt,
      Instant createdAt,
      Instant updatedAt) {
  }
}
