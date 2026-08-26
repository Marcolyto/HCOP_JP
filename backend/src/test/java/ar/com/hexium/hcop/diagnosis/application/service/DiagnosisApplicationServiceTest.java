package ar.com.hexium.hcop.diagnosis.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.diagnosis.application.port.in.DiagnosisUseCase.DiagnosisLinkResult;
import ar.com.hexium.hcop.diagnosis.application.port.in.DiagnosisUseCase.DiagnosisListView;
import ar.com.hexium.hcop.diagnosis.application.port.out.PatientDiagnosisPort;
import ar.com.hexium.hcop.diagnosis.application.port.out.PatientDiagnosisPort.DiagnosisSnapshot;
import ar.com.hexium.hcop.diagnosis.domain.DiagnosisRecord;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiagnosisApplicationServiceTest {
  private final PatientDiagnosisPort port = mock(PatientDiagnosisPort.class);
  private final DiagnosisApplicationService service = new DiagnosisApplicationService(port);

  @Test
  void listaLosDiagnosticosDelSnapshot() {
    DiagnosisRecord record = new DiagnosisRecord("dx-1", "Mama", "2026-01-01", "IIA", "raw");
    when(port.snapshot(42L)).thenReturn(new DiagnosisSnapshot(7L, List.of(record)));

    DiagnosisListView view = service.list(42L);

    assertThat(view.revision()).isEqualTo(7L);
    assertThat(view.diagnoses()).containsExactly(record);
  }

  @Test
  void enlazaElPrimerDiagnosticoCuandoNoSeEspecificaId() {
    DiagnosisRecord first = new DiagnosisRecord("dx-1", "Mama", "", "", null);
    DiagnosisRecord second = new DiagnosisRecord("dx-2", "Pulmón", "", "", null);
    when(port.snapshot(1L)).thenReturn(new DiagnosisSnapshot(3L, List.of(first, second)));

    DiagnosisLinkResult result = service.link(1L, "", 0);

    assertThat(result.diagnosis()).isEqualTo(first);
    assertThat(result.revision()).isEqualTo(3L);
  }

  @Test
  void enlazaElDiagnosticoSolicitadoPorId() {
    DiagnosisRecord first = new DiagnosisRecord("dx-1", "Mama", "", "", null);
    DiagnosisRecord second = new DiagnosisRecord("dx-2", "Pulmón", "", "", null);
    when(port.snapshot(1L)).thenReturn(new DiagnosisSnapshot(3L, List.of(first, second)));

    DiagnosisLinkResult result = service.link(1L, "dx-2", 0);

    assertThat(result.diagnosis()).isEqualTo(second);
  }

  @Test
  void rechazaEnlaceConRevisionDesactualizada() {
    when(port.snapshot(1L)).thenReturn(new DiagnosisSnapshot(5L, List.of()));

    assertThatThrownBy(() -> service.link(1L, "", 4))
        .isInstanceOf(DiagnosisFailure.class)
        .satisfies(ex -> assertThat(((DiagnosisFailure) ex).type())
            .isEqualTo(DiagnosisFailure.Type.CONFLICT));
  }

  @Test
  void rechazaEnlaceSinDiagnosticoDisponible() {
    when(port.snapshot(1L)).thenReturn(new DiagnosisSnapshot(5L, List.of()));

    assertThatThrownBy(() -> service.link(1L, "", 0))
        .isInstanceOf(DiagnosisFailure.class)
        .satisfies(ex -> assertThat(((DiagnosisFailure) ex).type())
            .isEqualTo(DiagnosisFailure.Type.UNPROCESSABLE));
  }
}
