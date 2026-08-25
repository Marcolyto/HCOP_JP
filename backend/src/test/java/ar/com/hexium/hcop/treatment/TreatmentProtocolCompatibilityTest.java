package ar.com.hexium.hcop.treatment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TreatmentProtocolCompatibilityTest {
  private final TreatmentProtocolCompatibility compatibility = new TreatmentProtocolCompatibility();

  @Test
  void flagsAnObviousProstateDigestiveMismatch() {
    var result = compatibility.assess(
        "Neoplasia maligna de próstata · CIE-10 C61 · Estadio IIB",
        "DIGESTIVO - G: IRINOTECAN MONODROGA SEM");

    assertThat(result.mismatch()).isTrue();
    assertThat(result.diagnosisGroup()).isEqualTo("genitourinary");
    assertThat(result.protocolGroup()).isEqualTo("gastrointestinal");
  }

  @Test
  void acceptsAProtocolFromTheSameDiseaseFamily() {
    var result = compatibility.assess(
        "Tumor maligno de los bronquios y del pulmón",
        "PULMON - Carboplatino Pemetrexed Pembrolizumab");

    assertThat(result.mismatch()).isFalse();
    assertThat(result.diagnosisGroup()).isEqualTo("thoracic");
    assertThat(result.protocolGroup()).isEqualTo("thoracic");
  }

  @Test
  void leavesUnknownProtocolsForClinicalJudgementWithoutFalseWarnings() {
    var result = compatibility.assess(
        "Carcinoma de origen desconocido",
        "PROTOCOLO PERSONALIZADO ENSAYO 7");

    assertThat(result.mismatch()).isFalse();
    assertThat(result.protocolGroup()).isBlank();
  }

  @Test
  void treatsSupportProtocolsAsNeutral() {
    var result = compatibility.assess(
        "Tumor maligno de próstata",
        "Ácido Zoledrónico - soporte óseo");

    assertThat(result.mismatch()).isFalse();
    assertThat(result.protocolGroup()).isBlank();
  }
}
