package ar.com.hexium.hcop.patientcontext.infrastructure.persistence;

import ar.com.hexium.hcop.patientcontext.application.port.out.PatientContextPatientPort;
import ar.com.hexium.hcop.patientcontext.application.port.out.SessionActivePatientPort;
import ar.com.hexium.hcop.patientcontext.domain.ActivePatientId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;

/** Adaptador PostgreSQL de la sesión y del catálogo de pacientes para este caso de uso. */
public final class JdbcPatientContextAdapter
    implements PatientContextPatientPort, SessionActivePatientPort {
  private final JdbcTemplate jdbc;

  public JdbcPatientContextAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean exists(ActivePatientId patientId) {
    Integer count = jdbc.queryForObject(
        "SELECT count(*) FROM patients WHERE source_id = ?",
        Integer.class,
        patientId.value());
    return count != null && count > 0;
  }

  @Override
  public void assign(String sessionToken, ActivePatientId patientId, Instant occurredAt) {
    jdbc.update("""
        UPDATE local_sessions
           SET active_patient_id = ?, last_seen_at = ?
         WHERE token_hash = ?
        """,
        patientId == null ? null : patientId.value(),
        Timestamp.from(occurredAt),
        sha256(sessionToken));
  }

  private String sha256(String value) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
