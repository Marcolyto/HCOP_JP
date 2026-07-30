package ar.com.hexium.hcop.treatment;

import ar.com.hexium.hcop.infusion.TreatmentApplicationLogisticsService;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class TreatmentRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final TreatmentApplicationLogisticsService applicationLogistics;

  public TreatmentRepository(
      JdbcTemplate jdbc,
      ObjectMapper mapper,
      Clock clock,
      TreatmentApplicationLogisticsService applicationLogistics) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.clock = clock;
    this.applicationLogistics = applicationLogistics;
  }

  public List<Treatment> list(long patientId) {
    return jdbc.query(selectSql() + " WHERE patient_id = ? ORDER BY created_on DESC, created_at DESC",
        this::map, patientId);
  }

  public Optional<Treatment> find(long patientId, String treatmentId) {
    return jdbc.query(selectSql() + " WHERE patient_id = ? AND id = ?", this::map, patientId, treatmentId)
        .stream().findFirst();
  }

  public Optional<Treatment> find(String treatmentId) {
    return jdbc.query(selectSql() + " WHERE id = ?", this::map, treatmentId)
        .stream().findFirst();
  }

  public Optional<Treatment> findByClinicalEntryId(long patientId, String clinicalEntryId) {
    if (clinicalEntryId == null || clinicalEntryId.isBlank()) return Optional.empty();
    return jdbc.query(selectSql() + """
         WHERE patient_id = ? AND payload ->> 'clinicalEntryId' = ?
        """, this::map, patientId, clinicalEntryId).stream().findFirst();
  }

  public InsertResult insert(NewTreatment input, long actorId) {
    Instant now = clock.instant();
    int inserted = jdbc.update("""
        INSERT INTO clinical_treatments (
          id, patient_id, diagnosis_id, created_on, first_cycle_date, initial_cycle,
          cycle_count, cycle_days, treatment_type, intent, diagnosis, scheme_id,
          scheme_name, oncologist, treatment_status, consent_status, consent_available,
          estimated_duration_minutes, payload, created_by, updated_by, created_at, updated_at
        ) VALUES (?, ?, NULLIF(?, ''), ?, ?, ?, ?, ?, NULLIF(?, ''), NULLIF(?, ''),
                  NULLIF(?, ''), NULLIF(?, ''), ?, NULLIF(?, ''), ?, NULLIF(?, ''),
                  ?, ?, CAST(? AS jsonb), ?, ?, ?, ?)
        ON CONFLICT DO NOTHING
        """,
        input.id(), input.patientId(), input.diagnosisId(), Date.valueOf(input.createdOn()),
        input.firstCycleDate() == null ? null : Date.valueOf(input.firstCycleDate()),
        input.initialCycle(), input.cycleCount(), input.cycleDays() > 0 ? input.cycleDays() : null,
        input.treatmentType(), input.intent(), input.diagnosis(), input.schemeId(),
        input.schemeName(), input.oncologist(), input.status(), input.consentStatus(),
        input.consentAvailable(), input.durationMinutes(), input.payload().toString(),
        actorId, actorId, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
    if (inserted == 0) {
      String entryId = input.payload().path("clinicalEntryId").asText("");
      Treatment existing = findByClinicalEntryId(input.patientId(), entryId)
          .orElseThrow(() -> new IllegalStateException(
              "No se pudo recuperar el tratamiento después de un reintento idempotente."));
      return new InsertResult(existing, false);
    }
    jdbc.update("""
        INSERT INTO treatment_details (treatment_id, detail_json, revision, updated_by, updated_at)
        VALUES (?, CAST(? AS jsonb), 1, ?, ?)
        """, input.id(), input.detail().toString(), actorId, java.sql.Timestamp.from(now));
    for (int cycle = input.initialCycle(); cycle < input.initialCycle() + input.cycleCount(); cycle++) {
      LocalDate planned = input.firstCycleDate() == null || input.cycleDays() < 1
          ? null
          : input.firstCycleDate().plusDays((long) (cycle - input.initialCycle()) * input.cycleDays());
      jdbc.update("""
          INSERT INTO treatment_cycle_logistics (
            patient_id, treatment_id, cycle_number, planned_date, medication_state,
            prescription_state, revision, updated_by, created_at, updated_at
          ) VALUES (?, ?, ?, ?, 'pending', 'confirmed', 1, ?, ?, ?)
          """, input.patientId(), input.id(), cycle,
          planned == null ? null : Date.valueOf(planned), actorId,
           java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
    }
    applicationLogistics.synchronizeTreatment(input.id());
    return new InsertResult(find(input.patientId(), input.id()).orElseThrow(), true);
  }

  public JsonNode detail(String treatmentId) {
    return jdbc.query("""
        SELECT detail_json::text FROM treatment_details WHERE treatment_id = ?
        """, (result, row) -> mapper.readTree(result.getString(1)), treatmentId)
        .stream().findFirst().orElse(mapper.createObjectNode());
  }

  private String selectSql() {
    return """
        SELECT id, patient_id, diagnosis_id, created_on, first_cycle_date, initial_cycle,
               cycle_count, cycle_days, treatment_type, intent, diagnosis, scheme_id,
               scheme_name, oncologist, treatment_status, consent_status, consent_available,
               estimated_duration_minutes, payload::text, revision, created_at, updated_at
          FROM clinical_treatments
        """;
  }

  private Treatment map(ResultSet result, int rowNumber) throws SQLException {
    Date first = result.getDate("first_cycle_date");
    return new Treatment(
        result.getString("id"),
        result.getLong("patient_id"),
        text(result, "diagnosis_id"),
        result.getDate("created_on").toLocalDate(),
        first == null ? null : first.toLocalDate(),
        result.getInt("initial_cycle"),
        result.getInt("cycle_count"),
        result.getObject("cycle_days") == null ? 0 : result.getInt("cycle_days"),
        text(result, "treatment_type"),
        text(result, "intent"),
        text(result, "diagnosis"),
        text(result, "scheme_id"),
        text(result, "scheme_name"),
        text(result, "oncologist"),
        text(result, "treatment_status"),
        text(result, "consent_status"),
        result.getBoolean("consent_available"),
        result.getObject("estimated_duration_minutes") == null
            ? null : result.getInt("estimated_duration_minutes"),
        mapper.readTree(result.getString("payload")),
        result.getLong("revision"),
        result.getTimestamp("created_at").toInstant(),
        result.getTimestamp("updated_at").toInstant());
  }

  private String text(ResultSet result, String field) throws SQLException {
    String value = result.getString(field);
    return value == null ? "" : value;
  }

  public record NewTreatment(
      String id, long patientId, String diagnosisId, LocalDate createdOn, LocalDate firstCycleDate,
      int initialCycle, int cycleCount, int cycleDays, String treatmentType, String intent,
      String diagnosis, String schemeId, String schemeName, String oncologist, String status,
      String consentStatus, boolean consentAvailable, Integer durationMinutes, JsonNode payload,
      JsonNode detail) {
  }

  public record InsertResult(Treatment treatment, boolean created) {
  }

  public record Treatment(
      String id, long patientId, String diagnosisId, LocalDate createdOn, LocalDate firstCycleDate,
      int initialCycle, int cycleCount, int cycleDays, String treatmentType, String intent,
      String diagnosis, String schemeId, String schemeName, String oncologist, String status,
      String consentStatus, boolean consentAvailable, Integer durationMinutes, JsonNode payload,
      long revision, Instant createdAt, Instant updatedAt) {
  }
}
