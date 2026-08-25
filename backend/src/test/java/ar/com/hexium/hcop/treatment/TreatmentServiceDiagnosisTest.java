package ar.com.hexium.hcop.treatment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class TreatmentServiceDiagnosisTest {
  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void showsTheSavedDiagnosisBeforeCodeAndStage() throws Exception {
    JsonNode record = mapper.readTree("""
        {
          "diagnosis": "Tumor maligno de los bronquios y del pulmón",
          "stage": "IV",
          "diagnosticClassifications": {
            "cie10": {
              "code": "C34.90",
              "display": "Tumor maligno de bronquio o pulmón"
            }
          }
        }
        """);

    assertThat(TreatmentService.diagnosisDisplay(record))
        .isEqualTo("Tumor maligno de los bronquios y del pulmón · CIE-10 C34.90 · Estadio IV");
  }

  @Test
  void usesTheStructuredSnomedDiagnosisWhenTheLegacyFieldIsAbsent() throws Exception {
    JsonNode record = mapper.readTree("""
        {
          "diagnosticClassifications": {
            "snomed": {
              "code": "254637007",
              "display": "Carcinoma pulmonar"
            },
            "cie10": {
              "code": "C34.90",
              "display": "Tumor maligno de bronquio o pulmón"
            }
          },
          "tnm": {
            "stage": "IVA"
          }
        }
        """);

    assertThat(TreatmentService.diagnosisDisplay(record))
        .isEqualTo("Carcinoma pulmonar · CIE-10 C34.90 · Estadio IVA");
  }

  @Test
  void doesNotOfferAStageAsIfItWereADiagnosis() throws Exception {
    JsonNode record = mapper.readTree("""
        {
          "stage": "IV"
        }
        """);

    assertThat(TreatmentService.diagnosisDisplay(record)).isEmpty();
  }
}
