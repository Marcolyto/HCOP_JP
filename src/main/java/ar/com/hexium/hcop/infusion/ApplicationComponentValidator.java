package ar.com.hexium.hcop.infusion;

import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowCommands.Preparation;
import ar.com.hexium.hcop.infusion.ApplicationWorkflowCommands.StockComponent;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;

/**
 * Pure safety checks that keep one prescribed application, its stock reservation and its
 * preparation trace in one-to-one correspondence.
 */
final class ApplicationComponentValidator {
  private static final Pattern FIRST_NUMBER =
      Pattern.compile("[+-]?\\d+(?:[.,]\\d+)?");

  private ApplicationComponentValidator() {
  }

  static List<StockComponent> componentsFromDrugs(JsonNode drugs) {
    return expectedComponents(drugs).stream()
        .map(component -> new StockComponent(
            component.key(),
            component.drugId(),
            component.drugName(),
            component.quantity(),
            component.quantityText(),
            component.unit(),
            null))
        .toList();
  }

  static void validateStockComponents(JsonNode drugs, List<StockComponent> supplied) {
    List<ExpectedComponent> expected = expectedComponents(drugs);
    if (expected.isEmpty()) {
      throw conflict(
          "El protocolo no contiene componentes reservables.",
          "INCOMPLETE_PHARMACY_ORDER");
    }
    if (supplied == null || supplied.isEmpty()) {
      throw badRequest("Informe todos los componentes que se reservarán.");
    }
    if (supplied.size() != expected.size()) {
      throw conflict(
          "La reserva debe incluir exactamente todos los componentes de esta aplicación.",
          "STOCK_COMPONENT_MISMATCH");
    }

    Map<String, ExpectedComponent> byKey = new LinkedHashMap<>();
    for (ExpectedComponent component : expected) {
      byKey.put(component.key(), component);
    }
    Set<String> seen = new HashSet<>();
    for (StockComponent actual : supplied) {
      if (actual == null) {
        throw badRequest("La reserva contiene un componente vacío.");
      }
      String key = trim(actual.componentKey());
      if (key.isBlank()) {
        throw badRequest("Cada componente debe conservar su clave de prescripción.");
      }
      if (!seen.add(key)) {
        throw conflict(
            "La reserva repite la clave de componente " + key + ".",
            "DUPLICATE_STOCK_COMPONENT");
      }
      ExpectedComponent prescribed = byKey.get(key);
      if (prescribed == null) {
        throw conflict(
            "El componente " + key + " no pertenece a esta aplicación.",
            "STOCK_COMPONENT_MISMATCH");
      }
      requireEqual(
          normalizedText(actual.drugName()),
          normalizedText(prescribed.drugName()),
          "La droga de " + key + " no coincide con la prescripción.");
      requireEqual(
          trim(actual.drugId()),
          prescribed.drugId(),
          "El identificador de droga de " + key + " no coincide con la prescripción.");
      requireEqual(
          normalizedText(actual.unit()),
          normalizedText(prescribed.unit()),
          "La unidad de " + key + " no coincide con la prescripción.");
      if (actual.requestedQuantity() == null || prescribed.quantity() == null
          || actual.requestedQuantity().compareTo(prescribed.quantity()) != 0) {
        throw conflict(
            "La cantidad de " + key + " no coincide con la dosis prescripta.",
            "STOCK_COMPONENT_MISMATCH");
      }
    }
    if (seen.size() != byKey.size()) {
      throw conflict(
          "La reserva no incluye todos los componentes de esta aplicación.",
          "STOCK_COMPONENT_MISMATCH");
    }
  }

  static void validatePreparationMultiplicity(
      JsonNode drugs, List<Preparation> preparations) {
    resolvePreparations(drugs, preparations);
  }

  static List<ResolvedPreparation> resolvePreparations(
      JsonNode drugs, List<Preparation> preparations) {
    List<ExpectedComponent> expected = expectedComponents(drugs);
    if (preparations == null || preparations.size() != expected.size()) {
      throw conflict(
          "La preparación debe incluir exactamente una traza por cada componente prescripto, "
              + "incluidas las drogas repetidas.",
          "INCOMPLETE_PREPARATION_TRACE");
    }
    Map<String, ExpectedComponent> byKey = new LinkedHashMap<>();
    expected.forEach(component -> byKey.put(component.key(), component));
    Set<String> used = new HashSet<>();
    List<ResolvedPreparation> resolved = new ArrayList<>();
    for (Preparation preparation : preparations) {
      if (preparation == null) {
        throw badRequest("La preparación contiene un componente vacío.");
      }
      String suppliedKey = trim(preparation.componentKey());
      ExpectedComponent component;
      if (!suppliedKey.isBlank()) {
        component = byKey.get(suppliedKey);
        if (component == null) {
          throw conflict(
              "La preparación referencia un componente que no pertenece a esta aplicación.",
              "PREPARATION_COMPONENT_MISMATCH");
        }
        if (!used.add(component.key())) {
          throw conflict(
              "La preparación repite la clave de componente " + component.key() + ".",
              "DUPLICATE_PREPARATION_COMPONENT");
        }
      } else {
        List<ExpectedComponent> candidates = expected.stream()
            .filter(candidate -> !used.contains(candidate.key()))
            .filter(candidate -> samePreparationComponent(candidate, preparation))
            .toList();
        if (candidates.isEmpty()) {
          throw conflict(
              "No se pudo resolver la clave del componente preparado; envíe componentKey.",
              "PREPARATION_COMPONENT_MISMATCH");
        }
        component = candidates.getFirst();
        used.add(component.key());
      }
      validatePreparationComponent(component, preparation);
      resolved.add(new ResolvedPreparation(component.key(), preparation));
    }
    if (used.size() != expected.size()) {
      throw conflict(
          "La preparación no cubre todos los componentes prescriptos.",
          "INCOMPLETE_PREPARATION_TRACE");
    }
    return List.copyOf(resolved);
  }

