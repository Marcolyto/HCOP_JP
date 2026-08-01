package ar.com.hexium.hcop.clinicalhistory.infrastructure.persistence;

import ar.com.hexium.hcop.clinicalhistory.application.port.in.ClinicalHistoryReadUseCase.HistorySnapshot;
import ar.com.hexium.hcop.clinicalhistory.application.port.out.ClinicalHistoryReadPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Adaptador PostgreSQL de lectura de la historia clínica versionada. */
@Repository
public class JdbcClinicalHistoryReadAdapter implements ClinicalHistoryReadPort {
  private final JdbcTemplate jdbc;

  public JdbcClinicalHistoryReadAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<HistorySnapshot> find(long patientId) {
    return jdbc.query("""
        SELECT patient_id, document_json::text, revision, imported_at, created_at, updated_at
          FROM hcop_patient_documents
         WHERE patient_id = ?
        """, this::map, patientId).stream().findFirst();
  }

  private HistorySnapshot map(ResultSet result, int rowNumber) throws SQLException {
    return new HistorySnapshot(
        result.getLong("patient_id"),
        result.getString("document_json"),
        result.getLong("revision"),
        result.getTimestamp("imported_at") == null ? null : result.getTimestamp("imported_at").toInstant(),
        result.getTimestamp("created_at").toInstant(),
        result.getTimestamp("updated_at").toInstant());
  }
}
