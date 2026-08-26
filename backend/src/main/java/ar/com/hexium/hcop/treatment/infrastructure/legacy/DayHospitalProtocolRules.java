package ar.com.hexium.hcop.treatment.infrastructure.legacy;

import ar.com.hexium.hcop.treatment.domain.DayHospitalApplicationPolicy;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * Reglas clínicas para decidir qué componentes de protocolo se vuelven aplicaciones operativas
 * de Hospital de Día — la mitad de {@code DayHospitalApplicationPolicy} original que navega
 * JSON, separada de {@link DayHospitalApplicationPolicy} (dominio puro, sin Jackson) porque
 * {@code domain}/{@code application} nunca pueden importar {@code tools.jackson}.
 */
public final class DayHospitalProtocolRules {
  private static final Pattern DAY_NUMBER = Pattern.compile("\\d+");

  private DayHospitalProtocolRules() {
  }

  public static Set<Integer> applicationDays(JsonNode component) {
    String value = firstText(component, "applicationDays", "dia", "days");
    if (value.isBlank()) {
      value = firstText(component.path("source"), "applicationDays", "dia", "days");
    }
    Set<Integer> result = new LinkedHashSet<>();
    Matcher matcher = DAY_NUMBER.matcher(value);
    while (matcher.find()) {
      try {
        int day = Integer.parseInt(matcher.group());
        if (DayHospitalApplicationPolicy.isValidApplicationDay(day)) result.add(day);
      } catch (NumberFormatException ignored) {
        // Preserve valid fragments when a legacy schedule contains malformed text.
      }
    }
    return result.isEmpty() ? Set.of(1) : Set.copyOf(result);
  }

  public static boolean requiresDayHospital(JsonNode component) {
    JsonNode source = component.path("source");
    Boolean chairRequired = booleanValue(component, "chairRequired");
    if (chairRequired == null) chairRequired = booleanValue(source, "chairRequired");
    if (chairRequired != null) return chairRequired;

    String route = normalize(firstText(component, "route", "viaAdministracion", "via"));
    if (route.isBlank()) {
      route = normalize(firstText(source, "route", "viaAdministracion", "via"));
    }
    if (route.contains("oral")) return false;

    Boolean explicit = booleanValue(
        component, "dayHospital", "seAplicaEnHdd", "usesDayHospital");
    if (explicit == null) {
      explicit = booleanValue(
          source, "dayHospital", "seAplicaEnHdd", "usesDayHospital");
    }
    return explicit == null || explicit;
  }

  private static Boolean booleanValue(JsonNode node, String... fields) {
    for (String field : fields) {
      JsonNode value = node.path(field);
      if (value.isMissingNode() || value.isNull()) continue;
      if (value.isBoolean()) return value.asBoolean();
      String text = normalize(value.asText(""));
      if (Set.of("1", "true", "si", "yes").contains(text)) return true;
      if (Set.of("0", "false", "no").contains(text)) return false;
    }
    return null;
  }

  private static String firstText(JsonNode node, String... fields) {
    for (String field : fields) {
      String value = node.path(field).asText("").trim();
      if (!value.isBlank()) return value;
    }
    return "";
  }

  private static String normalize(String value) {
    return Normalizer.normalize(
            value == null ? "" : value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .trim();
  }
}
