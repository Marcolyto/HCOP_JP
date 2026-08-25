package ar.com.hexium.hcop.treatment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.catalog.TreatmentCatalogService;
import ar.com.hexium.hcop.infusion.InfusionService;
import ar.com.hexium.hcop.patient.PatientDocumentService;
import ar.com.hexium.hcop.patient.PatientService;
import ar.com.hexium.hcop.treatment.TreatmentRepository.Treatment;
import ar.com.hexium.hcop.treatment.TreatmentRepository.WorkflowState;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TreatmentServiceWorkflowStateTest {
  @Test
  void listIncludesPersistedContinuityPrescriptionAndPendingRequests() {
    var mapper = JsonMapper.builder().build();
    var repository = mock(TreatmentRepository.class);
    var patients = mock(PatientService.class);
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
    when(repository.list(10L)).thenReturn(List.of(treatment));
    when(repository.workflowStates(10L)).thenReturn(Map.of("trt-1", workflow));
    var service = new TreatmentService(
        repository, mock(TreatmentCatalogService.class), patients,
        mock(PatientDocumentService.class), mapper, Clock.systemUTC(),
        mock(InfusionService.class), mock(TreatmentProtocolCompatibility.class),
        mock(TreatmentCycleTimeline.class),
        new LegacyDoseUnitResolver(Path.of("runtime/catalogs/protocolos-lira/indicacionAplicacion.json"), mapper));

    var item = service.list(10L).getFirst();

    assertThat(item).containsEntry("workflowStatus", "temporary_hold")
        .containsEntry("effectiveFromCycle", 2)
        .containsEntry("prescriptionWorkflowState", "requested")
        .containsEntry("managementRevision", 4L);
    assertThat(((Map<?, ?>) item.get("prescriptionStates")).get(2)).isEqualTo("requested");
    assertThat(((Map<?, ?>) item.get("pendingRequestIds")).get("prescription_request"))
        .isEqualTo(91L);
  }
}
