package ar.com.hexium.hcop.sharedkernel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SharedKernelIdentifiersTest {

  @Test
  void conservaIdentificadoresGrandesSinPerderPrecision() {
    PatientId patientId = PatientId.of(8_000_000_000_000_000L);

    assertThat(patientId.value()).isEqualTo(8_000_000_000_000_000L);
  }

  @Test
  void normalizaElIdentificadorOpacoDelTratamiento() {
    assertThat(TreatmentId.of("  trt-123  ").value()).isEqualTo("trt-123");
  }

  @Test
  void identificaUnaAplicacionConCicloYDia() {
    ApplicationKey key = new ApplicationKey(
        PatientId.of(42),
        TreatmentId.of("trt-123"),
        2,
        15);

    assertThat(key.cycleNumber()).isEqualTo(2);
    assertThat(key.applicationDay()).isEqualTo(15);
  }

  @Test
  void incrementaLaRevisionSinMutarLaAnterior() {
    Revision initial = Revision.initial();

    assertThat(initial.next()).isEqualTo(new Revision(2));
    assertThat(initial).isEqualTo(new Revision(1));
  }

  @Test
  void rechazaIdentificadoresYPosicionesInvalidas() {
    assertThatThrownBy(() -> PatientId.of(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TreatmentId.of(" "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ApplicationKey(
        PatientId.of(1),
        TreatmentId.of("trt-1"),
        0,
        1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
