package ar.com.hexium.hcop.infusion.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.infusion.application.service.InfusionFailure;
import ar.com.hexium.hcop.infusion.application.port.in.TreatmentApplicationLogisticsUseCase;
import ar.com.hexium.hcop.infusion.application.port.out.InfusionStore;
import ar.com.hexium.hcop.infusion.domain.Infusion;
import ar.com.hexium.hcop.infusion.domain.Logistics;
import ar.com.hexium.hcop.infusion.domain.ScheduleSettings;
import ar.com.hexium.hcop.infusion.infrastructure.persistence.PostgresApplicationWorkflowStore.Key;
import ar.com.hexium.hcop.infusion.infrastructure.persistence.PostgresApplicationWorkflowStore.ScheduleGate;
import ar.com.hexium.hcop.patient.application.port.in.PatientDocumentUseCase;
import ar.com.hexium.hcop.patient.application.port.in.PatientUseCase;
import ar.com.hexium.hcop.treatment.application.port.out.TreatmentStore;
import ar.com.hexium.hcop.treatment.domain.Treatment;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Regression tests for the two concurrency scenarios that previously required
 * two human QA sessions (FAR-25 and TUR-25).
 */
class HospitalDayConcurrencySafetyTest {
  private static final Instant NOW = Instant.parse("2026-07-29T15:00:00Z");

  @Test
  void far25TwoPharmacistsCannotOverReserveTheSameInventoryLot() throws Exception {
    AtomicStockJdbcTemplate jdbc = new AtomicStockJdbcTemplate(new BigDecimal("100.0"), 2);
    PostgresApplicationWorkflowStore store = new PostgresApplicationWorkflowStore(
        jdbc, JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC), mock(PatientDocumentUseCase.class));
    ExecutorService workers = Executors.newFixedThreadPool(2);

