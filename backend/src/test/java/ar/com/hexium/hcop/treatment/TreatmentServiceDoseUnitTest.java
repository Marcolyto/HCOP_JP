package ar.com.hexium.hcop.treatment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ar.com.hexium.hcop.catalog.TreatmentCatalogService;
import ar.com.hexium.hcop.infusion.InfusionService;
import ar.com.hexium.hcop.patient.PatientDocumentService;
import ar.com.hexium.hcop.patient.PatientService;
import java.lang.reflect.Method;
import java.time.Clock;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

class TreatmentServiceDoseUnitTest {
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final TreatmentService service = new TreatmentService(
      mock(TreatmentRepository.class),
      mock(TreatmentCatalogService.class),
      mock(PatientService.class),
      mock(PatientDocumentService.class),
      mapper,
      Clock.systemUTC(),
      mock(InfusionService.class),
      mock(TreatmentProtocolCompatibility.class),
      mock(TreatmentCycleTimeline.class),
      new LegacyDoseUnitResolver(
          Path.of("runtime/catalogs/protocolos-lira/indicacionAplicacion.json"),
          mapper));

  @Test
  void preservesAnExplicitDoseUnit() throws Exception {
    JsonNode drug = firstDrug("""
        {
          "drugs": [{
            "drugName": "Pembrolizumab",
            "dose": "200",
            "doseUnit": "mg",
            "calculationMethod": "Dosis fija"
          }]
        }
        """);

    assertThat(drug.path("prescribedDoseText").asText()).isEqualTo("200");
    assertThat(drug.path("doseUnit").asText()).isEqualTo("mg");
  }

  @Test
  void normalizesAnExplicitPerSurfaceUnitToTheFinalDoseUnit() throws Exception {
    JsonNode drug = firstDrug("""
        {
          "drugs": [{
            "drugName": "Paclitaxel",
            "dose": "80",
            "unidadDosis": "mg/m2",
            "calculationMethod": "Superficie corporal"
          }]
        }
        """);

    assertThat(drug.path("doseUnit").asText()).isEqualTo("mg");
  }

  @Test
  void recoversTheDoseUnitFromALegacyDoseText() throws Exception {
    JsonNode surfaceDrug = firstDrug("""
        {
          "drogas": [{
            "nombre": "Paclitaxel",
            "dosis": "80 mg/m²",
            "calculoDosis": "Superficie corporal"
          }]
        }
        """);
    JsonNode fixedDrug = firstDrug("""
        {
          "components": [{
            "name": "Dexametasona",
            "dose": "8 mg",
            "doseCalculation": "Dosis fija"
          }]
        }
        """);

    assertThat(surfaceDrug.path("doseUnit").asText()).isEqualTo("mg");
    assertThat(fixedDrug.path("doseUnit").asText()).isEqualTo("mg");
  }

  @Test
  void enrichesACommonLegacyProtocolFromItsPresentationCatalog() throws Exception {
    JsonNode drug = firstDrug("""
        {
          "drugs": [{
            "id": "13",
            "idDroga": "1109",
            "droga": "Carboplatino",
            "dosisDiaria": "6",
            "calculoDosis": "Calvert"
          }]
        }
        """);

    assertThat(drug.path("doseUnit").asText()).isEqualTo("mg");
    assertThat(drug.path("sourceItemRef").asText()).isEqualTo("13");
  }

  @Test
  void keepsRepeatedFluorouracilComponentsDistinct() throws Exception {
    ArrayNode drugs = extractDrugs("""
        {
          "drugs": [
            {
              "id": "391",
              "idDroga": "1331",
              "droga": "Fluorouracilo",
              "dosisDiaria": "400",
              "calculoDosis": "Superficie corporal"
            },
            {
              "id": "392",
              "idDroga": "1331",
              "droga": "Fluorouracilo",
              "dosisDiaria": "2400",
              "calculoDosis": "Superficie corporal"
            }
          ]
        }
        """);

    assertThat(drugs).hasSize(2);
    assertThat(drugs).extracting(item -> item.path("sourceItemRef").asText())
        .containsExactly("391", "392");
    assertThat(drugs).extracting(item -> item.path("doseUnit").asText())
        .containsOnly("mg");
  }

  private JsonNode firstDrug(String definition) throws Exception {
    ArrayNode drugs = extractDrugs(definition);
    assertThat(drugs).hasSize(1);
    return drugs.get(0);
  }

  private ArrayNode extractDrugs(String definition) throws Exception {
    Method extract = TreatmentService.class
        .getDeclaredMethod("extractDrugs", JsonNode.class, int.class);
    extract.setAccessible(true);
    return (ArrayNode) extract.invoke(service, mapper.readTree(definition), 1);
  }
}