  private static List<ExpectedComponent> expectedComponents(JsonNode drugs) {
    if (drugs == null || !drugs.isArray()) return List.of();
    List<ExpectedComponent> result = new ArrayList<>();
    Set<String> keys = new HashSet<>();
    int ordinal = 0;
    for (JsonNode drug : drugs) {
      ordinal++;
      String name = firstText(drug, "drugName", "droga", "name", "nombre", "genericName");
      if (name.isBlank()) {
        throw conflict(
            "La prescripción contiene un componente sin identificar.",
            "INCOMPLETE_PHARMACY_ORDER");
      }
      String drugId = firstText(drug, "drugId", "idDroga", "id");
      String explicitKey = firstText(drug, "sourceItemRef", "componentKey");
      if (explicitKey.isBlank() && drug.path("source").isObject()) {
        explicitKey = firstText(drug.path("source"), "sourceItemRef", "id");
      }
      String stem = drugId.isBlank() ? componentStem(name) : drugId;
      String key = explicitKey.isBlank() ? stem + "-" + ordinal : explicitKey;
      if (!keys.add(key)) {
        throw conflict(
            "La prescripción repite la clave de componente " + key
                + "; asigne un sourceItemRef único.",
            "DUPLICATE_APPLICATION_COMPONENT");
      }
      String quantityText = firstText(
          drug, "calculatedDoseText", "prescribedDoseText", "totalDoseText",
          "calculatedDose", "dose", "dosis", "dailyDose", "dosisDiaria");
      BigDecimal quantity = firstNumber(quantityText);
      String unit = firstText(drug, "doseUnit", "unidadDosis", "unidad");
      if (quantity == null || quantity.signum() <= 0 || unit.isBlank()) {
        throw conflict(
            "La prescripción de " + name + " no posee cantidad y unidad verificables.",
            "INCOMPLETE_PHARMACY_ORDER");
      }
      result.add(new ExpectedComponent(key, drugId, name, quantity, quantityText, unit));
    }
    return List.copyOf(result);
  }

  private static boolean samePreparationComponent(
      ExpectedComponent expected, Preparation actual) {
    return normalizedText(actual.drugName()).equals(normalizedText(expected.drugName()))
        && actual.quantity() != null
        && actual.quantity().compareTo(expected.quantity()) == 0
        && normalizedText(actual.unit()).equals(normalizedText(expected.unit()));
  }

  private static void validatePreparationComponent(
      ExpectedComponent expected, Preparation actual) {
    if (!normalizedText(actual.drugName()).equals(normalizedText(expected.drugName()))) {
      throw conflict(
          "La droga preparada no coincide con el componente " + expected.key() + ".",
          "PREPARATION_COMPONENT_MISMATCH");
    }
    if (actual.quantity() == null
        || actual.quantity().compareTo(expected.quantity()) != 0) {
      throw conflict(
          "La cantidad preparada no coincide con la dosis del componente "
              + expected.key() + ".",
          "PREPARATION_COMPONENT_MISMATCH");
    }
    if (!normalizedText(actual.unit()).equals(normalizedText(expected.unit()))) {
      throw conflict(
          "La unidad preparada no coincide con el componente " + expected.key() + ".",
          "PREPARATION_COMPONENT_MISMATCH");
    }
  }

  private static BigDecimal firstNumber(String value) {
    Matcher matcher = FIRST_NUMBER.matcher(trim(value));
    if (!matcher.find()) return null;
    try {
      return new BigDecimal(matcher.group().replace(',', '.'));
    } catch (NumberFormatException invalid) {
      return null;
    }
  }

  private static String firstText(JsonNode node, String... fields) {
    for (String field : fields) {
      String value = node.path(field).asText("").trim();
      if (!value.isBlank()) return value;
    }
    return "";
  }

  private static String componentStem(String value) {
    String normalized = Normalizer.normalize(trim(value), Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("(^-|-$)", "");
    return normalized.isBlank() ? "component" : normalized;
  }

  private static String normalizedText(String value) {
    return Normalizer.normalize(trim(value), Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("\\s+", " ");
  }

  private static void requireEqual(String actual, String expected, String message) {
    if (!actual.equals(expected)) {
      throw conflict(message, "STOCK_COMPONENT_MISMATCH");
    }
  }

  private static String trim(String value) {
    return value == null ? "" : value.trim();
  }

  private static ApiException badRequest(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, message);
  }

  private static ApiException conflict(String message, String code) {
    return new ApiException(HttpStatus.CONFLICT, message, code);
  }

  private record ExpectedComponent(
      String key,
      String drugId,
      String drugName,
      BigDecimal quantity,
      String quantityText,
      String unit) {
  }

  record ResolvedPreparation(String componentKey, Preparation preparation) {
  }
}
