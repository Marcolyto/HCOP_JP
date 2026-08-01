package ar.com.hexium.hcop.clinicalhistory.infrastructure.persistence;

import ar.com.hexium.hcop.clinicalhistory.application.port.out.ClinicalHistorySavePort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Adaptador PostgreSQL del control de revisión de historias clínicas. */
@Repository
public class JdbcClinicalHistorySaveAdapter implements ClinicalHistorySavePort {
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public JdbcClinicalHistorySaveAdapter(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Override
  public Optional<SavedDocument> update(long patientId, String documentJson, long expectedRevision, long actorId) {
    Instant now = clock.instant();
    return jdbc.query("""
        UPDATE hcop_patient_documents
           SET document_json = CAST(? AS jsonb),
               revision = revision + 1,
               updated_by = ?,
               updated_at = ?
         WHERE patient_id = ? AND revision = ?
        RETURNING document_json::text, revision
        """, this::map, documentJson, actorId, java.sql.Timestamp.from(now), patientId, expectedRevision)
        .stream().findFirst();
  }

  private SavedDocument map(ResultSet result, int rowNumber) throws SQLException {
    return new SavedDocument(result.getString("document_json"), result.getLong("revision"));
  }
}
