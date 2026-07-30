package ar.com.hexium.hcop.workflow;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class TreatmentWorkflowRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public TreatmentWorkflowRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public boolean treatmentExists(long patientId, String treatmentId) {
    Integer count = jdbc.queryForObject("""
        SELECT count(*) FROM clinical_treatments WHERE patient_id = ? AND id = ?
        """, Integer.class, patientId, treatmentId);
    return count != null && count == 1;
  }

  public TreatmentSummary treatment(long patientId, String treatmentId) {
    return jdbc.query("""
        SELECT t.patient_id, t.id, t.scheme_name, t.diagnosis, t.treatment_status,
               t.initial_cycle, t.cycle_count,
               p.document_number, concat_ws(', ', p.last_name, p.first_name) AS patient_name
          FROM clinical_treatments t
          JOIN patients p ON p.source_id = t.patient_id
         WHERE t.patient_id = ? AND t.id = ?
        """, this::mapTreatment, patientId, treatmentId).stream().findFirst().orElseThrow();
  }

  public ManagementState upsertManagement(
      long patientId, String treatmentId, String status, Integer cycle, String reason,
      LocalDate resumeDate, boolean prescriptionRequired, long actorId) {
    jdbc.update("""
        INSERT INTO treatment_management_states (
          patient_id, treatment_id, continuity_status, effective_from_cycle,
          suspension_reason, resume_date, prescription_required, revision,
          updated_by, created_at, updated_at
        ) VALUES (?, ?, ?, ?, NULLIF(?, ''), ?, ?, 1, ?, clock_timestamp(), clock_timestamp())
        ON CONFLICT (patient_id, treatment_id) DO UPDATE SET
          continuity_status = EXCLUDED.continuity_status,
          effective_from_cycle = EXCLUDED.effective_from_cycle,
          suspension_reason = EXCLUDED.suspension_reason,
          resume_date = EXCLUDED.resume_date,
          prescription_required = EXCLUDED.prescription_required,
          revision = treatment_management_states.revision + 1,
          updated_by = EXCLUDED.updated_by,
          updated_at = clock_timestamp()
        """, patientId, treatmentId, status, cycle, reason,
        resumeDate == null ? null : Date.valueOf(resumeDate), prescriptionRequired, actorId);
    return management(patientId, treatmentId).orElseThrow();
  }

  public Optional<ManagementState> management(long patientId, String treatmentId) {
    return jdbc.query("""
        SELECT patient_id, treatment_id, continuity_status, effective_from_cycle,
               suspension_reason, resume_date, prescription_required, revision, updated_at
          FROM treatment_management_states
         WHERE patient_id = ? AND treatment_id = ?
        """, this::mapManagement, patientId, treatmentId).stream().findFirst();
  }

  public long insertRequest(
      String type, long patientId, String treatmentId, int cycle, long requestedBy,
      long assignedTo, String message, JsonNode context) {
    return jdbc.queryForObject("""
        INSERT INTO treatment_workflow_requests (
          request_type, status, patient_id, treatment_id, cycle_number,
          requested_by_user_id, assigned_to_user_id, message, context_json
        ) VALUES (?, 'pending', ?, ?, ?, ?, ?, NULLIF(?, ''), CAST(? AS jsonb))
        RETURNING id
        """, Long.class, type, patientId, treatmentId, cycle, requestedBy, assignedTo,
        message, context.toString());
  }

  public Optional<Request> request(long id) {
    return jdbc.query(requestSelect() + " WHERE w.id = ?", this::mapRequest, id).stream().findFirst();
  }

  public List<Request> inbox(long userId) {
    return jdbc.query(requestSelect() + """
         WHERE w.assigned_to_user_id = ? AND w.status = 'pending'
         ORDER BY w.seen_at NULLS FIRST, w.created_at
        """, this::mapRequest, userId);
  }

  public Optional<Request> markSeen(long id, long userId, Instant now) {
    jdbc.update("""
        UPDATE treatment_workflow_requests
           SET seen_at = COALESCE(seen_at, ?), updated_at = clock_timestamp()
         WHERE id = ? AND assigned_to_user_id = ? AND status = 'pending'
        """, Timestamp.from(now), id, userId);
    return request(id);
  }

  public Optional<Request> resolve(
      long id, long assignedUserId, String resolution, String reason, LocalDate resumeDate, Instant now) {
    int changed = jdbc.update("""
        UPDATE treatment_workflow_requests
           SET status = 'resolved', resolution = ?, resolution_reason = NULLIF(?, ''),
               resume_date = ?, resolved_at = ?, resolved_by_user_id = ?,
               seen_at = COALESCE(seen_at, ?), updated_at = ?
         WHERE id = ? AND assigned_to_user_id = ? AND status = 'pending'
        """, resolution, reason, resumeDate == null ? null : Date.valueOf(resumeDate),
        Timestamp.from(now), assignedUserId, Timestamp.from(now), Timestamp.from(now),
        id, assignedUserId);
    return changed == 0 ? Optional.empty() : request(id);
  }

  public void updatePrescriptionState(
      long patientId, String treatmentId, int cycle, String state, long actorId) {
    jdbc.update("""
        UPDATE treatment_cycle_logistics
           SET prescription_state = ?, revision = revision + 1,
               updated_by = ?, updated_at = clock_timestamp()
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
        """, state, actorId, patientId, treatmentId, cycle);
  }

  public String prescriptionState(long patientId, String treatmentId, int cycle) {
    return jdbc.query("""
        SELECT prescription_state
          FROM treatment_cycle_logistics
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
        """, (result, row) -> text(result, "prescription_state"),
        patientId, treatmentId, cycle).stream().findFirst().orElse("");
  }

  public void insertEvent(
      Long requestId, long patientId, String treatmentId, Integer cycle,
      String eventType, long actorId, JsonNode event) {
    jdbc.update("""
        INSERT INTO clinical_workflow_events (
          id, workflow_request_id, patient_id, treatment_id, cycle_number,
          event_type, actor_user_id, event_json
        ) VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
        """, UUID.randomUUID(), requestId, patientId, treatmentId, cycle, eventType, actorId, event.toString());
  }

  private String requestSelect() {
    return """
        SELECT w.id, w.request_type, w.status, w.patient_id, w.treatment_id, w.cycle_number,
               w.requested_by_user_id, w.assigned_to_user_id, w.message,
               w.context_json::text, w.resolution, w.resolution_reason, w.resume_date,
               w.seen_at, w.resolved_at, w.resolved_by_user_id, w.created_at, w.updated_at,
               p.document_number, concat_ws(', ', p.last_name, p.first_name) AS patient_name,
               t.scheme_name, t.diagnosis,
               requester.display_name AS requested_by_name,
               assignee.display_name AS assigned_to_name
          FROM treatment_workflow_requests w
          JOIN patients p ON p.source_id = w.patient_id
          JOIN clinical_treatments t ON t.id = w.treatment_id AND t.patient_id = w.patient_id
          JOIN local_users requester ON requester.id = w.requested_by_user_id
          JOIN local_users assignee ON assignee.id = w.assigned_to_user_id
        """;
  }

  private TreatmentSummary mapTreatment(ResultSet result, int row) throws SQLException {
    return new TreatmentSummary(
        result.getLong("patient_id"), result.getString("id"), result.getString("scheme_name"),
        text(result, "diagnosis"), text(result, "treatment_status"), text(result, "document_number"),
        text(result, "patient_name"), result.getInt("initial_cycle"), result.getInt("cycle_count"));
  }

  private ManagementState mapManagement(ResultSet result, int row) throws SQLException {
    Date resume = result.getDate("resume_date");
    Object cycle = result.getObject("effective_from_cycle");
    return new ManagementState(
        result.getLong("patient_id"), result.getString("treatment_id"),
        result.getString("continuity_status"), cycle == null ? null : result.getInt("effective_from_cycle"),
        text(result, "suspension_reason"), resume == null ? null : resume.toLocalDate(),
        result.getBoolean("prescription_required"), result.getLong("revision"),
        result.getTimestamp("updated_at").toInstant());
  }

  private Request mapRequest(ResultSet result, int row) throws SQLException {
    Date resume = result.getDate("resume_date");
    return new Request(
        result.getLong("id"), result.getString("request_type"), result.getString("status"),
        result.getLong("patient_id"), result.getString("treatment_id"), result.getInt("cycle_number"),
        result.getLong("requested_by_user_id"), result.getLong("assigned_to_user_id"),
        text(result, "message"), mapper.readTree(result.getString("context_json")),
        text(result, "resolution"), text(result, "resolution_reason"),
        resume == null ? null : resume.toLocalDate(), instant(result, "seen_at"),
        instant(result, "resolved_at"), nullableLong(result, "resolved_by_user_id"),
        result.getTimestamp("created_at").toInstant(), result.getTimestamp("updated_at").toInstant(),
        text(result, "document_number"), text(result, "patient_name"), text(result, "scheme_name"),
        text(result, "diagnosis"), text(result, "requested_by_name"), text(result, "assigned_to_name"));
  }

  private String text(ResultSet result, String column) throws SQLException {
    String value = result.getString(column);
    return value == null ? "" : value;
  }

  private Instant instant(ResultSet result, String column) throws SQLException {
    Timestamp value = result.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }

  private Long nullableLong(ResultSet result, String column) throws SQLException {
    Object value = result.getObject(column);
    return value == null ? null : result.getLong(column);
  }

  public record TreatmentSummary(
      long patientId, String treatmentId, String scheme, String diagnosis, String status,
      String patientDni, String patientName, int initialCycle, int cycleCount) {
  }

  public record ManagementState(
      long patientId, String treatmentId, String status, Integer effectiveFromCycle,
      String reason, LocalDate resumeDate, boolean prescriptionRequired, long revision,
      Instant updatedAt) {
  }

  public record Request(
      long id, String type, String status, long patientId, String treatmentId, int cycleNumber,
      long requestedBy, long assignedTo, String message, JsonNode context, String resolution,
      String resolutionReason, LocalDate resumeDate, Instant seenAt, Instant resolvedAt,
      Long resolvedBy, Instant createdAt, Instant updatedAt, String patientDni, String patientName,
      String scheme, String diagnosis, String requestedByName, String assignedToName) {
  }
}
