package ar.com.hexium.hcop.diagnosis.infrastructure.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.diagnosis.application.port.out.PatientDiagnosisPort.DiagnosisSnapshot;
import ar.com.hexium.hcop.diagnosis.domain.DiagnosisRecord;
import ar.com.hexium.hcop.patient.PatientDocumentRepository.StoredDocument;
import ar.com.hexium.hcop.patient.PatientDocumentService;
import ar.com.hexium.hcop.patient.PatientService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class PatientDiagnosisAdapterTest {
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final PatientService patients = mock(PatientService.class);
  private final PatientDocumentService documents = mock(PatientDocumentService.class);
  private final PatientDiagnosisAdapter adapter = new PatientDiagnosisAdapter(patients, documents);

  @Test
  void proyectaLosRegistrosDeDiagnosticoIgnorandoArchivados() {
    ObjectNode document = mapper.createObjectNode();
    ObjectNode oncology = document.putObject("oncology");
    var records = oncology.putArray("diagnosisRecords");
    records.addObject().put("id", "dx-1").put("diagnosis", "Mama").put("date", "2026-01-01")
        .put("stage", "IIA");
    records.addObject().put("id", "dx-2").put("diagnosis", "Archivado").put("archived", true);
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    StoredDocument stored = new StoredDocument(1L, document, 9L, null, now, now);
    when(documents.require(1L)).thenReturn(stored);

    DiagnosisSnapshot snapshot = adapter.snapshot(1L);

    verify(patients).require(1L);
    assertThat(snapshot.revision()).isEqualTo(9L);
    assertThat(snapshot.records()).hasSize(1);
    DiagnosisRecord record = snapshot.records().getFirst();
    assertThat(record.id()).isEqualTo("dx-1");
    assertThat(record.display()).isEqualTo("Mama");
    assertThat(record.date()).isEqualTo("2026-01-01");
    assertThat(record.stage()).isEqualTo("IIA");
    assertThat(record.source()).isNotNull();
  }

  @Test
  void generaUnIdSinteticoCuandoElRegistroNoTraeUno() {
    ObjectNode document = mapper.createObjectNode();
    var records = document.putObject("oncology").putArray("diagnosisRecords");
    records.addObject().put("diagnosis", "Mama");
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    StoredDocument stored = new StoredDocument(1L, document, 1L, null, now, now);
    when(documents.require(1L)).thenReturn(stored);

    DiagnosisSnapshot snapshot = adapter.snapshot(1L);

    assertThat(snapshot.records().getFirst().id()).isEqualTo("diagnosis-1");
  }

  @Test
  void caeAlDiagnosticoOncologicoActualCuandoNoHayRegistros() {
    ObjectNode document = mapper.createObjectNode();
    ObjectNode oncology = document.putObject("oncology");
    oncology.put("diagnosis", "Pulmón").put("diagnosisDate", "2026-02-01").put("stage", "IB");
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    StoredDocument stored = new StoredDocument(1L, document, 2L, null, now, now);
    when(documents.require(1L)).thenReturn(stored);

    DiagnosisSnapshot snapshot = adapter.snapshot(1L);

    assertThat(snapshot.records()).hasSize(1);
    DiagnosisRecord record = snapshot.records().getFirst();
    assertThat(record.id()).isEqualTo("oncology-current");
    assertThat(record.display()).isEqualTo("Pulmón");
    assertThat(record.source()).isNull();
  }

  @Test
  void devuelveVacioCuandoNoHayNingunDiagnostico() {
    ObjectNode document = mapper.createObjectNode();
    document.putObject("oncology");
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    StoredDocument stored = new StoredDocument(1L, document, 1L, null, now, now);
    when(documents.require(1L)).thenReturn(stored);

    assertThat(adapter.snapshot(1L).records()).isEmpty();
  }
}
