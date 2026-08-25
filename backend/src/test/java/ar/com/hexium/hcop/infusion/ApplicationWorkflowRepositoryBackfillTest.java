package ar.com.hexium.hcop.infusion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;

import ar.com.hexium.hcop.infusion.ApplicationWorkflowRepository.Key;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

class ApplicationWorkflowRepositoryBackfillTest {

  @Test
  void runtimeBackfillNeverInventsPreparedOrReleasedTraceability() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    var repository =
        new ApplicationWorkflowRepository(jdbc, JsonMapper.builder().build(), Clock.systemUTC());
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);

    repository.ensureWorkflowRows();

    verify(jdbc).update(sql.capture());
    assertThat(sql.getValue())
        .contains("WHEN session.pharmacy_status IN ('ready','released')")
        .contains("WHEN 'ready' THEN CASE")
        .contains("WHEN 'released' THEN CASE")
        .doesNotContain("CASE WHEN session.id IS NOT NULL THEN 'approved'")
        .contains("ELSE 'not_started'")
        .doesNotContain("WHEN 'ready' THEN 'prepared'")
        .doesNotContain("WHEN 'released' THEN 'released'");
  }

  @Test
  void preparationUpdateBindsTimestampActorsAndVerifierInSqlOrder() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    var mapper = JsonMapper.builder().build();
    var repository =
        new ApplicationWorkflowRepository(jdbc, mapper, Clock.systemUTC());
    Instant now = Instant.parse("2026-07-29T15:00:00Z");
    Instant expiresAt = Instant.parse("2026-07-29T16:30:00Z");
    Key key = new Key(9, "tx-1", 2, 8);

    repository.updatePreparation(
        key, 7, "prepared", mapper.createObjectNode().put("lot", "A-1"),
        expiresAt, 44L, 22L, now);

    var invocation = mockingDetails(jdbc).getInvocations().iterator().next();
    Object[] rawArguments = invocation.getRawArguments();
    String sql = (String) rawArguments[0];
    Object[] parameters = (Object[]) rawArguments[1];
    assertThat(sql.chars().filter(character -> character == '?').count())
        .isEqualTo(27);
    assertThat(parameters).hasSize(27);
    assertThat(parameters[6]).isEqualTo(Timestamp.from(now));
    assertThat(parameters[8]).isEqualTo(22L);
    assertThat(parameters[10]).isEqualTo(44L);
    assertThat(parameters[12]).isEqualTo(Timestamp.from(now));
    assertThat(parameters[14]).isEqualTo(22L);
    assertThat(parameters[16]).isEqualTo(Timestamp.from(now));
    assertThat(parameters[17]).isEqualTo(Timestamp.from(expiresAt));
  }

  @Test
  void preparationLotPersistsTheCanonicalComponentKey() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    var repository =
        new ApplicationWorkflowRepository(jdbc, JsonMapper.builder().build(), Clock.systemUTC());
    Key key = new Key(9, "tx-1", 2, 8);
    Instant now = Instant.parse("2026-07-29T15:00:00Z");

    repository.insertPreparationLot(
        UUID.randomUUID(), key, "protocol-row-2", null, null,
        "Paclitaxel", "LOT-7", LocalDate.of(2027, 1, 1),
        new BigDecimal("80"), "80 mg", "mg", "SF", "250 ml",
        "0.32 mg/ml", 240, 22, 44, now);

    var invocation = mockingDetails(jdbc).getInvocations().iterator().next();
    Object[] rawArguments = invocation.getRawArguments();
    String sql = (String) rawArguments[0];
    Object[] parameters = (Object[]) rawArguments[1];
    assertThat(sql).contains("component_key");
    assertThat(parameters[5]).isEqualTo("protocol-row-2");
  }

  @Test
  void inventoryExpirationUsesTheClinicalLocalDateInsteadOfDatabaseCurrentDate() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    Instant nearUtcDayBoundary = Instant.parse("2026-07-30T02:30:00Z");
    Clock buenosAiresClock = Clock.fixed(
        nearUtcDayBoundary, ZoneId.of("America/Argentina/Buenos_Aires"));
    var repository = new ApplicationWorkflowRepository(
        jdbc, JsonMapper.builder().build(), buenosAiresClock);

    repository.reserveInventory(
        10, "drug-1", "Droga", new BigDecimal("25"), "mg",
        22, nearUtcDayBoundary);

    var invocation = mockingDetails(jdbc).getInvocations().iterator().next();
    Object[] rawArguments = invocation.getRawArguments();
    String sql = (String) rawArguments[0];
    Object[] parameters = (Object[]) rawArguments[1];
    assertThat(sql)
        .contains("expiration_date >= ?")
        .doesNotContain("CURRENT_DATE");
    assertThat(parameters).hasSize(11);
    assertThat(parameters[4]).isEqualTo(Date.valueOf(LocalDate.of(2026, 7, 29)));
  }

  @Test
  void preparationQueueKeepsReleasedRowsAndSearchesByMedicalRecord() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    Clock clock = Clock.fixed(
        Instant.parse("2026-07-29T15:00:00Z"),
        ZoneId.of("America/Argentina/Buenos_Aires"));
    var repository =
        new ApplicationWorkflowRepository(jdbc, JsonMapper.builder().build(), clock);

    repository.list("preparation", LocalDate.of(2026, 7, 29), "HC-1042", "");

    var invocation = mockingDetails(jdbc).getInvocations().iterator().next();
    Object[] rawArguments = invocation.getRawArguments();
    String sql = (String) rawArguments[0];
    Object[] parameters = (Object[]) rawArguments[2];
    assertThat(sql)
        .contains("w.preparation_status IN ('not_started','in_preparation','prepared','released')")
        .contains("p.medical_record_number")
        .contains("'ciclo', l.cycle_number")
        .contains("'dia', l.application_day")
        .contains("to_char(l.planned_date, 'DD/MM/YYYY')");
    assertThat(parameters).hasSize(3);
    assertThat(parameters).contains("%hc-1042%");
  }

  @Test
  void preparationRestartAlsoForcesAClinicalReevaluation() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    var repository =
        new ApplicationWorkflowRepository(jdbc, JsonMapper.builder().build(), Clock.systemUTC());

    repository.restartPreparation(
        new Key(9, "tx-1", 2, 8), 7, "TTL vencido", 22,
        Instant.parse("2026-07-29T15:00:00Z"));

    var invocation = mockingDetails(jdbc).getInvocations().iterator().next();
    String sql = (String) invocation.getRawArguments()[0];
    assertThat(sql)
        .contains("clinical_authorization_status = 'pending'")
        .contains("clinical_assessment = '{}'::jsonb")
        .contains("clinically_authorized_by = NULL")
        .contains("clinically_authorized_at = NULL");
  }
}
