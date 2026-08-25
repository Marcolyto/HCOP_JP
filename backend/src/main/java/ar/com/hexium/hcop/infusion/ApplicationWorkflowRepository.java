package ar.com.hexium.hcop.infusion;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
class ApplicationWorkflowRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final Clock clock;

  ApplicationWorkflowRepository(JdbcTemplate jdbc, ObjectMapper mapper, Clock clock) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.clock = clock;
  }

  void ensureWorkflowRows() {
    jdbc.update("""
        INSERT INTO treatment_application_workflows (
          patient_id, treatment_id, cycle_number, application_day,
          workflow_status, medication_source, pharmacy_validation_status,
          stock_reservation_status, clinical_authorization_status,
          preparation_status, administration_status,
          updated_by, created_at, updated_at
        )
        SELECT l.patient_id, l.treatment_id, l.cycle_number, l.application_day,
               CASE
                 WHEN session.administration_status = 'completed'
                   OR session.clinical_status = 'completed' THEN 'completed'
                 WHEN session.administration_status = 'in_progress' THEN 'in_administration'
                 WHEN session.pharmacy_status = 'in_preparation' THEN 'in_preparation'
                 WHEN session.pharmacy_status IN ('ready','released')
                   AND session.clinical_status IN ('ready','in_progress','observation')
                   THEN 'clinically_authorized'
                 WHEN session.pharmacy_status IN ('ready','released') THEN 'scheduled'
                 WHEN session.id IS NOT NULL THEN 'scheduled'
                 ELSE 'prescribed'
               END,
               CASE l.medication_state
                 WHEN 'with_patient' THEN 'patient_has_medication'
                 WHEN 'received' THEN 'received_center'
                 ELSE 'pending_supplier'
               END,
               'pending',
               CASE WHEN l.medication_state IN ('with_patient','received')
                    THEN 'not_applicable' ELSE 'none' END,
               CASE
                 WHEN session.administration_status IN ('in_progress','completed')
                   OR session.clinical_status IN ('ready','in_progress','observation','completed')
                   THEN 'passed'
                 ELSE 'pending'
               END,
               CASE session.pharmacy_status
                 WHEN 'in_preparation' THEN 'in_preparation'
                 WHEN 'ready' THEN CASE
                   WHEN session.administration_status IN ('in_progress','completed')
                     OR session.clinical_status = 'completed' THEN 'prepared'
                   ELSE 'not_started'
                 END
                 WHEN 'released' THEN CASE
                   WHEN session.administration_status IN ('in_progress','completed')
                     OR session.clinical_status = 'completed' THEN 'released'
                   ELSE 'not_started'
                 END
                 WHEN 'cancelled' THEN 'cancelled'
                 ELSE 'not_started'
               END,
               COALESCE(session.administration_status, 'not_started'),
               l.updated_by, l.created_at, l.updated_at
          FROM treatment_application_logistics l
          LEFT JOIN LATERAL (
            SELECT s.id, s.clinical_status, s.pharmacy_status, s.administration_status
              FROM unified_infusion_sessions s
             WHERE s.patient_id = l.patient_id
               AND s.treatment_id = l.treatment_id
               AND s.cycle_number = l.cycle_number
               AND s.application_day = l.application_day
               AND s.clinical_status <> 'cancelled'
             ORDER BY s.scheduled_at DESC NULLS LAST, s.id DESC
             LIMIT 1
          ) session ON true
        ON CONFLICT DO NOTHING
        """);
  }

  Optional<Application> lock(Key key) {
    List<Application> locked = jdbc.query(
        selectSql() + keyWhere() + " FOR UPDATE OF w",
        this::mapApplication,
        key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay());
    return locked.stream().findFirst();
  }

  Optional<Application> find(Key key) {
    return jdbc.query(
        selectSql() + keyWhere(),
        this::mapApplication,
        key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay())
        .stream().findFirst();
  }

  Optional<ScheduleGate> scheduleGate(Key key) {
    return jdbc.query("""
        SELECT w.pharmacy_validation_status, w.medication_source, w.stock_reservation_status,
               l.prescription_state,
               CASE
                 WHEN m.effective_from_cycle IS NULL OR w.cycle_number >= m.effective_from_cycle
                   THEN COALESCE(m.continuity_status, 'active')
                 ELSE 'active'
               END AS continuity_status,
               CASE
                 WHEN m.effective_from_cycle IS NULL OR w.cycle_number >= m.effective_from_cycle
                   THEN COALESCE(m.prescription_required, false)
                 ELSE false
               END AS prescription_required,
               clinical_authorization_status, clinical_authorization_reason,
               clinical_assessment::text, preparation_status, administration_status,
               workflow_status, w.revision
          FROM treatment_application_workflows w
          JOIN treatment_application_logistics l
            ON l.patient_id = w.patient_id AND l.treatment_id = w.treatment_id
           AND l.cycle_number = w.cycle_number AND l.application_day = w.application_day
          LEFT JOIN treatment_management_states m
            ON m.patient_id = w.patient_id AND m.treatment_id = w.treatment_id
         WHERE w.patient_id = ? AND w.treatment_id = ? AND w.cycle_number = ?
           AND w.application_day = ?
         FOR UPDATE OF w
        """, (result, row) -> new ScheduleGate(
            text(result, "prescription_state"),
            text(result, "continuity_status"),
            result.getBoolean("prescription_required"),
            text(result, "pharmacy_validation_status"),
            text(result, "medication_source"),
            text(result, "stock_reservation_status"),
            text(result, "clinical_authorization_status"),
            text(result, "clinical_authorization_reason"),
            mapper.readTree(result.getString("clinical_assessment")),
            text(result, "preparation_status"),
            text(result, "administration_status"),
            text(result, "workflow_status"),
            result.getLong("revision")),
        key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay())
        .stream().findFirst();
  }

  boolean markAppointmentScheduled(
      Key key, long expectedRevision, long actorId, Instant now) {
    return jdbc.update("""
        UPDATE treatment_application_workflows
           SET workflow_status = 'triage_pending',
               clinical_authorization_status = 'pending',
               clinical_authorization_reason = NULL,
               clinical_assessment = '{}'::jsonb,
               clinically_authorized_by = NULL,
               clinically_authorized_at = NULL,
               revision = revision + 1,
               updated_by = ?,
               updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND revision = ?
           AND clinical_authorization_status IN ('pending','failed')
           AND preparation_status IN ('not_started','cancelled')
           AND administration_status = 'not_started'
        """, actorId, Timestamp.from(now), key.patientId(), key.treatmentId(),
        key.cycleNumber(), key.applicationDay(), expectedRevision) == 1;
  }

  boolean markAppointmentRemoved(Key key, long expectedRevision, long actorId, Instant now) {
    return jdbc.update("""
        UPDATE treatment_application_workflows
           SET workflow_status = CASE
                 WHEN medication_source IN ('patient_has_medication','received_center')
                   OR (medication_source = 'center_stock'
                     AND stock_reservation_status = 'reserved')
                   THEN 'medication_ready'
                 ELSE 'medication_pending'
               END,
               revision = revision + 1,
               updated_by = ?,
               updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ?
           AND clinical_authorization_status IN ('pending','failed')
           AND preparation_status IN ('not_started','cancelled')
           AND administration_status = 'not_started'
           AND revision = ?
        """, actorId, Timestamp.from(now), key.patientId(), key.treatmentId(),
        key.cycleNumber(), key.applicationDay(), expectedRevision) == 1;
  }

  List<Application> list(String queue, LocalDate date, String query, String medicationSource) {
    StringBuilder where = new StringBuilder(
        Set.of("applications", "administration").contains(queue)
            ? " WHERE TRUE"
            : " WHERE w.administration_status <> 'completed'");
    List<Object> parameters = new ArrayList<>();
    switch (queue) {
      case "applications" -> where.append(" AND s.id IS NOT NULL");
      case "pharmacy" -> where.append("""
           AND w.workflow_status <> 'cancelled'
          """);
      case "triage" -> where.append("""
           AND s.id IS NOT NULL
           AND w.preparation_status IN ('not_started','cancelled')
          """);
      case "preparation" -> where.append("""
           AND s.id IS NOT NULL
           AND w.clinical_authorization_status = 'passed'
           AND w.preparation_status IN ('not_started','in_preparation','prepared','released')
          """);
      case "administration" -> where.append("""
           AND s.id IS NOT NULL
           AND w.clinical_authorization_status = 'passed'
           AND w.preparation_status = 'released'
          """);
      default -> throw new IllegalArgumentException("Unknown workflow queue");
    }
    if (date != null) {
      if ("pharmacy".equals(queue)) {
        where.append(" AND l.planned_date = ?");
        parameters.add(Date.valueOf(date));
      } else {
        Instant from = date.atStartOfDay(clock.getZone()).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        where.append(" AND s.scheduled_at >= ? AND s.scheduled_at < ?");
        parameters.add(Timestamp.from(from));
        parameters.add(Timestamp.from(to));
      }
    }
    String normalizedSource = medicationSource == null ? "" : medicationSource.trim();
    if (!normalizedSource.isBlank()) {
      where.append(" AND w.medication_source = ?");
      parameters.add(normalizedSource);
    }
    String normalizedQuery = normalizeSearch(query);
    if (!normalizedQuery.isBlank()) {
      String searchable = """
          translate(lower(concat_ws(' ',
            p.last_name, p.first_name, p.document_number, p.medical_record_number,
            t.scheme_name, t.diagnosis, l.drug_summary, s.chair,
            'ciclo', l.cycle_number, 'dia', l.application_day,
            CAST(l.planned_date AS text), to_char(l.planned_date, 'DD/MM/YYYY')
          )), 'áéíóúüñ', 'aeiouun')
          """;
      for (String token : normalizedQuery.split("\\s+")) {
        if (token.isBlank()) continue;
        where.append(" AND ").append(searchable).append(" LIKE ?");
        parameters.add("%" + token + "%");
      }
    }
    String order = "pharmacy".equals(queue)
        ? " ORDER BY l.planned_date NULLS LAST, p.last_name, p.first_name, l.cycle_number, l.application_day"
        : " ORDER BY s.scheduled_at NULLS LAST, s.chair, p.last_name, p.first_name";
    return jdbc.query(selectSql() + where + order + " LIMIT 2000",
        this::mapApplication, parameters.toArray());
  }

  boolean updatePharmacyValidation(
      Key key, long expectedRevision, boolean approved, String source, String notes,
      long actorId, Instant now) {
    String validation = approved ? "approved" : "rejected";
    String workflow = !approved
        ? "pharmacy_rejected"
        : Set.of("patient_has_medication", "received_center").contains(source)
            ? "medication_ready"
            : "medication_pending";
    String stock = switch (source) {
      case "patient_has_medication", "received_center" -> "not_applicable";
      default -> "none";
    };
    return jdbc.update("""
        UPDATE treatment_application_workflows
           SET pharmacy_validation_status = ?,
               pharmacy_validation_notes = NULLIF(?, ''),
               pharmacy_validated_by = ?,
               pharmacy_validated_at = ?,
               medication_source = ?,
               stock_reservation_status = CASE
                 WHEN stock_reservation_status = 'reserved'
                   AND medication_source = ?
                   AND ? = 'center_stock'
                 THEN 'reserved'
                 ELSE ?
               END,
               workflow_status = CASE
                 WHEN stock_reservation_status = 'reserved'
                   AND medication_source = ?
                   AND ? = 'center_stock'
                 THEN 'medication_ready'
                 ELSE ?
               END,
               revision = revision + 1,
               updated_by = ?,
               updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND revision = ?
        """, validation, notes, actorId, Timestamp.from(now),
        source, source, source, stock, source, source, workflow,
        actorId, Timestamp.from(now), key.patientId(), key.treatmentId(),
        key.cycleNumber(), key.applicationDay(), expectedRevision) == 1;
  }

  boolean updateReservationStatus(
      Key key, long expectedRevision, String status, String notes, long actorId, Instant now) {
    boolean reserved = "reserved".equals(status);
    return jdbc.update("""
        UPDATE treatment_application_workflows
           SET stock_reservation_status = ?,
               stock_reservation_notes = NULLIF(?, ''),
               stock_reserved_by = CASE WHEN ? THEN ? ELSE stock_reserved_by END,
               stock_reserved_at = CASE WHEN ? THEN ? ELSE stock_reserved_at END,
               stock_released_by = CASE WHEN ? THEN stock_released_by ELSE ? END,
               stock_released_at = CASE WHEN ? THEN stock_released_at ELSE ? END,
               workflow_status = CASE
                 WHEN ? THEN 'medication_ready'
                 WHEN clinical_authorization_status = 'failed' THEN 'postponed'
                 ELSE 'medication_pending'
               END,
               revision = revision + 1,
               updated_by = ?,
               updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND revision = ?
        """, status, notes, reserved, actorId, reserved, Timestamp.from(now),
        reserved, actorId, reserved, Timestamp.from(now), reserved,
        actorId, Timestamp.from(now), key.patientId(), key.treatmentId(),
        key.cycleNumber(), key.applicationDay(), expectedRevision) == 1;
  }

  boolean updateClinicalAuthorization(
      Key key, long expectedRevision, boolean passed, String reason, JsonNode assessment,
      LocalDate rescheduledDate, long actorId, Instant now) {
    int changed = jdbc.update("""
        UPDATE treatment_application_workflows
           SET clinical_authorization_status = ?,
               clinical_authorization_reason = NULLIF(?, ''),
               clinical_assessment = CAST(? AS jsonb),
               clinically_authorized_by = ?,
               clinically_authorized_at = ?,
               workflow_status = ?,
               stock_reservation_status = CASE
                 WHEN ? THEN stock_reservation_status
                 WHEN stock_reservation_status = 'reserved' THEN 'released'
                 ELSE stock_reservation_status
               END,
               stock_released_by = CASE
                 WHEN NOT ? AND stock_reservation_status = 'reserved' THEN ?
                 ELSE stock_released_by
               END,
               stock_released_at = CASE
                 WHEN NOT ? AND stock_reservation_status = 'reserved' THEN ?
                 ELSE stock_released_at
               END,
               revision = revision + 1,
               updated_by = ?,
               updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND revision = ?
        """, passed ? "passed" : "failed", reason, assessment.toString(),
        actorId, Timestamp.from(now), passed ? "clinically_authorized" : "postponed",
        passed, passed, actorId, passed, Timestamp.from(now), actorId, Timestamp.from(now),
        key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay(),
        expectedRevision);
    if (changed == 1 && rescheduledDate != null) {
      jdbc.update("""
          UPDATE treatment_application_logistics
             SET planned_date = ?, revision = revision + 1,
                 updated_by = ?, updated_at = ?
           WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
             AND application_day = ?
          """, Date.valueOf(rescheduledDate), actorId, Timestamp.from(now),
          key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay());
    }
    return changed == 1;
  }

  boolean updatePreparation(
      Key key, long expectedRevision, String status, JsonNode data, Instant expiresAt,
      Long verifiedBy, long actorId, Instant now) {
    String workflow = switch (status) {
      case "in_preparation" -> "in_preparation";
      case "prepared" -> "prepared";
      case "released" -> "released";
      default -> "clinically_authorized";
    };
    return jdbc.update("""
        UPDATE treatment_application_workflows
           SET preparation_status = ?,
               preparation_data = CASE WHEN ? = '{}' THEN preparation_data ELSE CAST(? AS jsonb) END,
               preparation_started_by = CASE
                 WHEN ? IN ('in_preparation','prepared') THEN COALESCE(preparation_started_by, ?)
                 ELSE preparation_started_by END,
               preparation_started_at = CASE
                 WHEN ? IN ('in_preparation','prepared') THEN COALESCE(preparation_started_at, ?)
                 ELSE preparation_started_at END,
               prepared_by = CASE WHEN ? = 'prepared' THEN ? ELSE prepared_by END,
               preparation_verified_by = CASE
                 WHEN ? = 'prepared' THEN ? ELSE preparation_verified_by END,
               prepared_at = CASE WHEN ? = 'prepared' THEN ? ELSE prepared_at END,
               preparation_released_by = CASE WHEN ? = 'released' THEN ? ELSE preparation_released_by END,
               preparation_released_at = CASE WHEN ? = 'released' THEN ? ELSE preparation_released_at END,
               preparation_expires_at = COALESCE(?, preparation_expires_at),
               stock_reservation_status = CASE
                 WHEN ? = 'prepared' AND stock_reservation_status = 'reserved' THEN 'consumed'
                 ELSE stock_reservation_status
               END,
               workflow_status = ?,
               revision = revision + 1,
               updated_by = ?,
               updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND revision = ?
        """, status, data.toString(), data.toString(),
        status, actorId, status, Timestamp.from(now),
        status, actorId, status, verifiedBy, status, Timestamp.from(now),
        status, actorId, status, Timestamp.from(now),
        expiresAt == null ? null : Timestamp.from(expiresAt), status, workflow,
        actorId, Timestamp.from(now), key.patientId(), key.treatmentId(),
        key.cycleNumber(), key.applicationDay(), expectedRevision) == 1;
  }

  boolean resetCancelledPreparation(
      Key key, long expectedRevision, long actorId, Instant now) {
    int changed = jdbc.update("""
        UPDATE treatment_application_workflows
           SET preparation_data = '{}'::jsonb,
               preparation_started_by = NULL,
               preparation_started_at = NULL,
               prepared_by = NULL,
               preparation_verified_by = NULL,
               prepared_at = NULL,
               preparation_released_by = NULL,
               preparation_released_at = NULL,
               preparation_expires_at = NULL
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND revision = ?
           AND preparation_status = 'cancelled'
        """, key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay(),
        expectedRevision);
    if (changed == 1) {
      jdbc.update("""
          UPDATE application_preparation_lots
             SET preparation_status = 'discarded',
                 discarded_by = COALESCE(discarded_by, ?),
                 discarded_at = COALESCE(discarded_at, ?),
                 discard_reason = COALESCE(
                   NULLIF(discard_reason, ''),
                   'Reinicio de una preparación cancelada')
           WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
             AND application_day = ? AND preparation_status = 'active'
          """, actorId, Timestamp.from(now), key.patientId(), key.treatmentId(),
          key.cycleNumber(), key.applicationDay());
    }
    return changed == 1;
  }

  boolean updateAdministration(
      Key key, long expectedRevision, String status, JsonNode data,
      Long secondCheckerId, Instant actualAt, long actorId, Instant now) {
    boolean starting = "in_progress".equals(status);
    return jdbc.update("""
        UPDATE treatment_application_workflows
           SET administration_status = ?,
               administration_data = CAST(? AS jsonb),
               administration_started_by = CASE WHEN ? THEN ? ELSE administration_started_by END,
               administration_second_checker_by = CASE
                 WHEN ? THEN ? ELSE administration_second_checker_by END,
               administration_started_at = CASE WHEN ? THEN ? ELSE administration_started_at END,
               administration_completed_by = CASE WHEN ? THEN administration_completed_by ELSE ? END,
               administration_completed_at = CASE WHEN ? THEN administration_completed_at ELSE ? END,
               workflow_status = CASE WHEN ? THEN 'in_administration' ELSE 'completed' END,
               revision = revision + 1,
               updated_by = ?,
               updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND revision = ?
        """, status, data.toString(), starting, actorId, starting, secondCheckerId,
        starting, Timestamp.from(actualAt), starting, actorId, starting, Timestamp.from(actualAt),
        starting, actorId, Timestamp.from(now), key.patientId(), key.treatmentId(),
        key.cycleNumber(), key.applicationDay(), expectedRevision) == 1;
  }

  boolean interruptAdministration(
      Key key, long expectedRevision, JsonNode data,
      long actorId, Instant now) {
    return jdbc.update("""
        UPDATE treatment_application_workflows
           SET administration_status = 'withheld',
               administration_data = CAST(? AS jsonb),
               workflow_status = 'in_administration',
               revision = revision + 1,
               updated_by = ?,
               updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND revision = ?
           AND administration_status = 'in_progress'
        """, data.toString(), actorId, Timestamp.from(now), key.patientId(),
        key.treatmentId(), key.cycleNumber(), key.applicationDay(),
        expectedRevision) == 1;
  }

  boolean resolveAdministration(
      Key key, long expectedRevision, boolean resume, JsonNode data,
      Instant resolvedAt, long actorId, Instant now) {
    return jdbc.update("""
        UPDATE treatment_application_workflows
           SET administration_status = CASE WHEN ? THEN 'in_progress' ELSE 'withheld' END,
               administration_data = CAST(? AS jsonb),
               administration_completed_by = CASE
                 WHEN ? THEN administration_completed_by ELSE ? END,
               administration_completed_at = CASE
                 WHEN ? THEN administration_completed_at ELSE ? END,
               workflow_status = CASE WHEN ? THEN 'in_administration' ELSE 'cancelled' END,
               revision = revision + 1,
               updated_by = ?,
               updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND revision = ?
           AND administration_status = 'withheld'
        """, resume, data.toString(), resume, actorId, resume, Timestamp.from(resolvedAt),
        resume, actorId, Timestamp.from(now), key.patientId(), key.treatmentId(),
        key.cycleNumber(), key.applicationDay(), expectedRevision) == 1;
  }

  void synchronizeLogisticsSource(Key key, String source, long actorId, Instant now) {
    String medicationState = switch (source) {
      case "patient_has_medication" -> "with_patient";
      case "received_center" -> "received";
      default -> "pending";
    };
    jdbc.update("""
        UPDATE treatment_application_logistics
           SET medication_state = ?, revision = revision + 1,
               updated_by = ?, updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ?
        """, medicationState, actorId, Timestamp.from(now), key.patientId(),
        key.treatmentId(), key.cycleNumber(), key.applicationDay());
    jdbc.update("""
        UPDATE unified_infusion_sessions
           SET source_ref = source_ref || jsonb_build_object(
                 'scheduler', COALESCE(source_ref -> 'scheduler', '{}'::jsonb)
                   || jsonb_build_object(
                     'medicationSource', ?,
                     'medicationReceived', ?,
                     'medicationWithPatient', ?
                   )
               ),
               updated_by = ?, updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND clinical_status <> 'cancelled'
        """, source, "received_center".equals(source),
        "patient_has_medication".equals(source), actorId, Timestamp.from(now),
        key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay());
  }

  boolean reserveInventory(long lotId, String drugId, String drugName, BigDecimal quantity, String unit,
      long actorId, Instant now) {
    return jdbc.update("""
        UPDATE pharmacy_inventory_lots
           SET quantity_reserved = quantity_reserved + ?,
               revision = revision + 1, updated_by = ?, updated_at = ?
         WHERE id = ? AND inventory_status = 'active'
           AND expiration_date >= ?
           AND lower(quantity_unit) = lower(?)
           AND (
             (NULLIF(?, '') IS NOT NULL AND drug_id = NULLIF(?, ''))
             OR (NULLIF(?, '') IS NULL AND lower(drug_name) = lower(?))
           )
           AND quantity_on_hand - quantity_reserved >= ?
        """, quantity, actorId, Timestamp.from(now), lotId,
        Date.valueOf(LocalDate.now(clock)), unit,
        drugId, drugId, drugId, drugName, quantity) == 1;
  }

  void insertReservation(
      UUID id, Key key, String componentKey, String drugId, String drugName,
      BigDecimal requestedQuantity, String requestedText, String unit,
      String source, String status, String verificationMethod, Long inventoryLotId,
      String notes, long actorId, Instant now) {
    jdbc.update("""
        INSERT INTO application_stock_reservations (
          id, patient_id, treatment_id, cycle_number, application_day,
          component_key, drug_id, drug_name, requested_quantity,
          requested_quantity_text, reserved_quantity, quantity_unit,
          medication_source, reservation_status, verification_method,
          inventory_lot_id, verification_notes, verified_by, verified_at,
          created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, NULLIF(?, ''), ?, ?, NULLIF(?, ''),
                  ?, NULLIF(?, ''), ?, ?, ?, ?, NULLIF(?, ''), ?, ?, ?, ?)
        """, id, key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay(),
        componentKey, drugId, drugName, requestedQuantity, requestedText,
        "reserved".equals(status) ? requestedQuantity : null, unit,
        source, status, verificationMethod, inventoryLotId, notes,
        actorId, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
  }

  List<Reservation> activeReservations(Key key) {
    return reservations(key, true);
  }

  List<Reservation> reservations(Key key) {
    return reservations(key, false);
  }

  private List<Reservation> reservations(Key key, boolean activeOnly) {
    return jdbc.query("""
        SELECT id, component_key, drug_id, drug_name, requested_quantity,
               requested_quantity_text, reserved_quantity, quantity_unit,
               medication_source, reservation_status, verification_method,
               inventory_lot_id, verification_notes
          FROM application_stock_reservations
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ?
        """ + (activeOnly ? " AND reservation_status IN ('pending','reserved')" : "") + """
         ORDER BY created_at, drug_name
        """, this::mapReservation, key.patientId(), key.treatmentId(),
        key.cycleNumber(), key.applicationDay());
  }

  void releaseReservations(Key key, long actorId, Instant now) {
    List<Reservation> rows = activeReservations(key);
    for (Reservation row : rows) {
      if ("reserved".equals(row.status())
          && row.inventoryLotId() != null
          && row.reservedQuantity() != null) {
        jdbc.update("""
            UPDATE pharmacy_inventory_lots
               SET quantity_reserved = GREATEST(0, quantity_reserved - ?),
                   revision = revision + 1, updated_by = ?, updated_at = ?
             WHERE id = ?
            """, row.reservedQuantity(), actorId, Timestamp.from(now), row.inventoryLotId());
      }
    }
    jdbc.update("""
        UPDATE application_stock_reservations
           SET reservation_status = 'released', released_by = ?, released_at = ?,
               updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND reservation_status IN ('pending','reserved')
        """, actorId, Timestamp.from(now), Timestamp.from(now), key.patientId(),
        key.treatmentId(), key.cycleNumber(), key.applicationDay());
  }

  void consumeReservations(Key key, long actorId, Instant now) {
    List<Reservation> rows = activeReservations(key);
    for (Reservation row : rows) {
      if (!"reserved".equals(row.status())) continue;
      if (row.inventoryLotId() != null && row.reservedQuantity() != null) {
        int changed = jdbc.update("""
            UPDATE pharmacy_inventory_lots
               SET quantity_on_hand = quantity_on_hand - ?,
                   quantity_reserved = quantity_reserved - ?,
                   inventory_status = CASE
                     WHEN quantity_on_hand - ? = 0 THEN 'depleted'
                     ELSE inventory_status
                   END,
                   revision = revision + 1, updated_by = ?, updated_at = ?
             WHERE id = ? AND quantity_on_hand >= ? AND quantity_reserved >= ?
            """, row.reservedQuantity(), row.reservedQuantity(), row.reservedQuantity(),
            actorId, Timestamp.from(now), row.inventoryLotId(),
            row.reservedQuantity(), row.reservedQuantity());
        if (changed != 1) {
          throw new IllegalStateException("Reserved inventory changed unexpectedly");
        }
      }
    }
    jdbc.update("""
        UPDATE application_stock_reservations
           SET reservation_status = 'consumed', consumed_at = ?, updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND reservation_status = 'reserved'
        """, Timestamp.from(now), Timestamp.from(now), key.patientId(),
        key.treatmentId(), key.cycleNumber(), key.applicationDay());
  }

  void insertPreparationLot(
      UUID id, Key key, String componentKey, UUID reservationId,
      Long inventoryLotId, String drugName,
      String lot, LocalDate expiryDate, BigDecimal quantity, String quantityText,
      String unit, String diluent, String finalVolume, String concentration,
      int ttlMinutes, long actorId, long verifiedBy, Instant now) {
    jdbc.update("""
        INSERT INTO application_preparation_lots (
          id, patient_id, treatment_id, cycle_number, application_day,
          component_key, stock_reservation_id, inventory_lot_id, drug_name, lot_number,
          expiration_date, quantity, quantity_text, quantity_unit, diluent,
          final_volume, concentration, ttl_minutes, prepared_by, verified_by, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULLIF(?, ''), NULLIF(?, ''),
                  NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), ?, ?, ?, ?)
        """, id, key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay(),
        componentKey, reservationId, inventoryLotId, drugName, lot, Date.valueOf(expiryDate),
        quantity, quantityText, unit, diluent, finalVolume, concentration,
        ttlMinutes, actorId, verifiedBy, Timestamp.from(now));
  }

  Optional<InventoryLot> inventoryLot(long id) {
    return jdbc.query("""
        SELECT id, drug_id, drug_name, lot_number, expiration_date,
               quantity_on_hand, quantity_reserved, quantity_unit, inventory_status
          FROM pharmacy_inventory_lots
         WHERE id = ?
        """, (result, row) -> new InventoryLot(
            result.getLong("id"), text(result, "drug_id"), text(result, "drug_name"),
            text(result, "lot_number"), result.getDate("expiration_date").toLocalDate(),
            result.getBigDecimal("quantity_on_hand"), result.getBigDecimal("quantity_reserved"),
            text(result, "quantity_unit"), text(result, "inventory_status")),
        id).stream().findFirst();
  }

  List<PreparationLot> preparationLots(Key key) {
    return jdbc.query("""
        SELECT lot.id, lot.component_key, lot.drug_name, lot.lot_number, lot.expiration_date,
               lot.quantity, lot.quantity_text, lot.quantity_unit, lot.diluent,
               lot.final_volume, lot.concentration, lot.ttl_minutes,
               lot.preparation_status, lot.created_at
          FROM application_preparation_lots lot
         WHERE lot.patient_id = ? AND lot.treatment_id = ? AND lot.cycle_number = ?
           AND lot.application_day = ? AND lot.preparation_status = 'active'
         ORDER BY lot.created_at, lot.drug_name
        """, (result, row) -> new PreparationLot(
            result.getObject("id", UUID.class), text(result, "component_key"),
            text(result, "drug_name"),
            text(result, "lot_number"), result.getDate("expiration_date").toLocalDate(),
            result.getBigDecimal("quantity"), text(result, "quantity_text"),
            text(result, "quantity_unit"), text(result, "diluent"),
            text(result, "final_volume"), text(result, "concentration"),
            result.getInt("ttl_minutes"), text(result, "preparation_status"),
            result.getTimestamp("created_at").toInstant()),
        key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay());
  }

  boolean restartPreparation(
      Key key, long expectedRevision, String notes, long actorId, Instant now) {
    int changed = jdbc.update("""
        UPDATE treatment_application_workflows
           SET medication_source = CASE medication_source
                 WHEN 'patient_has_medication' THEN 'patient_to_bring'
                 WHEN 'received_center' THEN 'pending_supplier'
                 ELSE medication_source
               END,
               stock_reservation_status = CASE
                 WHEN medication_source IN ('patient_has_medication','received_center')
                   THEN 'none'
                 WHEN medication_source = 'center_stock' THEN 'none'
                 ELSE stock_reservation_status
               END,
               clinical_authorization_status = 'pending',
               clinical_authorization_reason = NULL,
               clinical_assessment = '{}'::jsonb,
               clinically_authorized_by = NULL,
               clinically_authorized_at = NULL,
               preparation_status = 'not_started',
               preparation_data = jsonb_build_object(
                 'restartReason', NULLIF(?, ''),
                 'previousPreparationDiscardedAt', CAST(? AS text)
               ),
               preparation_started_by = NULL,
               preparation_started_at = NULL,
               prepared_by = NULL,
               preparation_verified_by = NULL,
               prepared_at = NULL,
               preparation_released_by = NULL,
               preparation_released_at = NULL,
               preparation_expires_at = NULL,
               preparation_restart_count = preparation_restart_count + 1,
               workflow_status = 'medication_pending',
               revision = revision + 1,
               updated_by = ?,
               updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND revision = ?
        """, notes, Timestamp.from(now), actorId, Timestamp.from(now),
        key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay(),
        expectedRevision);
    if (changed == 1) {
      jdbc.update("""
          UPDATE application_preparation_lots
             SET preparation_status = 'discarded', discarded_by = ?,
                 discarded_at = ?, discard_reason = NULLIF(?, '')
           WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
             AND application_day = ? AND preparation_status = 'active'
          """, actorId, Timestamp.from(now), notes, key.patientId(), key.treatmentId(),
          key.cycleNumber(), key.applicationDay());
    }
    return changed == 1;
  }

  void synchronizeSessionAfterClinicalFail(Key key, String reason, long actorId, Instant now) {
    jdbc.update("""
        UPDATE unified_infusion_sessions
           SET scheduled_at = NULL, chair = NULL, clinical_status = 'cancelled',
               pharmacy_status = CASE
                 WHEN pharmacy_status IN ('in_preparation','ready','released')
                   THEN pharmacy_status ELSE 'cancelled' END,
               administration_status = 'withheld',
               notes = concat_ws(E'\n', NULLIF(notes, ''), NULLIF(?, '')),
               revision = revision + 1, updated_by = ?, updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND clinical_status <> 'cancelled'
        """, "Triaje FAIL: " + reason, actorId, Timestamp.from(now), key.patientId(),
        key.treatmentId(), key.cycleNumber(), key.applicationDay());
  }

  void synchronizeSessionPreparation(Key key, String pharmacyStatus, long actorId, Instant now) {
    jdbc.update("""
        UPDATE unified_infusion_sessions
           SET pharmacy_status = ?, revision = revision + 1,
               updated_by = ?, updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND clinical_status <> 'cancelled'
        """, pharmacyStatus, actorId, Timestamp.from(now), key.patientId(),
        key.treatmentId(), key.cycleNumber(), key.applicationDay());
    jdbc.update("""
        UPDATE unified_infusion_medications m
           SET preparation_status = ?, revision = m.revision + 1,
               updated_by = ?, updated_at = ?
          FROM unified_infusion_sessions s
         WHERE m.infusion_session_id = s.id
           AND s.patient_id = ? AND s.treatment_id = ? AND s.cycle_number = ?
           AND s.application_day = ? AND s.clinical_status <> 'cancelled'
        """, pharmacyStatus, actorId, Timestamp.from(now), key.patientId(),
        key.treatmentId(), key.cycleNumber(), key.applicationDay());
  }

  void synchronizeSessionAdministration(
      Key key, String administrationStatus, String clinicalStatus,
      long actorId, Instant now) {
    jdbc.update("""
        UPDATE unified_infusion_sessions
           SET administration_status = ?, clinical_status = ?,
               revision = revision + 1, updated_by = ?, updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND clinical_status <> 'cancelled'
        """, administrationStatus, clinicalStatus, actorId, Timestamp.from(now),
        key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay());
    jdbc.update("""
        UPDATE unified_infusion_medications m
           SET administration_status = ?, revision = m.revision + 1,
               updated_by = ?, updated_at = ?
          FROM unified_infusion_sessions s
         WHERE m.infusion_session_id = s.id
           AND s.patient_id = ? AND s.treatment_id = ? AND s.cycle_number = ?
           AND s.application_day = ? AND s.clinical_status <> 'cancelled'
        """, administrationStatus, actorId, Timestamp.from(now), key.patientId(),
        key.treatmentId(), key.cycleNumber(), key.applicationDay());
  }

  Optional<Long> resolveEnabledUser(String identifier, String requiredPermission) {
    if (identifier == null || identifier.isBlank()) return Optional.empty();
    try {
      long id = Long.parseLong(identifier.trim());
      return jdbc.query("""
          SELECT u.id
            FROM local_users u
           WHERE u.id = ? AND u.enabled = true
             AND EXISTS (
               SELECT 1
                 FROM local_user_roles ur
                 JOIN local_role_permissions rp ON rp.role_id = ur.role_id
                 JOIN local_permissions permission ON permission.id = rp.permission_id
                 JOIN local_roles role ON role.id = ur.role_id
                WHERE ur.user_id = u.id AND role.enabled = true
                  AND permission.permission_key = ?
             )
          """, (result, row) -> result.getLong("id"), id, requiredPermission)
          .stream().findFirst();
    } catch (NumberFormatException ignored) {
      String value = identifier.trim();
      return jdbc.query("""
          SELECT u.id
            FROM local_users u
           WHERE u.enabled = true
             AND (lower(u.username) = lower(?) OR lower(COALESCE(u.display_name, '')) = lower(?))
             AND EXISTS (
               SELECT 1
                 FROM local_user_roles ur
                 JOIN local_role_permissions rp ON rp.role_id = ur.role_id
                 JOIN local_permissions permission ON permission.id = rp.permission_id
                 JOIN local_roles role ON role.id = ur.role_id
                WHERE ur.user_id = u.id AND role.enabled = true
                  AND permission.permission_key = ?
             )
           ORDER BY u.id LIMIT 1
          """, (result, row) -> result.getLong("id"), value, value, requiredPermission)
          .stream().findFirst();
    }
  }

  String userDisplayName(long userId) {
    return jdbc.query("""
        SELECT COALESCE(NULLIF(display_name, ''), username)
          FROM local_users
         WHERE id = ?
        """, (result, row) -> result.getString(1), userId)
        .stream().findFirst().orElse("Usuario " + userId);
  }

  Optional<WorkflowEvent> event(Key key, String idempotencyKey) {
    return jdbc.query("""
        SELECT event.id, event.action, event.expected_revision,
               event.resulting_revision, event.occurred_at, event.after_json::text,
               COALESCE(NULLIF(actor.display_name, ''), actor.username, 'Sistema') AS actor_name
          FROM treatment_application_workflow_events event
          LEFT JOIN local_users actor ON actor.id = event.actor_user_id
         WHERE event.patient_id = ? AND event.treatment_id = ? AND event.cycle_number = ?
           AND event.application_day = ? AND event.idempotency_key = ?
        """, (result, row) -> new WorkflowEvent(
            result.getLong("id"), result.getString("action"),
            result.getLong("expected_revision"), result.getLong("resulting_revision"),
            result.getTimestamp("occurred_at").toInstant(),
            result.getString("actor_name"),
            mapper.readTree(result.getString("after_json"))),
        key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay(),
        idempotencyKey).stream().findFirst();
  }

  List<AuditEvent> events(Key key) {
    return jdbc.query("""
        SELECT event.id, event.action, event.expected_revision, event.resulting_revision,
               event.occurred_at,
               COALESCE(NULLIF(actor.display_name, ''), actor.username, 'Sistema') AS actor_name
          FROM treatment_application_workflow_events event
          LEFT JOIN local_users actor ON actor.id = event.actor_user_id
         WHERE event.patient_id = ? AND event.treatment_id = ? AND event.cycle_number = ?
           AND event.application_day = ?
         ORDER BY event.occurred_at, event.id
        """, (result, row) -> new AuditEvent(
            result.getLong("id"), result.getString("action"),
            result.getLong("expected_revision"), result.getLong("resulting_revision"),
            result.getTimestamp("occurred_at").toInstant(),
            result.getString("actor_name")),
        key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay());
  }

  void insertEvent(
      Key key, String action, String idempotencyKey, long actorId,
      long expectedRevision, long resultingRevision, JsonNode command,
      JsonNode before, JsonNode after, Instant now) {
    jdbc.update("""
        INSERT INTO treatment_application_workflow_events (
          patient_id, treatment_id, cycle_number, application_day,
          action, idempotency_key, actor_user_id, expected_revision,
          resulting_revision, command_json, before_json, after_json, occurred_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb),
                  CAST(? AS jsonb), CAST(? AS jsonb), ?)
        """, key.patientId(), key.treatmentId(), key.cycleNumber(), key.applicationDay(),
        action, idempotencyKey, actorId, expectedRevision, resultingRevision,
        command.toString(), before.toString(), after.toString(), Timestamp.from(now));
  }

  void updateEventAfter(Key key, String idempotencyKey, JsonNode after) {
    jdbc.update("""
        UPDATE treatment_application_workflow_events
           SET after_json = CAST(? AS jsonb)
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND idempotency_key = ?
        """, after.toString(), key.patientId(), key.treatmentId(), key.cycleNumber(),
        key.applicationDay(), idempotencyKey);
  }

  private String selectSql() {
    return """
        SELECT w.patient_id, w.treatment_id, w.cycle_number, w.application_day,
               w.workflow_status, w.medication_source,
               w.pharmacy_validation_status, w.pharmacy_validation_notes,
               w.pharmacy_validated_at, w.stock_reservation_status,
               w.stock_reservation_notes, w.stock_reserved_at, w.stock_released_at,
               w.clinical_authorization_status, w.clinical_authorization_reason,
               w.clinical_assessment::text, w.clinically_authorized_at,
               w.preparation_status, w.preparation_data::text,
               w.preparation_started_at, w.prepared_by,
               COALESCE(NULLIF(preparer.display_name, ''), preparer.username, '') AS prepared_by_name,
               w.preparation_verified_by,
               COALESCE(NULLIF(verifier.display_name, ''), verifier.username, '') AS preparation_verified_by_name,
               w.prepared_at, w.preparation_released_at,
               w.preparation_expires_at, w.preparation_restart_count,
               w.administration_status,
               w.administration_data::text, w.administration_started_at,
               w.administration_completed_at, w.revision, w.updated_at,
               l.planned_date, l.prescription_state, l.duration_minutes,
               l.duration_source, l.drug_summary, l.application_drugs::text,
               p.document_number, p.medical_record_number, p.first_name, p.last_name,
               p.health_insurance, p.health_insurance_number,
               t.diagnosis, t.scheme_name, t.treatment_type, t.cycle_count,
               s.id AS session_id, s.scheduled_at, s.chair,
               s.clinical_status AS session_clinical_status,
               s.pharmacy_status AS session_pharmacy_status,
               s.administration_status AS session_administration_status,
               s.appointment_confirmed
          FROM treatment_application_workflows w
          JOIN treatment_application_logistics l
            ON l.patient_id = w.patient_id AND l.treatment_id = w.treatment_id
           AND l.cycle_number = w.cycle_number AND l.application_day = w.application_day
          JOIN clinical_treatments t
            ON t.patient_id = w.patient_id AND t.id = w.treatment_id
          JOIN patients p ON p.source_id = w.patient_id
          LEFT JOIN local_users preparer ON preparer.id = w.prepared_by
          LEFT JOIN local_users verifier ON verifier.id = w.preparation_verified_by
          LEFT JOIN LATERAL (
            SELECT candidate.id, candidate.scheduled_at, candidate.chair,
                   candidate.clinical_status, candidate.pharmacy_status,
                   candidate.administration_status, candidate.appointment_confirmed
              FROM unified_infusion_sessions candidate
             WHERE candidate.patient_id = w.patient_id
               AND candidate.treatment_id = w.treatment_id
               AND candidate.cycle_number = w.cycle_number
               AND candidate.application_day = w.application_day
               AND candidate.clinical_status <> 'cancelled'
             ORDER BY candidate.scheduled_at NULLS LAST, candidate.id DESC
             LIMIT 1
          ) s ON true
        """;
  }

  private String keyWhere() {
    return """
         WHERE w.patient_id = ? AND w.treatment_id = ? AND w.cycle_number = ?
           AND w.application_day = ?
        """;
  }

  private Application mapApplication(ResultSet result, int row) throws SQLException {
    Date planned = result.getDate("planned_date");
    Long sessionId = nullableLong(result, "session_id");
    return new Application(
        new Key(result.getLong("patient_id"), result.getString("treatment_id"),
            result.getInt("cycle_number"), result.getInt("application_day")),
        text(result, "workflow_status"), text(result, "prescription_state"),
        text(result, "medication_source"), text(result, "pharmacy_validation_status"),
        text(result, "pharmacy_validation_notes"), instant(result, "pharmacy_validated_at"),
        text(result, "stock_reservation_status"), text(result, "stock_reservation_notes"),
        instant(result, "stock_reserved_at"), instant(result, "stock_released_at"),
        text(result, "clinical_authorization_status"),
        text(result, "clinical_authorization_reason"),
        mapper.readTree(result.getString("clinical_assessment")),
        instant(result, "clinically_authorized_at"),
        text(result, "preparation_status"),
        mapper.readTree(result.getString("preparation_data")),
        instant(result, "preparation_started_at"),
        nullableLong(result, "prepared_by"), text(result, "prepared_by_name"),
        nullableLong(result, "preparation_verified_by"),
        text(result, "preparation_verified_by_name"),
        instant(result, "prepared_at"),
        instant(result, "preparation_released_at"), instant(result, "preparation_expires_at"),
        result.getInt("preparation_restart_count"),
        text(result, "administration_status"),
        mapper.readTree(result.getString("administration_data")),
        instant(result, "administration_started_at"),
        instant(result, "administration_completed_at"),
        result.getLong("revision"), result.getTimestamp("updated_at").toInstant(),
        planned == null ? null : planned.toLocalDate(), result.getInt("duration_minutes"),
        text(result, "duration_source"), text(result, "drug_summary"),
        mapper.readTree(result.getString("application_drugs")),
        text(result, "document_number"), text(result, "medical_record_number"),
        text(result, "first_name"), text(result, "last_name"),
        text(result, "health_insurance"), text(result, "health_insurance_number"),
        text(result, "diagnosis"), text(result, "scheme_name"),
        text(result, "treatment_type"), result.getInt("cycle_count"),
        sessionId, instant(result, "scheduled_at"), text(result, "chair"),
        text(result, "session_clinical_status"), text(result, "session_pharmacy_status"),
        text(result, "session_administration_status"),
        sessionId != null && result.getBoolean("appointment_confirmed"));
  }

  private Reservation mapReservation(ResultSet result, int row) throws SQLException {
    return new Reservation(
        result.getObject("id", UUID.class), text(result, "component_key"),
        text(result, "drug_id"), text(result, "drug_name"),
        result.getBigDecimal("requested_quantity"), text(result, "requested_quantity_text"),
        result.getBigDecimal("reserved_quantity"), text(result, "quantity_unit"),
        text(result, "medication_source"), text(result, "reservation_status"),
        text(result, "verification_method"), nullableLong(result, "inventory_lot_id"),
        text(result, "verification_notes"));
  }

  private String text(ResultSet result, String column) throws SQLException {
    String value = result.getString(column);
    return value == null ? "" : value;
  }

  private Long nullableLong(ResultSet result, String column) throws SQLException {
    Object value = result.getObject(column);
    return value == null ? null : result.getLong(column);
  }

  private Instant instant(ResultSet result, String column) throws SQLException {
    Timestamp value = result.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }

  record Key(long patientId, String treatmentId, int cycleNumber, int applicationDay) {
  }

  record Application(
      Key key,
      String workflowStatus,
      String prescriptionStatus,
      String medicationSource,
      String pharmacyValidationStatus,
      String pharmacyValidationNotes,
      Instant pharmacyValidatedAt,
      String stockReservationStatus,
      String stockReservationNotes,
      Instant stockReservedAt,
      Instant stockReleasedAt,
      String clinicalAuthorizationStatus,
      String clinicalAuthorizationReason,
      JsonNode clinicalAssessment,
      Instant clinicallyAuthorizedAt,
      String preparationStatus,
      JsonNode preparationData,
      Instant preparationStartedAt,
      Long preparedBy,
      String preparedByName,
      Long preparationVerifiedBy,
      String preparationVerifiedByName,
      Instant preparedAt,
      Instant preparationReleasedAt,
      Instant preparationExpiresAt,
      int preparationRestartCount,
      String administrationStatus,
      JsonNode administrationData,
      Instant administrationStartedAt,
      Instant administrationCompletedAt,
      long revision,
      Instant updatedAt,
      LocalDate plannedDate,
      int durationMinutes,
      String durationSource,
      String drugSummary,
      JsonNode applicationDrugs,
      String patientDni,
      String medicalRecord,
      String firstName,
      String lastName,
      String insurance,
      String affiliateNumber,
      String diagnosis,
      String scheme,
      String treatmentType,
      int totalCycles,
      Long sessionId,
      Instant scheduledAt,
      String chair,
      String sessionClinicalStatus,
      String sessionPharmacyStatus,
      String sessionAdministrationStatus,
      boolean appointmentConfirmed) {
    String patientName() {
      return (lastName + ", " + firstName).replaceAll("(^[, ]+|[, ]+$)", "");
    }

    boolean hasActiveAppointment() {
      return sessionId != null;
    }

    boolean hasConfirmedAppointmentOn(LocalDate date, ZoneId zone) {
      return sessionId != null
          && appointmentConfirmed
          && scheduledAt != null
          && LocalDate.ofInstant(scheduledAt, zone).equals(date);
    }

    ApplicationWorkflowPolicy.State policyState() {
      return new ApplicationWorkflowPolicy.State(
          workflowStatus, prescriptionStatus, medicationSource,
          pharmacyValidationStatus, stockReservationStatus,
          clinicalAuthorizationStatus, preparationStatus, administrationStatus);
    }
  }

  private String normalizeSearch(String value) {
    if (value == null || value.isBlank()) return "";
    return Normalizer.normalize(value.trim().toLowerCase(), Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .replaceAll("[^a-z0-9/.-]+", " ")
        .trim();
  }

  record Reservation(
      UUID id,
      String componentKey,
      String drugId,
      String drugName,
      BigDecimal requestedQuantity,
      String requestedQuantityText,
      BigDecimal reservedQuantity,
      String unit,
      String source,
      String status,
      String verificationMethod,
      Long inventoryLotId,
      String notes) {
  }

  record InventoryLot(
      long id,
      String drugId,
      String drugName,
      String lotNumber,
      LocalDate expirationDate,
      BigDecimal quantityOnHand,
      BigDecimal quantityReserved,
      String unit,
      String status) {
  }

  record PreparationLot(
      UUID id,
      String componentKey,
      String drugName,
      String lotNumber,
      LocalDate expirationDate,
      BigDecimal quantity,
      String quantityText,
      String unit,
      String diluent,
      String finalVolume,
      String concentration,
      int ttlMinutes,
      String status,
      Instant createdAt) {
  }

  record WorkflowEvent(
      long id,
      String action,
      long expectedRevision,
      long resultingRevision,
      Instant occurredAt,
      String actorName,
      JsonNode after) {
  }

  record AuditEvent(
      long id,
      String action,
      long expectedRevision,
      long resultingRevision,
      Instant occurredAt,
      String actorName) {
  }

  record ScheduleGate(
      String prescriptionStatus,
      String continuityStatus,
      boolean prescriptionRequired,
      String pharmacyValidationStatus,
      String medicationSource,
      String stockReservationStatus,
      String clinicalAuthorizationStatus,
      String clinicalAuthorizationReason,
      JsonNode clinicalAssessment,
      String preparationStatus,
      String administrationStatus,
      String workflowStatus,
      long revision) {
  }
}
