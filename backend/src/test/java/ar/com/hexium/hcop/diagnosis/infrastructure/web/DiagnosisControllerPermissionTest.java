package ar.com.hexium.hcop.diagnosis.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.diagnosis.application.port.in.DiagnosisUseCase;
import ar.com.hexium.hcop.diagnosis.application.port.in.DiagnosisUseCase.DiagnosisLinkResult;
import ar.com.hexium.hcop.diagnosis.application.port.in.DiagnosisUseCase.DiagnosisListView;
import ar.com.hexium.hcop.diagnosis.domain.DiagnosisRecord;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DiagnosisControllerPermissionTest {
  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void exigeLecturaDeHistoriaAntesDeListar() {
    DiagnosisUseCase diagnoses = mock(DiagnosisUseCase.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    DiagnosisRecord record = new DiagnosisRecord("dx-1", "Mama", "2026-01-01", "IIA", "raw");
    when(diagnoses.list(42L)).thenReturn(new DiagnosisListView(List.of(record), 5L));
    DiagnosisController controller = new DiagnosisController(diagnoses, auth);

    Map<String, Object> response = controller.list(42L, request);

    var order = inOrder(auth, diagnoses);
    order.verify(auth).requirePermission(request, "section.history.view");
    order.verify(diagnoses).list(42L);
    assertThat(response).containsEntry("ok", true).containsEntry("revision", 5L);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> diagnosisList = (List<Map<String, Object>>) response.get("diagnoses");
    assertThat(diagnosisList).hasSize(1);
    assertThat(diagnosisList.getFirst()).containsEntry("id", "dx-1").containsEntry("source", "raw");
  }

  @Test
  void exigeEdicionDeHistoriaAntesDeEnlazar() {
    DiagnosisUseCase diagnoses = mock(DiagnosisUseCase.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    DiagnosisRecord record = new DiagnosisRecord("dx-1", "Mama", "", "", null);
    when(diagnoses.link(42L, "dx-1", 3L)).thenReturn(new DiagnosisLinkResult(record, 6L));
    DiagnosisController controller = new DiagnosisController(diagnoses, auth);
    var body = mapper.createObjectNode().put("diagnosisEntryId", "dx-1").put("expectedRevision", 3);

    Map<String, Object> response = controller.link(42L, body, request);

    var order = inOrder(auth, diagnoses);
    order.verify(auth).requirePermission(request, "section.history.edit");
    order.verify(diagnoses).link(42L, "dx-1", 3L);
    assertThat(response).containsEntry("ok", true).containsEntry("linked", true).containsEntry("revision", 6L);
    @SuppressWarnings("unchecked")
    Map<String, Object> diagnosis = (Map<String, Object>) response.get("diagnosis");
    assertThat(diagnosis).containsEntry("id", "dx-1").doesNotContainKey("source");
  }
}
