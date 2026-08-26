package ar.com.hexium.hcop.treatment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ar.com.hexium.hcop.patient.PatientDocumentService;
import ar.com.hexium.hcop.treatment.application.port.out.TreatmentApplicationSyncPort;
import ar.com.hexium.hcop.treatment.domain.Treatment;
import ar.com.hexium.hcop.treatment.domain.WorkflowState;
import ar.com.hexium.hcop.treatment.infrastructure.legacy.LegacyDoseUnitResolver;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

class PostgresTreatmentStoreViewTest {
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final PostgresTreatmentStore store = new PostgresTreatmentStore(
      mock(JdbcTemplate.class),
      mapper,
      Clock.systemUTC(),
      mock(TreatmentApplicationSyncPort.class),
      new LegacyDoseUnitResolver(
          Path.of("runtime/catalogs/protocolos-lira/indicacionAplicacion.json"), mapper),
      new TreatmentCycleTimeline(mapper),
      mock(PatientDocumentService.class));

  @Test
  void listIncludesPersistedContinuityPrescriptionAndPendingRequests() {
    var treatment = new Treatment(
        "trt-1", 10L, "dx-1", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5),
        1, 6, 21, "Quimioterapia", "Paliativo", "Pulmón", "scheme-1",
        "Carboplatino", "Dra. Test", "Iniciado", "Pendiente", false, 120,
        mapper.createObjectNode(), 1L, Instant.parse("2026-08-01T12:00:00Z"),
        Instant.parse("2026-08-01T12:00:00Z"));
    var workflow = new WorkflowState(
        "temporary_hold", 2, "Neutropenia", LocalDate.of(2026, 8, 20), true, 4L,
        Map.of(1, "confirmed", 2, "requested"),
        Map.of(2, Map.of("prescription_request", 91L)));

    var item = store.view(treatment, workflow, 120);

    assertThat(item).containsEntry("workflowStatus", "temporary_hold")
        .containsEntry("effectiveFromCycle", 2)
        .containsEntry("prescriptionWorkflowState", "requested")
        .containsEntry("managementRevision", 4L);
    assertThat(((Map<?, ?>) item.get("prescriptionStates")).get(2)).isEqualTo("requested");
    assertThat(((Map<?, ?>) item.get("pendingRequestIds")).get("prescription_request"))
        .isEqualTo(91L);
  }
}
