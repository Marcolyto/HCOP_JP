package ar.com.hexium.hcop.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.config.HcopProperties;
import ar.com.hexium.hcop.infusion.InfusionRepository;
import ar.com.hexium.hcop.infusion.InfusionRepository.Logistics;
import ar.com.hexium.hcop.infusion.InfusionService;
import ar.com.hexium.hcop.patient.PatientDocumentService;
import ar.com.hexium.hcop.patient.PatientService;
import ar.com.hexium.hcop.treatment.TreatmentRepository;
import ar.com.hexium.hcop.treatment.TreatmentRepository.Treatment;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

class QrWorkflowServiceApplicationTest {
  private final InfusionRepository infusions = mock(InfusionRepository.class);
  private final TreatmentRepository treatments = mock(TreatmentRepository.class);
  private final PatientService patients = mock(PatientService.class);
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final QrWorkflowService service = new QrWorkflowService(
      new HcopProperties(
          null, null, null, "", "", 60, 1_000_000, 1_000_000,
          "test-qr-secret", "test-encryption-secret"),
      infusions,
      mock(InfusionService.class),
      treatments,
      patients,
      mock(PatientDocumentService.class),
      mock(QrWorkflowRepository.class),
      mapper,
      Clock.systemUTC());

  @Test
  void createsQrOnlyForAnExistingDayHospitalApplication() throws Exception {
    Logistics logistics = mock(Logistics.class);
    when(logistics.applicationDrugs()).thenReturn(mapper.readTree("""
        [{
          "drugName": "Paclitaxel",
          "route": "Endovenosa",
          "applicationDays": "1",
          "source": {"seAplicaEnHdd": "1"}
        }]
        """));
    when(treatments.find(7, "tx-1")).thenReturn(Optional.of(mock(Treatment.class)));
    when(infusions.logistics(7, "tx-1", 2, 8)).thenReturn(Optional.of(logistics));

    assertThat(service.code(7, "tx-1", 2, 8)).startsWith("HCOPJP|2|7|");
  }

  @Test
  void rejectsQrWhenNoApplicationLogisticsExists() {
    when(treatments.find(7, "tx-1")).thenReturn(Optional.of(mock(Treatment.class)));
    when(infusions.logistics(7, "tx-1", 2, 8)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.code(7, "tx-1", 2, 8))
        .isInstanceOf(ApiException.class)
        .satisfies(error ->
            assertThat(((ApiException) error).status()).isEqualTo(HttpStatus.CONFLICT))
        .hasMessageContaining("Hospital de Dia");
  }

  @Test
  void rejectsQrForAnOralOnlyLogisticsRow() throws Exception {
    Logistics logistics = mock(Logistics.class);
    when(logistics.applicationDrugs()).thenReturn(mapper.readTree("""
        [{
          "drugName": "Capecitabina",
          "route": "Oral",
          "applicationDays": "1 - 14",
          "source": {"seAplicaEnHdd": "1"}
        }]
        """));
    when(treatments.find(7, "tx-1")).thenReturn(Optional.of(mock(Treatment.class)));
    when(infusions.logistics(7, "tx-1", 2, 1)).thenReturn(Optional.of(logistics));

    assertThatThrownBy(() -> service.code(7, "tx-1", 2, 1))
        .isInstanceOf(ApiException.class)
        .satisfies(error ->
            assertThat(((ApiException) error).status()).isEqualTo(HttpStatus.CONFLICT))
        .hasMessageContaining("dosis domiciliarias");
  }
}
