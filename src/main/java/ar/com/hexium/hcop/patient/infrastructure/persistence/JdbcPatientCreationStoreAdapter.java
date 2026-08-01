package ar.com.hexium.hcop.patient.infrastructure.persistence;

import ar.com.hexium.hcop.patient.application.port.in.PatientCreationUseCase.NewPatientData;
import ar.com.hexium.hcop.patient.application.port.in.PatientCreationUseCase.PatientProfile;
import ar.com.hexium.hcop.patient.application.port.out.PatientCreationStorePort;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Adaptador PostgreSQL para la regla de alta de pacientes. */
@Repository
public class JdbcPatientCreationStoreAdapter implements PatientCreationStorePort {
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public JdbcPatientCreationStoreAdapter(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Override
  public Optional<PatientProfile> findDuplicate(String dni, String medicalRecord) {
    return jdbc.query("""
        SELECT source_id, document_number, medical_record_number, first_name, last_name,
               birth_date, sex, health_insurance, health_insurance_number, phone, email,
               address, local_only, created_at, updated_at
          FROM patients
         WHERE (NULLIF(?, '') IS NOT NULL AND lower(document_number) = lower(?))
            OR (NULLIF(?, '') IS NOT NULL AND lower(medical_record_number) = lower(?))
         ORDER BY source_id
         LIMIT 1
        """, this::map, dni, dni, medicalRecord, medicalRecord).stream().findFirst();
  }

  @Override
  public PatientProfile insert(NewPatientData input) {
    long id = jdbc.queryForObject("SELECT nextval('local_patient_id_sequence')", Long.class);
    Instant now = clock.instant();
    jdbc.update("""
        INSERT INTO patients (
          source_id, document_number, medical_record_number, first_name, last_name,
          birth_date, sex, health_insurance, health_insurance_number, phone, email,
          address, identity_json, local_only, created_at, updated_at
        ) VALUES (?, NULLIF(?, ''), NULLIF(?, ''), ?, ?, ?, NULLIF(?, ''),
                  NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''),
                  NULLIF(?, ''), '{}'::jsonb, true, ?, ?)
        """,
        id, input.dni(), input.medicalRecord(), input.firstName(), input.lastName(),
        input.birthDate() == null ? null : Date.valueOf(input.birthDate()),
        input.sex(), input.insurance(), input.affiliateNumber(), input.phone(),
        input.email(), input.address(), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
    return new PatientProfile(
        id, input.dni(), input.medicalRecord(), input.firstName(), input.lastName(),
        input.birthDate(), input.sex(), input.insurance(), input.affiliateNumber(), input.phone(),
        input.email(), input.address(), true, now, now);
  }

  private PatientProfile map(ResultSet result, int rowNumber) throws SQLException {
    Date birth = result.getDate("birth_date");
    return new PatientProfile(
        result.getLong("source_id"), text(result, "document_number"),
        text(result, "medical_record_number"), text(result, "first_name"),
        text(result, "last_name"), birth == null ? null : birth.toLocalDate(),
        text(result, "sex"), text(result, "health_insurance"),
        text(result, "health_insurance_number"), text(result, "phone"),
        text(result, "email"), text(result, "address"), result.getBoolean("local_only"),
        result.getTimestamp("created_at").toInstant(), result.getTimestamp("updated_at").toInstant());
  }

  private String text(ResultSet result, String column) throws SQLException {
    String value = result.getString(column);
    return value == null ? "" : value;
  }
}
