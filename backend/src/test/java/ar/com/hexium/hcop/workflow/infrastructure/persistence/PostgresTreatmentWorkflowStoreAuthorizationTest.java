package ar.com.hexium.hcop.workflow.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

class PostgresTreatmentWorkflowStoreAuthorizationTest {

  @Test
  void markSeenDoesNotReturnARequestThatWasNotAssignedToTheActor() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(), anyLong(), anyLong())).thenReturn(0);
    PostgresTreatmentWorkflowStore store = new PostgresTreatmentWorkflowStore(
        jdbc, JsonMapper.builder().build());

    assertThat(store.markSeen(91, 42, Instant.parse("2026-08-05T12:00:00Z")))
        .isEmpty();

    verify(jdbc).update(anyString(), any(), anyLong(), anyLong());
    verifyNoMoreInteractions(jdbc);
  }
}
