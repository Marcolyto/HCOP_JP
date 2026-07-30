package ar.com.hexium.hcop.infusion;

import ar.com.hexium.hcop.catalog.TreatmentCatalogService;
import ar.com.hexium.hcop.infusion.TreatmentApplicationPlanner.ApplicationPlan;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class TreatmentApplicationLogisticsService {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final TreatmentApplicationPlanner planner;
  private final TreatmentCatalogService catalog;
  private final Clock clock;
  private final AtomicBoolean synchronizedExistingTreatments = new AtomicBoolean(false);

  public TreatmentApplicationLogisticsService(
      JdbcTemplate jdbc,
      ObjectMapper mapper,
      TreatmentApplicationPlanner planner,
      TreatmentCatalogService catalog,
      Clock clock) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.planner = planner;
    this.catalog = catalog;
    this.clock = clock;
  }

  @Transactional
  public void synchronizeExistingTreatments() {
    if (synchronizedExistingTreatments.get()) return;
    synchronized (synchronizedExistingTreatments) {
      if (synchronizedExistingTreatments.get()) return;
      List<String> treatmentIds = jdbc.query("""
          SELECT t.id
            FROM clinical_treatments t
            JOIN treatment_details d ON d.treatment_id = t.id
           ORDER BY t.created_at
          """, (result, row) -> result.getString("id"));
      treatmentIds.forEach(this::synchronizeTreatment);
      synchronizedExistingTreatments.set(true);
    }
  }

  @Transactional
  public void synchronizeTreatment(String treatmentId) {
    List<Snapshot> snapshots = jdbc.query("""
        SELECT t.id, t.patient_id, t.scheme_id, t.first_cycle_date, t.initial_cycle, t.cycle_days,
               t.estimated_duration_minutes, t.updated_by, d.detail_json::text,
               COALESCE(
                 (SELECT NULLIF((c.definition_json ->> 'slotMinutes'), '')::integer
                    FROM clinical_configuration_items c
                   WHERE c.item_kind = 'day-hospital-settings' AND c.active = true
                   ORDER BY c.updated_at DESC LIMIT 1),
                 10
               ) AS slot_minutes
          FROM clinical_treatments t
          JOIN treatment_details d ON d.treatment_id = t.id
         WHERE t.id = ?
        """, (result, row) -> {
      Date first = result.getDate("first_cycle_date");
      return new Snapshot(
          result.getString("id"),
          result.getLong("patient_id"),
          result.getString("scheme_id"),
          first == null ? null : first.toLocalDate(),
          result.getInt("initial_cycle"),
          result.getObject("cycle_days") == null ? 0 : result.getInt("cycle_days"),
          result.getObject("estimated_duration_minutes") == null
              ? null : result.getInt("estimated_duration_minutes"),
          result.getLong("updated_by"),
          mapper.readTree(result.getString("detail_json")),
          result.getInt("slot_minutes"));
    }, treatmentId);
    if (snapshots.isEmpty()) return;
    Snapshot snapshot = snapshots.getFirst();
    TreatmentCatalogService.Scheme scheme = catalog.scheme(snapshot.schemeId()).orElse(null);
    JsonNode protocolDefinition = scheme == null ? null : scheme.definition();
    Integer protocolDuration = snapshot.protocolDurationMinutes();
    if ((protocolDuration == null || protocolDuration < 1)
        && scheme != null && scheme.durationMinutes() != null && scheme.durationMinutes() > 0) {
      protocolDuration = scheme.durationMinutes();
    }
    List<ApplicationPlan> plans = planner.plan(
        snapshot.detail(), protocolDefinition,
        snapshot.firstCycleDate(), snapshot.initialCycle(),
        snapshot.cycleDays(), protocolDuration, snapshot.slotMinutes());
    Instant now = clock.instant();
    for (ApplicationPlan plan : plans) {
      upsert(snapshot, plan, now);
      reconcileLegacyScheduledApplication(snapshot, plan, now);
    }
  }

  private void upsert(Snapshot snapshot, ApplicationPlan plan, Instant now) {
    jdbc.update("""
        INSERT INTO treatment_application_logistics (
          patient_id, treatment_id, cycle_number, application_day, planned_date,
          medication_state, prescription_state, duration_minutes, duration_source,
          drug_summary, application_drugs, notes, revision, updated_by, created_at, updated_at
        )
        SELECT ?, ?, ?, ?, ?,
               COALESCE(c.medication_state, 'pending'),
               COALESCE(c.prescription_state, 'confirmed'),
               ?, ?, NULLIF(?, ''), CAST(? AS jsonb), c.notes, 1, ?, ?, ?
          FROM (SELECT 1) seed
          LEFT JOIN treatment_cycle_logistics c
            ON c.patient_id = ? AND c.treatment_id = ? AND c.cycle_number = ?
        ON CONFLICT (patient_id, treatment_id, cycle_number, application_day)
        DO UPDATE SET
          duration_minutes = EXCLUDED.duration_minutes,
          duration_source = EXCLUDED.duration_source,
          drug_summary = EXCLUDED.drug_summary,
          application_drugs = EXCLUDED.application_drugs,
          updated_at = EXCLUDED.updated_at
        """,
        snapshot.patientId(), snapshot.treatmentId(), plan.cycleNumber(), plan.applicationDay(),
        plan.plannedDate() == null ? null : Date.valueOf(plan.plannedDate()),
        plan.durationMinutes(), plan.durationSource(), plan.drugSummary(), plan.drugs().toString(),
        snapshot.actorId(), Timestamp.from(now), Timestamp.from(now),
        snapshot.patientId(), snapshot.treatmentId(), plan.cycleNumber());
  }

  /**
   * Brings appointments created before application-level logistics into the new model.
   * Only future/planned legacy appointments are resized; completed or already-started
   * administrations retain their historical duration.
   */
  private void reconcileLegacyScheduledApplication(
      Snapshot snapshot, ApplicationPlan plan, Instant now) {
    List<Long> sessionIds = jdbc.query("""
        SELECT id
          FROM unified_infusion_sessions
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ? AND clinical_status <> 'cancelled'
        """, (result, row) -> result.getLong("id"),
        snapshot.patientId(), snapshot.treatmentId(), plan.cycleNumber(), plan.applicationDay());
    if (sessionIds.isEmpty()) return;

    jdbc.update("""
        UPDATE unified_infusion_sessions
           SET duration_minutes = ?,
               source_ref = COALESCE(source_ref, '{}'::jsonb) || jsonb_build_object(
                 'scheduler',
                 COALESCE(source_ref -> 'scheduler', '{}'::jsonb) || jsonb_build_object(
                   'applicationDay', ?,
                   'durationSource', ?,
                   'drugSummary', ?,
                   'applicationDrugs', CAST(? AS jsonb)
                 )
               ),
               updated_by = ?,
               updated_at = ?
         WHERE patient_id = ? AND treatment_id = ? AND cycle_number = ?
           AND application_day = ?
           AND clinical_status = 'planned'
           AND COALESCE(administration_status, 'not_started') = 'not_started'
           AND COALESCE(source_ref #>> '{scheduler,durationSource}', '') = ''
        """,
        plan.durationMinutes(), plan.applicationDay(), plan.durationSource(), plan.drugSummary(),
        plan.drugs().toString(), snapshot.actorId(), Timestamp.from(now),
        snapshot.patientId(), snapshot.treatmentId(), plan.cycleNumber(), plan.applicationDay());

    for (Long sessionId : sessionIds) {
      for (JsonNode drug : plan.drugs()) {
        String sourceItemRef = firstText(drug, "sourceItemRef", "id");
        String drugId = firstText(drug, "drugId", "idDroga");
        String drugName = firstText(drug, "drugName", "droga", "name", "nombre");
        if (drugName.isBlank()) continue;
        String dose = firstText(drug, "prescribedDoseText", "dosis", "dosisDiaria");
        String doseUnit = firstText(drug, "doseUnit", "unidad", "unidadDosis");
        String route = firstText(drug, "route", "viaAdministracion", "via");
        String administrationTime =
            firstText(drug, "administrationTime", "tiempoAdministracion", "time");
        jdbc.update("""
            INSERT INTO unified_infusion_medications (
              infusion_session_id, source_item_ref, drug_id, drug_name, prescribed_dose_text,
              dose_unit, route, preparation_status, administration_status, notes, revision,
              created_by, updated_by, created_at, updated_at
            )
            SELECT ?, NULLIF(?, ''), NULLIF(?, ''), ?, NULLIF(?, ''), NULLIF(?, ''),
                   NULLIF(?, ''), 'pending', 'not_started', NULLIF(?, ''), 1, ?, ?, ?, ?
             WHERE NOT EXISTS (
               SELECT 1
                 FROM unified_infusion_medications m
                WHERE m.infusion_session_id = ?
                  AND (
                    (NULLIF(?, '') IS NOT NULL AND m.source_item_ref = NULLIF(?, ''))
                    OR (NULLIF(?, '') IS NULL AND lower(m.drug_name) = lower(?))
                  )
             )
            """,
            sessionId, sourceItemRef, drugId, drugName, dose, doseUnit, route,
            administrationTime, snapshot.actorId(), snapshot.actorId(),
            Timestamp.from(now), Timestamp.from(now),
            sessionId, sourceItemRef, sourceItemRef, sourceItemRef, drugName);
      }
    }
  }

  private String firstText(JsonNode node, String... fields) {
    for (String field : fields) {
      String value = node.path(field).asText("").trim();
      if (!value.isBlank()) return value;
    }
    return "";
  }

  private record Snapshot(
      String treatmentId,
      long patientId,
      String schemeId,
      LocalDate firstCycleDate,
      int initialCycle,
      int cycleDays,
      Integer protocolDurationMinutes,
      long actorId,
      JsonNode detail,
      int slotMinutes) {
  }
}
