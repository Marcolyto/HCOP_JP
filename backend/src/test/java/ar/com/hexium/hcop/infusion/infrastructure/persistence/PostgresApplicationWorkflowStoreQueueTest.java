package ar.com.hexium.hcop.infusion.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.patient.application.port.in.PatientDocumentUseCase;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

class PostgresApplicationWorkflowStoreQueueTest {

  @Test
  void applicationsUsesItsOwnScheduledApplicationQueue() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(List.of());
    var store = new PostgresApplicationWorkflowStore(
        jdbc, JsonMapper.builder().build(), Clock.system(ZoneId.of("America/Argentina/Buenos_Aires")),
        mock(PatientDocumentUseCase.class));

    var result = store.listApplications("applications", LocalDate.of(2026, 8, 5), "ruarte", "");

    assertThat(result).isEmpty();
  }
}
