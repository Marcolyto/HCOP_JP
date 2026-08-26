package ar.com.hexium.hcop.qr.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.qr.application.port.out.PatientEvolutionPort;
import ar.com.hexium.hcop.qr.application.port.out.QrInfusionPort;
import ar.com.hexium.hcop.qr.application.port.out.QrPatientPort;
import ar.com.hexium.hcop.qr.application.port.out.QrScanStore;
import ar.com.hexium.hcop.qr.application.port.in.QrUseCase.ScanCommand;
import ar.com.hexium.hcop.qr.application.port.out.QrTreatmentPort;
import ar.com.hexium.hcop.qr.domain.QrInfusionRef;
import ar.com.hexium.hcop.qr.domain.QrPatientView;
import ar.com.hexium.hcop.qr.domain.QrScan;
import ar.com.hexium.hcop.qr.domain.QrTreatmentView;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class QrApplicationServiceTest {
  private final QrPatientPort patients = mock(QrPatientPort.class);
  private final QrTreatmentPort treatments = mock(QrTreatmentPort.class);
  private final QrInfusionPort infusions = mock(QrInfusionPort.class);
  private final QrScanStore scans = mock(QrScanStore.class);
  private final PatientEvolutionPort evolutions = mock(PatientEvolutionPort.class);
  private final QrApplicationService service = new QrApplicationService(
      "test-qr-secret", patients, treatments, infusions, scans, evolutions, Clock.systemUTC());

  @Test
  void createsQrOnlyForAnExistingDayHospitalApplication() {
    when(treatments.findTreatment(7, "tx-1"))
        .thenReturn(Optional.of(new QrTreatmentView("tx-1", "Esquema", "Diagnostico")));
    when(infusions.dayHospitalEligibility(7, "tx-1", 2, 8)).thenReturn(Optional.of(true));

    assertThat(service.code(7, "tx-1", 2, 8)).startsWith("HCOPJP|2|7|");
  }

  @Test
  void rejectsQrWhenNoApplicationLogisticsExists() {
    when(treatments.findTreatment(7, "tx-1"))
        .thenReturn(Optional.of(new QrTreatmentView("tx-1", "Esquema", "Diagnostico")));
    when(infusions.dayHospitalEligibility(7, "tx-1", 2, 8)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.code(7, "tx-1", 2, 8))
        .isInstanceOf(QrFailure.class)
        .satisfies(error -> assertThat(((QrFailure) error).type()).isEqualTo(QrFailure.Type.CONFLICT))
        .hasMessageContaining("Hospital de Dia");
  }

  @Test
  void rejectsQrForAnOralOnlyLogisticsRow() {
    when(treatments.findTreatment(7, "tx-1"))
        .thenReturn(Optional.of(new QrTreatmentView("tx-1", "Esquema", "Diagnostico")));
    when(infusions.dayHospitalEligibility(7, "tx-1", 2, 1)).thenReturn(Optional.of(false));

    assertThatThrownBy(() -> service.code(7, "tx-1", 2, 1))
        .isInstanceOf(QrFailure.class)
        .satisfies(error -> assertThat(((QrFailure) error).type()).isEqualTo(QrFailure.Type.CONFLICT))
        .hasMessageContaining("dosis domiciliarias");
  }

  @Test
  void rejectsAnInvalidCycle() {
    assertThatThrownBy(() -> service.code(7, "tx-1", 0, 1))
        .isInstanceOf(QrFailure.class)
        .satisfies(error -> assertThat(((QrFailure) error).type()).isEqualTo(QrFailure.Type.INVALID));
  }

  @Test
  void rejectsATreatmentThatDoesNotExist() {
    when(treatments.findTreatment(7, "tx-1")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.code(7, "tx-1", 1, 1))
        .isInstanceOf(QrFailure.class)
        .satisfies(error -> assertThat(((QrFailure) error).type()).isEqualTo(QrFailure.Type.NOT_FOUND));
  }

  @Test
  void scanEsIdempotenteParaLaMismaOperacionYElMismoCodigo() {
    String code = "cualquier-codigo-ya-escaneado";
    QrScan previous = new QrScan(
        "op-1", sha256(code), 7, "tx-1", 2, 8, 55, 9, Instant.now());
    when(scans.findOperation("op-1")).thenReturn(Optional.of(previous));
    when(treatments.findTreatment(7, "tx-1"))
        .thenReturn(Optional.of(new QrTreatmentView("tx-1", "Esquema", "Diagnostico")));
    when(patients.requirePatient(7)).thenReturn(new QrPatientView(7, "Paciente", "111"));
    when(infusions.view(55)).thenReturn(Optional.of(Map.of("id", "55")));

    var result = service.scan(new ScanCommand(code, "op-1", 9, "Actor"));

    assertThat(result.idempotent()).isTrue();
    assertThat(result.evolution()).isNull();
    org.mockito.Mockito.verifyNoInteractions(evolutions);
  }

  @Test
  void scanRechazaLaOperacionSiElCodigoEsDistinto() {
    when(scans.findOperation("op-1")).thenReturn(Optional.of(
        new QrScan("op-1", "otro-hash", 7, "tx-1", 2, 8, 55, 9, Instant.now())));

    assertThatThrownBy(() -> service.scan(new ScanCommand("cualquier-codigo", "op-1", 9, "Actor")))
        .isInstanceOf(QrFailure.class)
        .satisfies(error -> assertThat(((QrFailure) error).type()).isEqualTo(QrFailure.Type.CONFLICT));
  }

  @Test
  void scanRealizaLaAdministracionYAnexaLaEvolucion() {
    when(treatments.findTreatment(7, "tx-1"))
        .thenReturn(Optional.of(new QrTreatmentView("tx-1", "Esquema", "Diagnostico")));
    when(infusions.dayHospitalEligibility(7, "tx-1", 2, 8)).thenReturn(Optional.of(true));
    String code = service.code(7, "tx-1", 2, 8);
    when(scans.findOperation("op-2")).thenReturn(Optional.empty());
    when(infusions.findByApplication(7, "tx-1", 2, 8)).thenReturn(Optional.of(
        new QrInfusionRef(55, 2, 8, "Esquema", Instant.now())));
    when(scans.insertIfAbsent(
        org.mockito.ArgumentMatchers.eq("op-2"), org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq("tx-1"),
        org.mockito.ArgumentMatchers.eq(2), org.mockito.ArgumentMatchers.eq(8),
        org.mockito.ArgumentMatchers.eq(55L), org.mockito.ArgumentMatchers.eq(9L),
        org.mockito.ArgumentMatchers.any())).thenReturn(true);
    when(evolutions.append(
        org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.eq("Actor")))
        .thenReturn(new PatientEvolutionPort.AppendedEvolution("evolution", 4L));
    when(patients.requirePatient(7)).thenReturn(new QrPatientView(7, "Paciente", "111"));
    when(infusions.view(55)).thenReturn(Optional.of(Map.of("id", "55")));

    var result = service.scan(new ScanCommand(code, "op-2", 9, "Actor"));

    assertThat(result.idempotent()).isFalse();
    assertThat(result.evolution()).isEqualTo("evolution");
    assertThat(result.documentRevision()).isEqualTo(4L);
  }

  private String sha256(String value) {
    return HexFormat.of().formatHex(sha256Bytes(value));
  }

  private byte[] sha256Bytes(String value) {
    try {
      return java.security.MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }
}
