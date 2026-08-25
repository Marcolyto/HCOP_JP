package ar.com.hexium.hcop.infusion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class InfusionRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final Clock clock;

  public InfusionRepository(JdbcTemplate jdbc, ObjectMapper mapper, Clock clock) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.clock = clock;
  }

  public List<Infusion> list(Long patientId, LocalDate date) {
    StringBuilder where = new StringBuilder(" WHERE 1=1");
    List<Object> parameters = new ArrayList<>();
    if (patientId != null) {
      where.append(" AND s.patient_id = ?");
      parameters.add(patientId);
    }
    if (date != null) {
      where.append(" AND s.scheduled_at >= ? AND s.scheduled_at < ?");
      parameters.add(Timestamp.from(date.atStartOfDay(clock.getZone()).toInstant()));
      parameters.add(Timestamp.from(date.plusDays(1).atStartOfDay(clock.getZone()).toInstant()));
    }
    return jdbc.query(selectSql() + where + " ORDER BY s.scheduled_at NULLS LAST, s.chair, s.id",
        this::map, parameters.toArray());
  }

  public Optional<Infusion> find(long id) {
    return jdbc.query(selectSql() + " WHERE s.id = ?", this::map, id).stream().findFirst();
  }

  public Optional<Infusion> findByCycle(long patientId, String treatmentId, int cycleNumber) {
    return jdbc.query(selectSql() + """
         WHERE s.patient_id = ? AND s.treatment_id = ? AND s.cycle_number = ?
           AND s.clinical_status <> 'cancelled'
         ORDER BY s.scheduled_at NULLS LAST, s.id DESC
         LIMIT 1
        """, this::map, patientId, treatmentId, cycleNumber).stream().findFirst();
  }

  public Optional<Infusion> findByApplication(
      long patientId, String treatmentId, int cycleNumber, int applicationDay) {
    return jdbc.query(selectSql() + """
         WHERE s.patient_id = ? AND s.treatment_id = ? AND s.cycle_number = ?
           AND s.application_day = ?
           AND s.clinical_status <> 'cancelled'
         ORDER BY s.scheduled_at NULLS LAST, s.id DESC
         LIMIT 1
        """, this::map, patientId, treatmentId, cycleNumber, applicationDay)
        .stream().findFirst();
  }

  public Infusion insert(NewInfusion input, long actorId) {
    Instant now = clock.instant();
    long id = jdbc.queryForObject("""
        INSERT INTO unified_infusion_sessions (
          patient_id, treatment_id, cycle_number, application_day,
          scheduled_at, chair, duration_minutes,
          clinical_status, pharmacy_status, administration_status, appointment_confirmed,
          notes, source_ref, revision, created_by, updated_by, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, NULLIF(?, ''), ?, ?, ?, ?, ?, NULLIF(?, ''),
                  CAST(? AS jsonb), 1, ?, ?, ?, ?)
        RETURNING id
        """, Long.class,
        input.patientId(), input.treatmentId(), input.cycleNumber(), input.applicationDay(),
        input.scheduledAt() == null ? null : Timestamp.from(input.scheduledAt()),
        input.chair(), input.durationMinutes(), input.clinicalStatus(), input.pharmacyStatus(),
        input.administrationStatus(), input.appointmentConfirmed(), input.notes(),
        input.sourceRef().toString(), actorId, actorId,
        Timestamp.from(now), Timestamp.from(now));
    for (Medication medication : input.medications()) insertMedication(id, medication, actorId, now);
    return find(id).orElseThrow();
  }

  public Optional<Infusion> update(long id, long expectedRevision, Patch patch, long actorId) {
    Instant now = clock.instant();
    int changed = jdbc.update("""
        UPDATE unified_infusion_sessions
           SET scheduled_at = CASE WHEN ? = 'cancelled' THEN NULL ELSE COALESCE(?, scheduled_at) END,
               chair = CASE WHEN ? = 'cancelled' THEN NULL ELSE COALESCE(NULLIF(?, ''), chair) END,
               duration_minutes = COALESCE(?, duration_minutes),
               clinical_status = COALESCE(NULLIF(?, ''), clinical_status),
               pharmacy_status = COALESCE(NULLIF(?, ''), pharmacy_status),
               administration_status = COALESCE(NULLIF(?, ''), administration_status),
               appointment_confirmed = COALESCE(?, appointment_confirmed),
               notes = COALESCE(?, notes),
               source_ref = COALESCE(CAST(? AS jsonb), source_ref),
               revision = revision + 1,
               updated_by = ?,
               updated_at = ?
         WHERE id = ? AND revision = ?
        """,
        patch.clinicalStatus(),
        patch.scheduledAt() == null ? null : Timestamp.from(patch.scheduledAt()),
        patch.clinicalStatus(), patch.chair(), patch.durationMinutes(), patch.clinicalStatus(), patch.pharmacyStatus(),
        patch.administrationStatus(), patch.appointmentConfirmed(), patch.notes(),
        patch.sourceRef() == null ? null : patch.sourceRef().toString(),
        actorId, Timestamp.from(now), id, expectedRevision);
    return changed == 0 ? Optional.empty() : find(id);
  }

  public List<MedicationView> medications(long infusionId) {
    return jdbc.query("""
        SELECT id, source_item_ref, drug_id, drug_name, prescribed_dose_text, dose_unit,
               route, preparation_status, administration_status, notes, revision
          FROM unified_infusion_medications
         WHERE infusion_session_id = ?
         ORDER BY id
        """, (result, row) -> new MedicationView(
            result.getLong("id"), text(result, "source_item_ref"), text(result, "drug_id"),
            text(result, "drug_name"), text(result, "prescribed_dose_text"), text(result, "dose_unit"),
            text(result, "route"), text(result, "preparation_status"),
            text(result, "administration_status"), text(result, "notes"), result.getLong("revision")),
        infusionId);
  }

  public List<Candidate> candidates(String query, boolean includeScheduled) {
    return candidates(query, includeScheduled, true);
  }

  public List<Candidate> candidates(
      String query, boolean includeScheduled, boolean onlySchedulingEligible) {
    List<Object> parameters = new ArrayList<>();
    parameters.add(includeScheduled);
    parameters.add(!onlySchedulingEligible);
    StringBuilder search = new StringBuilder();
    String normalizedQuery = normalizeSearch(query);
    if (!normalizedQuery.isBlank()) {
      String searchable = """
          translate(lower(concat_ws(' ',
            p.last_name, p.first_name, p.document_number, p.medical_record_number,
            t.scheme_name, t.diagnosis, l.drug_summary, l.application_drugs::text,
            'ciclo', l.cycle_number, 'dia', l.application_day,
            CAST(l.planned_date AS text), to_char(l.planned_date, 'DD/MM/YYYY')
          )), 'áéíóúüñ', 'aeiouun')
          """;
      for (String token : normalizedQuery.split("\\s+")) {
        if (token.isBlank()) continue;
        search.append(" AND ").append(searchable).append(" LIKE ?");
        parameters.add("%" + token + "%");
      }
    }
    return jdbc.query("""
        SELECT l.patient_id, l.treatment_id, l.cycle_number, l.application_day, l.planned_date,
               l.medication_state, l.prescription_state, l.notes AS logistics_notes,
               l.revision AS logistics_revision, l.duration_minutes, l.duration_source,
               l.drug_summary, l.application_drugs::text,
               aw.pharmacy_validation_status, aw.medication_source,
               aw.stock_reservation_status, aw.workflow_status AS application_workflow_status,
               aw.revision AS application_workflow_revision,
               p.document_number, p.medical_record_number, p.first_name, p.last_name,
               p.health_insurance, p.health_insurance_number,
               t.diagnosis, t.scheme_id, t.scheme_name, t.treatment_type, t.cycle_count, t.cycle_days,
               COALESCE(m.continuity_status, 'active') AS continuity_status,
               m.effective_from_cycle, m.suspension_reason, m.resume_date,
               COALESCE(m.prescription_required, false) AS prescription_required,
               m.revision AS management_revision
          FROM treatment_application_logistics l
          JOIN clinical_treatments t ON t.id = l.treatment_id AND t.patient_id = l.patient_id
          JOIN patients p ON p.source_id = l.patient_id
          LEFT JOIN treatment_application_workflows aw
            ON aw.patient_id = l.patient_id AND aw.treatment_id = l.treatment_id
           AND aw.cycle_number = l.cycle_number AND aw.application_day = l.application_day
          LEFT JOIN treatment_management_states m
            ON m.patient_id = l.patient_id AND m.treatment_id = l.treatment_id
         WHERE (? OR NOT EXISTS (
                 SELECT 1 FROM unified_infusion_sessions s
                  WHERE s.patient_id = l.patient_id
                    AND s.treatment_id = l.treatment_id
                    AND s.cycle_number = l.cycle_number
                    AND s.application_day = l.application_day
                    AND s.clinical_status <> 'cancelled'
               ))
           AND (
             ?
             OR (
               aw.pharmacy_validation_status = 'approved'
               AND (
                 (aw.medication_source = 'center_stock'
                   AND aw.stock_reservation_status = 'reserved')
                 OR aw.medication_source IN (
                   'patient_to_bring','patient_has_medication','received_center')
               )
             )
           )
        """ + search + """
         ORDER BY l.planned_date NULLS LAST, p.last_name, p.first_name,
                  l.cycle_number, l.application_day
         LIMIT 2000
        """, this::mapCandidate, parameters.toArray());
  }

  public ScheduleSettings scheduleSettings() {
    JsonNode definition = jdbc.query("""
        SELECT definition_json::text
          FROM clinical_configuration_items
         WHERE item_kind = 'day-hospital-settings' AND active = true
         ORDER BY updated_at DESC, id DESC
         LIMIT 1
        """, (result, row) -> mapper.readTree(result.getString("definition_json")))
        .stream().findFirst().orElseGet(mapper::createObjectNode);
    int chairCount = definition.path("chairCount").asInt(6);
    int slotMinutes = definition.path("slotMinutes").asInt(10);
    if (chairCount < 1 || chairCount > 200) chairCount = 6;
    if (!List.of(5, 10, 15, 20, 30).contains(slotMinutes)) slotMinutes = 10;
    String startTime = definition.path("startTime").asText(
        definition.path("workdayStart").asText("08:00"));
    String endTime = definition.path("endTime").asText(
        definition.path("workdayEnd").asText("16:00"));
    return new ScheduleSettings(chairCount, slotMinutes, startTime, endTime);
  }

  public Optional<Logistics> logistics(
      long patientId, String treatmentId, int cycleNumber, int applicationDay) {
    return jdbc.query("""
        SELECT patient_id, treatment_id, cycle_number, application_day, planned_date,
               medication_state, prescription_state, duration_minutes, duration_source,
               drug_summary, application_drugs::text, notes, revision, updated_at
          FROM treatment_application_logistics
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ? AND application_day = ?
        """, this::mapLogistics, patientId, treatmentId, cycleNumber, applicationDay)
        .stream().findFirst();
  }

  public Optional<Logistics> updateLogistics(
      long patientId, String treatmentId, int cycleNumber, int applicationDay, long expectedRevision,
      LocalDate plannedDate, String medicationState, String prescriptionState,
      String notes, long actorId) {
    Instant now = clock.instant();
    int changed = jdbc.update("""
        UPDATE treatment_application_logistics
           SET planned_date = COALESCE(?, planned_date),
               medication_state = COALESCE(NULLIF(?, ''), medication_state),
               prescription_state = COALESCE(NULLIF(?, ''), prescription_state),
               notes = COALESCE(?, notes),
               revision = revision + 1,
               updated_by = ?,
               updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND revision = ?
        """, plannedDate == null ? null : java.sql.Date.valueOf(plannedDate), medicationState,
        prescriptionState, notes, actorId, Timestamp.from(now),
        patientId, treatmentId, cycleNumber, applicationDay, expectedRevision);
    if (changed == 0) return Optional.empty();
    Logistics saved = logistics(patientId, treatmentId, cycleNumber, applicationDay).orElseThrow();
    synchronizeActiveSessionLogistics(saved, actorId, now);
    return Optional.of(saved);
  }

  private void synchronizeActiveSessionLogistics(
      Logistics logistics, long actorId, Instant now) {
    boolean medicationReceived = "received".equals(logistics.medicationState());
    boolean medicationWithPatient = "with_patient".equals(logistics.medicationState());
    boolean prescriptionConfirmed = "confirmed".equals(logistics.prescriptionState());
    jdbc.update("""
        UPDATE unified_infusion_sessions
           SET source_ref = source_ref || jsonb_build_object(
                 'scheduler',
                 COALESCE(source_ref -> 'scheduler', '{}'::jsonb) || jsonb_build_object(
                   'medicationState', ?,
                   'medicationReceived', ?,
                   'medicationWithPatient', ?,
                   'medicationLocation', CASE WHEN ? THEN 'patient' ELSE '' END,
                   'prescriptionConfirmed', ?
                 )
               ),
               revision = revision + 1,
               updated_by = ?,
               updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND clinical_status <> 'cancelled'
        """,
        logistics.medicationState(), medicationReceived, medicationWithPatient,
        medicationWithPatient, prescriptionConfirmed,
        actorId, Timestamp.from(now), logistics.patientId(), logistics.treatmentId(),
        logistics.cycleNumber(), logistics.applicationDay());
  }

  private void insertMedication(long infusionId, Medication medication, long actorId, Instant now) {
    jdbc.update("""
        INSERT INTO unified_infusion_medications (
          infusion_session_id, source_item_ref, drug_id, drug_name, prescribed_dose_text,
          dose_unit, route, preparation_status, administration_status, notes, revision,
          created_by, updated_by, created_at, updated_at
        ) VALUES (?, NULLIF(?, ''), NULLIF(?, ''), ?, ?, NULLIF(?, ''), NULLIF(?, ''),
                  ?, ?, NULLIF(?, ''), 1, ?, ?, ?, ?)
        """, infusionId, medication.sourceItemRef(), medication.drugId(), medication.drugName(),
        medication.prescribedDoseText(), medication.doseUnit(), medication.route(),
        medication.preparationStatus(), medication.administrationStatus(), medication.notes(),
        actorId, actorId, Timestamp.from(now), Timestamp.from(now));
  }

  private String selectSql() {
    return """
        SELECT s.id, s.patient_id, s.treatment_id, s.cycle_number, s.application_day,
               s.application_id,
               s.scheduled_at, s.chair, s.duration_minutes, s.clinical_status,
               s.pharmacy_status, s.administration_status, s.appointment_confirmed,
               s.notes, s.source_ref::text, s.revision, s.created_at, s.updated_at,
               p.document_number, p.medical_record_number, p.first_name, p.last_name,
               p.health_insurance, p.health_insurance_number,
               t.diagnosis, t.scheme_name, t.treatment_type, t.cycle_count, t.cycle_days
          FROM unified_infusion_sessions s
          JOIN patients p ON p.source_id = s.patient_id
          JOIN clinical_treatments t ON t.id = s.treatment_id
        """;
  }

  private Infusion map(ResultSet result, int row) throws SQLException {
    Timestamp scheduled = result.getTimestamp("scheduled_at");
    return new Infusion(
        result.getLong("id"), result.getLong("patient_id"), result.getString("treatment_id"),
        result.getInt("cycle_number"), result.getInt("application_day"),
        result.getObject("application_id") == null ? null : result.getLong("application_id"),
        scheduled == null ? null : scheduled.toInstant(), text(result, "chair"),
        result.getObject("duration_minutes") == null ? null : result.getInt("duration_minutes"),
        text(result, "clinical_status"), text(result, "pharmacy_status"),
        text(result, "administration_status"), result.getBoolean("appointment_confirmed"),
        text(result, "notes"), mapper.readTree(result.getString("source_ref")),
        result.getLong("revision"), result.getTimestamp("created_at").toInstant(),
        result.getTimestamp("updated_at").toInstant(), text(result, "document_number"),
        text(result, "medical_record_number"), text(result, "first_name"), text(result, "last_name"),
        text(result, "health_insurance"), text(result, "health_insurance_number"),
        text(result, "diagnosis"), text(result, "scheme_name"), text(result, "treatment_type"),
        result.getInt("cycle_count"), result.getObject("cycle_days") == null ? 0 : result.getInt("cycle_days"));
  }

  private Candidate mapCandidate(ResultSet result, int row) throws SQLException {
    java.sql.Date planned = result.getDate("planned_date");
    java.sql.Date resume = result.getDate("resume_date");
    return new Candidate(
        result.getLong("patient_id"), result.getString("treatment_id"),
        result.getInt("cycle_number"), result.getInt("application_day"),
        planned == null ? null : planned.toLocalDate(),
        text(result, "medication_state"), text(result, "prescription_state"),
        text(result, "logistics_notes"), result.getLong("logistics_revision"),
        text(result, "document_number"), text(result, "medical_record_number"),
        text(result, "first_name"), text(result, "last_name"),
        text(result, "health_insurance"), text(result, "health_insurance_number"),
        text(result, "diagnosis"), text(result, "scheme_id"), text(result, "scheme_name"),
        text(result, "treatment_type"),
        result.getInt("cycle_count"), result.getObject("cycle_days") == null ? 0 : result.getInt("cycle_days"),
        result.getInt("duration_minutes"), text(result, "duration_source"),
        text(result, "drug_summary"), mapper.readTree(result.getString("application_drugs")),
        text(result, "pharmacy_validation_status"), text(result, "medication_source"),
        text(result, "stock_reservation_status"), text(result, "application_workflow_status"),
        result.getObject("application_workflow_revision") == null
            ? null : result.getLong("application_workflow_revision"),
        text(result, "continuity_status"),
        result.getObject("effective_from_cycle") == null ? null : result.getInt("effective_from_cycle"),
        text(result, "suspension_reason"), resume == null ? null : resume.toLocalDate(),
        result.getBoolean("prescription_required"),
        result.getObject("management_revision") == null ? null : result.getLong("management_revision"));
  }

  private Logistics mapLogistics(ResultSet result, int row) throws SQLException {
    java.sql.Date planned = result.getDate("planned_date");
    return new Logistics(
        result.getLong("patient_id"), result.getString("treatment_id"),
        result.getInt("cycle_number"), result.getInt("application_day"),
        planned == null ? null : planned.toLocalDate(), text(result, "medication_state"),
        text(result, "prescription_state"), result.getInt("duration_minutes"),
        text(result, "duration_source"), text(result, "drug_summary"),
        mapper.readTree(result.getString("application_drugs")),
        text(result, "notes"), result.getLong("revision"),
        result.getTimestamp("updated_at").toInstant());
  }

  private String normalizeSearch(String value) {
    if (value == null || value.isBlank()) return "";
    return Normalizer.normalize(value.trim().toLowerCase(), Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .replaceAll("[^a-z0-9/.-]+", " ")
        .trim();
  }

  private String text(ResultSet result, String field) throws SQLException {
    String value = result.getString(field);
    return value == null ? "" : value;
  }

  public record NewInfusion(
      long patientId, String treatmentId, int cycleNumber, int applicationDay,
      Instant scheduledAt, String chair,
      Integer durationMinutes, String clinicalStatus, String pharmacyStatus,
      String administrationStatus, boolean appointmentConfirmed, String notes,
      JsonNode sourceRef, List<Medication> medications) {
  }

  public record Patch(
      Instant scheduledAt, String chair, Integer durationMinutes, String clinicalStatus,
      String pharmacyStatus, String administrationStatus, Boolean appointmentConfirmed,
      String notes, JsonNode sourceRef) {
  }

  public record Medication(
      String sourceItemRef, String drugId, String drugName, String prescribedDoseText,
      String doseUnit, String route, String preparationStatus, String administrationStatus,
      String notes) {
  }

  public record MedicationView(
      long id, String sourceItemRef, String drugId, String drugName, String prescribedDoseText,
      String doseUnit, String route, String preparationStatus, String administrationStatus,
      String notes, long revision) {
  }

  public record Infusion(
      long id, long patientId, String treatmentId, int cycleNumber, int applicationDay,
      Long applicationId,
      Instant scheduledAt, String chair, Integer durationMinutes, String clinicalStatus,
      String pharmacyStatus, String administrationStatus, boolean appointmentConfirmed,
      String notes, JsonNode sourceRef, long revision, Instant createdAt, Instant updatedAt,
      String patientDni, String medicalRecord, String firstName, String lastName,
      String insurance, String affiliateNumber, String diagnosis, String scheme,
      String treatmentType, int totalCycles, int cycleDays) {
    public String patientName() {
      return (lastName + ", " + firstName).replaceAll("(^[, ]+|[, ]+$)", "");
    }
  }

  public record Candidate(
      long patientId, String treatmentId, int cycleNumber, int applicationDay,
      LocalDate plannedDate,
      String medicationState, String prescriptionState, String logisticsNotes,
      long logisticsRevision, String patientDni, String medicalRecord, String firstName,
      String lastName, String insurance, String affiliateNumber, String diagnosis,
      String schemeId, String scheme, String treatmentType, int totalCycles, int cycleDays,
      Integer durationMinutes, String durationSource, String drugSummary, JsonNode applicationDrugs,
      String pharmacyValidationStatus, String medicationSource,
      String stockReservationStatus, String applicationWorkflowStatus,
      Long applicationWorkflowRevision,
      String continuityStatus, Integer effectiveFromCycle, String suspensionReason,
      LocalDate resumeDate, boolean prescriptionRequired, Long managementRevision) {
    public String patientName() {
      return (lastName + ", " + firstName).replaceAll("(^[, ]+|[, ]+$)", "");
    }
  }

  public record Logistics(
      long patientId, String treatmentId, int cycleNumber, int applicationDay,
      LocalDate plannedDate, String medicationState, String prescriptionState,
      int durationMinutes, String durationSource, String drugSummary, JsonNode applicationDrugs,
      String notes, long revision,
        Instant updatedAt) {
  }

  public record ScheduleSettings(
      int chairCount, int slotMinutes, String startTime, String endTime) {
  }
}
