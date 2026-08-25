package ar.com.hexium.hcop.patient;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PatientRepository {
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public PatientRepository(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  public List<Patient> search(String query) {
    String like = "%" + query.trim().toLowerCase() + "%";
    return jdbc.query("""
        SELECT source_id, document_number, medical_record_number, first_name, last_name,
               birth_date, sex, health_insurance, health_insurance_number, phone, email,
               address, local_only, created_at, updated_at
          FROM patients
         WHERE lower(COALESCE(document_number, '')) LIKE ?
            OR lower(COALESCE(medical_record_number, '')) LIKE ?
            OR lower(COALESCE(first_name, '')) LIKE ?
            OR lower(COALESCE(last_name, '')) LIKE ?
            OR lower(concat_ws(' ', last_name, first_name)) LIKE ?
            OR lower(concat_ws(' ', first_name, last_name)) LIKE ?
         ORDER BY last_name, first_name, source_id
         LIMIT 50
        """, this::map, like, like, like, like, like, like);
  }

  public List<Patient> recent() {
    return jdbc.query("""
        SELECT source_id, document_number, medical_record_number, first_name, last_name,
               birth_date, sex, health_insurance, health_insurance_number, phone, email,
               address, local_only, created_at, updated_at
          FROM patients
         ORDER BY updated_at DESC, last_name, first_name, source_id
         LIMIT 50
        """, this::map);
  }

  public Optional<Patient> find(long patientId) {
    return jdbc.query("""
        SELECT source_id, document_number, medical_record_number, first_name, last_name,
               birth_date, sex, health_insurance, health_insurance_number, phone, email,
               address, local_only, created_at, updated_at
          FROM patients WHERE source_id = ?
        """, this::map, patientId).stream().findFirst();
  }

  public Optional<Patient> findBySeedKey(String seedKey) {
    return jdbc.query("""
        SELECT source_id, document_number, medical_record_number, first_name, last_name,
               birth_date, sex, health_insurance, health_insurance_number, phone, email,
               address, local_only, created_at, updated_at
          FROM patients
         WHERE identity_json ->> 'seedKey' = ?
         ORDER BY source_id
         LIMIT 1
        """, this::map, seedKey).stream().findFirst();
  }

  public Optional<Patient> findDuplicate(String dni, String medicalRecord) {
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

  public Patient insert(NewPatient input) {
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
    return find(id).orElseThrow();
  }

  public Optional<Patient> insertSeedIfMissing(NewPatient input, String seedKey) {
    long id = jdbc.queryForObject("SELECT nextval('local_patient_id_sequence')", Long.class);
    Instant now = clock.instant();
    return jdbc.query("""
        INSERT INTO patients (
          source_id, document_number, medical_record_number, first_name, last_name,
          birth_date, sex, health_insurance, health_insurance_number, phone, email,
          address, identity_json, local_only, created_at, updated_at
        ) VALUES (?, NULLIF(?, ''), NULLIF(?, ''), ?, ?, ?, NULLIF(?, ''),
                  NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''),
                  NULLIF(?, ''), jsonb_build_object('seedKey', ?), true, ?, ?)
        ON CONFLICT DO NOTHING
        RETURNING source_id, document_number, medical_record_number, first_name, last_name,
                  birth_date, sex, health_insurance, health_insurance_number, phone, email,
                  address, local_only, created_at, updated_at
        """, this::map,
        id, input.dni(), input.medicalRecord(), input.firstName(), input.lastName(),
        input.birthDate() == null ? null : Date.valueOf(input.birthDate()),
        input.sex(), input.insurance(), input.affiliateNumber(), input.phone(),
        input.email(), input.address(), seedKey,
        java.sql.Timestamp.from(now), java.sql.Timestamp.from(now)).stream().findFirst();
  }

  private Patient map(ResultSet result, int rowNumber) throws SQLException {
    Date birth = result.getDate("birth_date");
    return new Patient(
        result.getLong("source_id"),
        text(result, "document_number"),
        text(result, "medical_record_number"),
        text(result, "first_name"),
        text(result, "last_name"),
        birth == null ? null : birth.toLocalDate(),
        text(result, "sex"),
        text(result, "health_insurance"),
        text(result, "health_insurance_number"),
        text(result, "phone"),
        text(result, "email"),
        text(result, "address"),
        result.getBoolean("local_only"),
        result.getTimestamp("created_at").toInstant(),
        result.getTimestamp("updated_at").toInstant());
  }

  private String text(ResultSet result, String column) throws SQLException {
    String value = result.getString(column);
    return value == null ? "" : value;
  }

  public record Patient(
      long id,
      String dni,
      String medicalRecord,
      String firstName,
      String lastName,
      LocalDate birthDate,
      String sex,
      String insurance,
      String affiliateNumber,
      String phone,
      String email,
      String address,
      boolean localOnly,
      Instant createdAt,
      Instant updatedAt) {
    public String fullName() {
      String joined = String.join(", ", lastName, firstName).replaceAll("(^[, ]+|[, ]+$)", "");
      return joined.isBlank() ? Long.toString(id) : joined;
    }
  }

  public record NewPatient(
      String firstName,
      String lastName,
      String dni,
      String medicalRecord,
      LocalDate birthDate,
      String sex,
      String insurance,
      String affiliateNumber,
      String phone,
      String email,
      String address) {
  }
}
