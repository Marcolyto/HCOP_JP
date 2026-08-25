package ar.com.hexium.hcop.infusion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.patient.PatientDocumentService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ApplicationWorkflowServiceQueueTest {
  @Test
  void applicationsUsesItsOwnScheduledApplicationQueue() {
    var workflows = mock(ApplicationWorkflowRepository.class);
    var logistics = mock(TreatmentApplicationLogisticsService.class);
    when(workflows.list("applications", LocalDate.of(2026, 8, 5), "ruarte", ""))
        .thenReturn(List.of());
    var service = new ApplicationWorkflowService(
        workflows, logistics, mock(PatientDocumentService.class), JsonMapper.builder().build(),
        Clock.system(ZoneId.of("America/Argentina/Buenos_Aires")));

    var result = service.list("applications", LocalDate.of(2026, 8, 5), "ruarte", "");

    assertThat(result).isEmpty();
    verify(workflows).list("applications", LocalDate.of(2026, 8, 5), "ruarte", "");
  }
}
