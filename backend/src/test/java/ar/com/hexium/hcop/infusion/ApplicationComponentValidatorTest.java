package ar.com.hexium.hcop.infusion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowCommands.Preparation;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowCommands.StockComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ApplicationComponentValidatorTest {
  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void repeatedDrugRowsReceiveStableUniqueOrdinalKeys() throws Exception {
    JsonNode drugs = drugsWithoutSourceRefs();

    List<StockComponent> components =
        ApplicationComponentValidator.componentsFromDrugs(drugs);

    assertThat(components)
        .extracting(StockComponent::componentKey)
        .containsExactly("drug-7-1", "drug-7-2");
    assertThat(components)
        .extracting(StockComponent::requestedQuantity)
        .containsExactly(new BigDecimal("10"), new BigDecimal("20"));
    assertThatCode(() ->
        ApplicationComponentValidator.validateStockComponents(drugs, components))
        .doesNotThrowAnyException();
  }

  @Test
  void explicitSourceReferencesRemainTheCanonicalComponentIdentity() throws Exception {
    JsonNode drugs = mapper.readTree("""
        [
          {
            "sourceItemRef": "protocol-row-a",
            "drugId": "drug-7",
            "drugName": "Paclitaxel",
            "prescribedDoseText": "80",
            "doseUnit": "mg"
          },
          {
            "sourceItemRef": "protocol-row-b",
            "drugId": "drug-7",
            "drugName": "Paclitaxel",
            "prescribedDoseText": "100",
            "doseUnit": "mg"
          }
        ]
        """);

    assertThat(ApplicationComponentValidator.componentsFromDrugs(drugs))
        .extracting(StockComponent::componentKey)
        .containsExactly("protocol-row-a", "protocol-row-b");
  }

  @Test
  void stockReservationRejectsSubsetsExtrasAndDuplicateKeys() throws Exception {
    JsonNode drugs = drugsWithoutSourceRefs();
    List<StockComponent> exact =
        ApplicationComponentValidator.componentsFromDrugs(drugs);

    assertThatThrownBy(() ->
        ApplicationComponentValidator.validateStockComponents(
            drugs, List.of(exact.getFirst())))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("exactamente todos");

    List<StockComponent> withExtra = new ArrayList<>(exact);
    withExtra.add(component("extra-3", "other", "Otra", "1", "mg"));
    assertThatThrownBy(() ->
        ApplicationComponentValidator.validateStockComponents(drugs, withExtra))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("exactamente todos");

    List<StockComponent> duplicate = List.of(
        exact.getFirst(),
        component(
            exact.getFirst().componentKey(),
            exact.getLast().drugId(),
            exact.getLast().drugName(),
            "20",
            exact.getLast().unit()));
    assertThatThrownBy(() ->
        ApplicationComponentValidator.validateStockComponents(drugs, duplicate))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("repite la clave");
  }

  @Test
  void stockReservationRejectsEveryPrescriptionFieldMismatch() throws Exception {
    JsonNode drugs = drugsWithoutSourceRefs();
    List<StockComponent> exact =
        ApplicationComponentValidator.componentsFromDrugs(drugs);
    StockComponent first = exact.getFirst();
    StockComponent second = exact.getLast();

    assertMismatch(drugs, component(
        first.componentKey(), first.drugId(), "Docetaxel", "10", first.unit()), second);
    assertMismatch(drugs, component(
        first.componentKey(), "wrong-id", first.drugName(), "10", first.unit()), second);
    assertMismatch(drugs, component(
        first.componentKey(), first.drugId(), first.drugName(), "10", "ml"), second);
    assertMismatch(drugs, component(
        first.componentKey(), first.drugId(), first.drugName(), "11", first.unit()), second);
  }

  @Test
  void preparationComparesTheWholeDrugMultisetForEveryMedicationSource() throws Exception {
    JsonNode drugs = drugsWithoutSourceRefs();
    List<Preparation> exact = List.of(
        preparation(null, "Paclitaxel", "10", "mg"),
        preparation(null, "Paclitaxel", "20", "mg"));

    assertThatCode(() ->
        ApplicationComponentValidator.validatePreparationMultiplicity(drugs, exact))
        .doesNotThrowAnyException();
    assertThatThrownBy(() ->
        ApplicationComponentValidator.validatePreparationMultiplicity(
            drugs, List.of(preparation(null, "Paclitaxel", "10", "mg"))))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("drogas repetidas");
    assertThatThrownBy(() ->
        ApplicationComponentValidator.validatePreparationMultiplicity(
            drugs, List.of(
                preparation(null, "Paclitaxel", "10", "mg"),
                preparation(null, "Paclitaxel", "20", "mg"),
                preparation(null, "Ondansetron", "8", "mg"))))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("exactamente una traza");
  }

  @Test
  void legacyPreparationWithoutKeysResolvesEachRepeatedDoseToItsCanonicalComponent()
      throws Exception {
    JsonNode drugs = drugsWithoutSourceRefs();

    var resolved = ApplicationComponentValidator.resolvePreparations(
        drugs,
        List.of(
            preparation(null, "Paclitaxel", "20", "mg"),
            preparation(null, "Paclitaxel", "10", "mg")));

    assertThat(resolved)
        .extracting(ApplicationComponentValidator.ResolvedPreparation::componentKey)
        .containsExactly("drug-7-2", "drug-7-1");
  }

  @Test
  void explicitPreparationKeyMustMatchNameDoseAndUnit() throws Exception {
    JsonNode drugs = drugsWithoutSourceRefs();

    assertThatThrownBy(() ->
        ApplicationComponentValidator.resolvePreparations(
            drugs,
            List.of(
                preparation("drug-7-1", "Paclitaxel", "20", "mg"),
                preparation("drug-7-2", "Paclitaxel", "10", "mg"))))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("cantidad preparada no coincide");

    assertThatThrownBy(() ->
        ApplicationComponentValidator.resolvePreparations(
            drugs,
            List.of(
                preparation("drug-7-1", "Paclitaxel", "10", "ml"),
                preparation("drug-7-2", "Paclitaxel", "20", "mg"))))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("unidad preparada no coincide");
  }

  @Test
  void identicalLegacyRowsReceiveDifferentCanonicalKeysInPrescriptionOrder()
      throws Exception {
    JsonNode drugs = mapper.readTree("""
        [
          {
            "drugId": "drug-7",
            "drugName": "Paclitaxel",
            "prescribedDoseText": "10",
            "doseUnit": "mg"
          },
          {
            "drugId": "drug-7",
            "drugName": "Paclitaxel",
            "prescribedDoseText": "10",
            "doseUnit": "mg"
          }
        ]
        """);

    var resolved = ApplicationComponentValidator.resolvePreparations(
        drugs,
        List.of(
            preparation(null, "Paclitaxel", "10", "mg"),
            preparation(null, "Paclitaxel", "10", "mg")));

    assertThat(resolved)
        .extracting(ApplicationComponentValidator.ResolvedPreparation::componentKey)
        .containsExactly("drug-7-1", "drug-7-2");
  }

  @Test
  void duplicateSourceReferencesAreRejectedInsteadOfCollapsingTwoDoses() throws Exception {
    JsonNode drugs = mapper.readTree("""
        [
          {
            "sourceItemRef": "same-row",
            "drugId": "drug-7",
            "drugName": "Paclitaxel",
            "prescribedDoseText": "80",
            "doseUnit": "mg"
          },
          {
            "sourceItemRef": "same-row",
            "drugId": "drug-7",
            "drugName": "Paclitaxel",
            "prescribedDoseText": "100",
            "doseUnit": "mg"
          }
        ]
        """);

    assertThatThrownBy(() ->
        ApplicationComponentValidator.componentsFromDrugs(drugs))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("sourceItemRef único");
  }

  private JsonNode drugsWithoutSourceRefs() throws Exception {
    return mapper.readTree("""
        [
          {
            "drugId": "drug-7",
            "drugName": "Paclitaxel",
            "prescribedDoseText": "10",
            "doseUnit": "mg"
          },
          {
            "drugId": "drug-7",
            "drugName": "Paclitaxel",
            "prescribedDoseText": "20",
            "doseUnit": "mg"
          }
        ]
        """);
  }

  private void assertMismatch(
      JsonNode drugs, StockComponent changed, StockComponent untouched) {
    assertThatThrownBy(() ->
        ApplicationComponentValidator.validateStockComponents(
            drugs, List.of(changed, untouched)))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("no coincide");
  }

  private StockComponent component(
      String key, String drugId, String name, String quantity, String unit) {
    return new StockComponent(
        key, drugId, name, new BigDecimal(quantity), quantity + " " + unit, unit, null);
  }

  private Preparation preparation(
      String componentKey, String drugName, String quantity, String unit) {
    return new Preparation(
        componentKey,
        drugName,
        "LOT-1",
        LocalDate.of(2027, 1, 1),
        new BigDecimal(quantity),
        quantity + " " + unit,
        unit,
        "SF",
        "100 ml",
        "0.01 mg/ml",
        120,
        null,
        null);
  }
}