    try {
      Callable<Boolean> reserve = () -> store.reserveInventory(
          41, "drug-1", "Paclitaxel", new BigDecimal("75.0"), "mg", 22, NOW);
      List<Future<Boolean>> attempts = workers.invokeAll(List.of(reserve, reserve));
      List<Boolean> outcomes = attempts.stream().map(HospitalDayConcurrencySafetyTest::result).toList();

      assertThat(outcomes).containsExactlyInAnyOrder(true, false);
      assertThat(jdbc.reserved()).isEqualByComparingTo("75.0");
      assertThat(jdbc.reserved()).isLessThanOrEqualTo(jdbc.onHand());
      assertThat(jdbc.sql())
          .contains("quantity_reserved = quantity_reserved + ?")
          .contains("quantity_on_hand - quantity_reserved >= ?");
    } finally {
      workers.shutdownNow();
    }
  }

  @Test
  void far25ApplicationLockSerializesTwoPharmacistsBeforeCheckingRevision() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    PostgresApplicationWorkflowStore store = new PostgresApplicationWorkflowStore(
        jdbc, JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC), mock(PatientDocumentUseCase.class));

    store.lock(new Key(9, "tx-1", 1, 1));

    String sql = (String) org.mockito.Mockito.mockingDetails(jdbc)
        .getInvocations().iterator().next().getRawArguments()[0];
    assertThat(sql)
        .contains("FOR UPDATE OF w")
        .contains("w.patient_id = ?")
        .contains("w.application_day = ?");
  }

  @Test
  void tur25SimultaneousDropsYieldOneAppointmentAndOneClearConflict() throws Exception {
    SchedulingFixture fixture = new SchedulingFixture();
    CyclicBarrier simultaneousInsert = new CyclicBarrier(2);
    AtomicBoolean appointmentCreated = new AtomicBoolean();
    when(fixture.infusions.insert(any(), anyLong())).thenAnswer(invocation -> {
      await(simultaneousInsert);
      if (appointmentCreated.compareAndSet(false, true)) {
        return fixture.appointment();
      }
      throw new DataIntegrityViolationException("23P01: El sillón ya está ocupado en ese horario.");
    });

    JsonNode input = fixture.scheduleInput("2026-07-30T13:00:00Z", "1");
    ExecutorService workers = Executors.newFixedThreadPool(2);
    try {
      Callable<Object> drop = () -> fixture.store.create(
          input.deepCopy(), fixture.actor.userId(), fixture.actor.displayName());
      List<Future<Object>> attempts = workers.invokeAll(List.of(drop, drop));
      List<Object> outcomes = attempts.stream().map(HospitalDayConcurrencySafetyTest::outcome).toList();

      assertThat(outcomes.stream().filter(Map.class::isInstance)).hasSize(1);
      List<InfusionFailure> conflicts = outcomes.stream()
          .filter(InfusionFailure.class::isInstance)
          .map(InfusionFailure.class::cast)
          .toList();
      assertThat(conflicts).singleElement().satisfies(conflict -> {
        assertThat(conflict.type()).isEqualTo(InfusionFailure.Type.CONFLICT);
        assertThat(conflict.code()).isEqualTo("CHAIR_SCHEDULE_CONFLICT");
        assertThat(conflict).hasMessageContaining("sillón");
      });
    } finally {
      workers.shutdownNow();
    }
  }

  @Test
  void tur25DatabaseSerializesAChairAndRejectsDuplicateActiveApplications() throws Exception {
    String overlapGuard = new ClassPathResource("db/migration/V003__scheduler_overlap_guard.sql")
        .getContentAsString(StandardCharsets.UTF_8);
    String applicationGuard = new ClassPathResource("db/migration/V007__application_level_day_hospital.sql")
        .getContentAsString(StandardCharsets.UTF_8);

    assertThat(overlapGuard)
        .contains("pg_advisory_xact_lock")
        .contains("NEW.scheduled_at <")
        .contains("occupied.scheduled_at <")
        .contains("ERRCODE = '23P01'");
    assertThat(applicationGuard)
        .contains("CREATE UNIQUE INDEX uq_unified_infusion_active_application")
        .contains("(patient_id, treatment_id, cycle_number, application_day)")
        .contains("WHERE clinical_status <> 'cancelled'");
  }

  @Test
  void tur25AcceptsExactWorkdayEdgesAndRejectsTheFirstOverflowingSlot() {
    SchedulingFixture fixture = new SchedulingFixture();
    when(fixture.infusions.insert(any(), anyLong())).thenReturn(fixture.appointment());

    assertThat(fixture.store.create(
        fixture.scheduleInput("2026-07-30T08:00:00Z", "1"), fixture.actor.userId(), fixture.actor.displayName()))
        .containsEntry("chair", "1");
    assertThat(fixture.store.create(
        fixture.scheduleInput("2026-07-30T14:30:00Z", "1"), fixture.actor.userId(), fixture.actor.displayName()))
        .containsEntry("chair", "1");

    assertThatThrownBy(() -> fixture.store.create(
        fixture.scheduleInput("2026-07-30T14:40:00Z", "1"), fixture.actor.userId(), fixture.actor.displayName()))
        .isInstanceOfSatisfying(InfusionFailure.class, conflict -> {
          assertThat(conflict.type()).isEqualTo(InfusionFailure.Type.CONFLICT);
          assertThat(conflict.code()).isEqualTo("OUTSIDE_DAY_HOSPITAL_HOURS");
        });
  }

  private static boolean result(Future<Boolean> future) {
    try {
      return future.get();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new AssertionError(interrupted);
    } catch (ExecutionException failed) {
      throw new AssertionError(failed.getCause());
    }
  }

  private static Object outcome(Future<Object> future) {
    try {
      return future.get();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return interrupted;
    } catch (ExecutionException failed) {
      return failed.getCause();
    }
  }

  private static void await(CyclicBarrier barrier) {
    try {
      barrier.await();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    } catch (BrokenBarrierException broken) {
      throw new IllegalStateException(broken);
    }
  }

  private static final class AtomicStockJdbcTemplate extends JdbcTemplate {
    private final BigDecimal onHand;
    private final CyclicBarrier contenders;
    private BigDecimal reserved = BigDecimal.ZERO;
    private volatile String sql = "";

    private AtomicStockJdbcTemplate(BigDecimal onHand, int contenderCount) {
      this.onHand = onHand;
      this.contenders = new CyclicBarrier(contenderCount);
    }

    @Override
    public int update(String statement, Object... arguments) {
      sql = statement;
      await(contenders);
      BigDecimal requested = (BigDecimal) arguments[0];
      synchronized (this) {
        if (onHand.subtract(reserved).compareTo(requested) < 0) return 0;
        reserved = reserved.add(requested);
        return 1;
      }
    }

    private synchronized BigDecimal reserved() {
      return reserved;
    }

    private BigDecimal onHand() {
      return onHand;
    }

    private String sql() {
      return sql;
    }
  }

  private static final class SchedulingFixture {
    private final InfusionStore infusions = mock(InfusionStore.class);
    private final TreatmentApplicationLogisticsUseCase logistics = mock(TreatmentApplicationLogisticsUseCase.class);
    private final PostgresApplicationWorkflowStore workflows = mock(PostgresApplicationWorkflowStore.class);
    private final TreatmentStore treatments = mock(TreatmentStore.class);
    private final PatientUseCase patients = mock(PatientUseCase.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final SessionPrincipal actor = new SessionPrincipal(
        22, "admisiones", "", "Admisiones", "", "",
        true, null, List.of(), Set.of("application.schedule.manage"));
    private final PostgresInfusionOperationsStore store = new PostgresInfusionOperationsStore(
        infusions, logistics, workflows, treatments, patients, mapper, Clock.fixed(NOW, ZoneOffset.UTC));

    private SchedulingFixture() {
      Treatment treatment = new Treatment(
          "tx-1", 9, "diagnosis-1",
          LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1),
          1, 6, 21, "Oncológico", "Paliativo",
          "Diagnóstico", "scheme-1", "Esquema", "Oncólogo",
          "Iniciado", "Pendiente", false, 90,
          mapper.createObjectNode(), 1, NOW, NOW);
      Logistics planned = new Logistics(
          9, "tx-1", 1, 1, LocalDate.of(2026, 7, 30),
          "pending", "confirmed", 90, "protocol-day-estimate",
          "Paclitaxel", mapper.createArrayNode(), "", 1, NOW);
      ScheduleGate gate = new ScheduleGate(
          "confirmed", "active", false,
          "approved", "center_stock", "reserved",
          "pending", "", mapper.createObjectNode(),
          "not_started", "not_started", "scheduled", 1);
      Key key = new Key(9, "tx-1", 1, 1);

      when(treatments.find(9, "tx-1")).thenReturn(Optional.of(treatment));
      when(infusions.logistics(9, "tx-1", 1, 1)).thenReturn(Optional.of(planned));
      when(infusions.scheduleSettings()).thenReturn(new ScheduleSettings(6, 10, "08:00", "16:00"));
      when(workflows.scheduleGate(key)).thenReturn(Optional.of(gate));
      when(workflows.markAppointmentScheduled(
          any(Key.class), anyLong(), anyLong(), any(Instant.class))).thenReturn(true);
      when(infusions.medications(anyLong())).thenReturn(List.of());
    }

    private JsonNode scheduleInput(String scheduledAt, String chair) {
      return mapper.createObjectNode()
          .put("patientId", 9)
          .put("treatmentId", "tx-1")
          .put("cycleNumber", 1)
          .put("applicationDay", 1)
          .put("scheduledAt", scheduledAt)
          .put("chair", chair)
          .put("durationMinutes", 90);
    }

    private Infusion appointment() {
      return new Infusion(
          71, 9, "tx-1", 1, 1, null,
          Instant.parse("2026-07-30T13:00:00Z"), "1", 90,
          "planned", "pending", "not_started", false,
          "", mapper.createObjectNode(), 1, NOW, NOW,
          "30111222", "HC-9", "Ana", "Paciente",
          "Obra social", "1234", "Diagnóstico", "Esquema",
          "Oncológico", 6, 21);
    }
  }
}
