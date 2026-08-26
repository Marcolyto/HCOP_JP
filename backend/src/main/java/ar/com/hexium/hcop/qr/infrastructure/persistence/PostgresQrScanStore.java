package ar.com.hexium.hcop.qr.infrastructure.persistence;

import ar.com.hexium.hcop.qr.application.port.out.QrScanStore;
import ar.com.hexium.hcop.qr.domain.QrScan;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresQrScanStore implements QrScanStore {
  private final JdbcTemplate jdbc;

  public PostgresQrScanStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<QrScan> findOperation(String operationId) {
    return jdbc.query("""
        SELECT operation_id, code_sha256, patient_id, treatment_id, cycle_number, application_day,
               infusion_session_id, actor_user_id, scanned_at
          FROM clinical_qr_scan_events
         WHERE operation_id = ?
        """, (result, row) -> new QrScan(
        result.getString("operation_id"), result.getString("code_sha256"),
        result.getLong("patient_id"), result.getString("treatment_id"),
        result.getInt("cycle_number"), result.getInt("application_day"),
        result.getLong("infusion_session_id"),
        result.getLong("actor_user_id"), result.getTimestamp("scanned_at").toInstant()),
        operationId).stream().findFirst();
  }

  @Override
  public boolean insertIfAbsent(
      String operationId, String hash, long patientId, String treatmentId, int cycle,
      int applicationDay, long infusionId, long actorId, Instant now) {
    return jdbc.update("""
        INSERT INTO clinical_qr_scan_events (
          id, operation_id, code_sha256, patient_id, treatment_id, cycle_number, application_day,
          infusion_session_id, actor_user_id, scanned_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (operation_id) DO NOTHING
        """, UUID.randomUUID(), operationId, hash, patientId, treatmentId, cycle, applicationDay,
        infusionId, actorId, Timestamp.from(now)) == 1;
  }
}
