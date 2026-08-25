package ar.com.hexium.hcop.infusion;

import ar.com.hexium.hcop.treatment.DayHospitalApplicationPolicy;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * Converts the prescribed protocol snapshot into the real Hospital de Día workload.
 * A plan is produced for each cycle/day that contains at least one medication to be
 * administered in the centre.
 */
@Component
public class TreatmentApplicationPlanner {
  private static final Pattern NUMBER = Pattern.compile("(\\d+(?:[.,]\\d+)?)");
  private static final Pattern HOURS = Pattern.compile(
      "(\\d+(?:[.,]\\d+)?)\\s*(?:h(?:s|r|rs)?\\.?|hora(?:s)?)\\b");
  private static final Pattern MINUTES = Pattern.compile(
      "(\\d+(?:[.,]\\d+)?)\\s*(?:min(?:uto)?s?\\.?)\\b");
  private static final int DEFAULT_SLOT_MINUTES = 10;
  private static final int DEFAULT_DURATION_MINUTES = 60;
  private static final int OPERATIONAL_BUFFER_MINUTES = 20;

  public List<ApplicationPlan> plan(
      JsonNode detail,
      LocalDate firstCycleDate,
      int initialCycle,
      int cycleDays,
      Integer protocolDurationMinutes,
      int configuredSlotMinutes) {
    return plan(
        detail, null, firstCycleDate, initialCycle, cycleDays,
        protocolDurationMinutes, configuredSlotMinutes);
  }

  public List<ApplicationPlan> plan(
      JsonNode detail,
      JsonNode protocolDefinition,
      LocalDate firstCycleDate,
      int initialCycle,
      int cycleDays,
      Integer protocolDurationMinutes,
      int configuredSlotMinutes) {
    int slotMinutes = supportedSlot(configuredSlotMinutes);
    List<Draft> drafts = new ArrayList<>();
    JsonNode cycles = detail.path("cycles");
    if (!cycles.isArray()) return List.of();

    for (JsonNode cycle : cycles) {
      int cycleNumber = cycle.path("number").asInt(0);
      if (cycleNumber < 1) continue;
      LocalDate cycleDate = date(cycle.path("plannedDate").asText(""));
      if (cycleDate == null) cycleDate = date(cycle.path("date").asText(""));
      if (cycleDate == null && firstCycleDate != null) {
        cycleDate = cycleDays > 0
            ? firstCycleDate.plusDays((long) (cycleNumber - initialCycle) * cycleDays)
            : firstCycleDate;
      }

      JsonNode cycleDrugs = cycle.path("drugs");
      if (!cycleDrugs.isArray() || cycleDrugs.isEmpty()) {
        cycleDrugs = protocolDrugs(protocolDefinition);
      }
      TreeMap<Integer, ArrayNode> drugsByDay = new TreeMap<>();
      for (JsonNode drug : cycleDrugs) {
        if (!DayHospitalApplicationPolicy.requiresDayHospital(drug)) continue;
        Set<Integer> applicationDays = DayHospitalApplicationPolicy.applicationDays(drug);
        for (int applicationDay : applicationDays) {
          drugsByDay.computeIfAbsent(
              applicationDay, ignored -> JsonNodeFactory.instance.arrayNode()).add(drug.deepCopy());
        }
      }

      for (var entry : drugsByDay.entrySet()) {
        ArrayNode applicationDrugs = entry.getValue();
        int rawMinutes = administrationMinutesForDay(applicationDrugs);
        String drugSummary = drugSummary(applicationDrugs);
        LocalDate plannedDate = cycleDate == null
            ? null : cycleDate.plusDays(entry.getKey() - 1L);
        drafts.add(new Draft(
            cycleNumber, entry.getKey(), plannedDate, rawMinutes, drugSummary, applicationDrugs));
      }
    }

    if (drafts.isEmpty()) return List.of();
    int maximumRawMinutes = drafts.stream().mapToInt(Draft::rawMinutes).max().orElse(0);
    int calculatedReference = maximumRawMinutes > 0
        ? roundUp(maximumRawMinutes + OPERATIONAL_BUFFER_MINUTES, slotMinutes)
        : DEFAULT_DURATION_MINUTES;
    int referenceDuration = protocolDurationMinutes != null && protocolDurationMinutes > 0
        ? Math.max(protocolDurationMinutes, calculatedReference)
        : calculatedReference;
    referenceDuration = Math.min(1440, roundUp(referenceDuration, slotMinutes));

    List<ApplicationPlan> result = new ArrayList<>();
    for (Draft draft : drafts) {
      int duration;
      String source;
      if (maximumRawMinutes > 0 && draft.rawMinutes() > 0) {
        double ratio = (double) draft.rawMinutes() / maximumRawMinutes;
        duration = Math.max(slotMinutes, roundUp((int) Math.ceil(referenceDuration * ratio), slotMinutes));
        source = protocolDurationMinutes != null && protocolDurationMinutes > 0
            ? "protocol-adjusted-by-day"
            : "administration-times-plus-buffer";
      } else {
        duration = referenceDuration;
        source = protocolDurationMinutes != null && protocolDurationMinutes > 0
            ? "protocol-fallback"
            : "operational-default";
      }
      result.add(new ApplicationPlan(
          draft.cycleNumber(), draft.applicationDay(), draft.plannedDate(),
          Math.min(1440, duration), source, draft.drugSummary(), draft.drugs()));
    }
    return List.copyOf(result);
  }

  private JsonNode protocolDrugs(JsonNode definition) {
    if (definition == null || definition.isMissingNode() || definition.isNull()) {
      return JsonNodeFactory.instance.arrayNode();
    }
    for (String field : List.of("drugs", "drogas", "components")) {
      JsonNode drugs = definition.path(field);
      if (drugs.isArray()) return drugs;
    }
    return JsonNodeFactory.instance.arrayNode();
  }

  /**
   * Adds sequential steps, but takes only the longest step in each explicitly
   * parallel group. Components are never assumed to run in parallel merely
   * because they share a day; that conservative rule avoids underbooking chairs.
   */
  private int administrationMinutesForDay(ArrayNode drugs) {
    int sequentialMinutes = 0;
    Map<String, Integer> parallelGroups = new LinkedHashMap<>();
    for (JsonNode drug : drugs) {
      int minutes = administrationMinutes(drug);
      if (minutes < 1) continue;
      String group = parallelGroup(drug);
      if (group.isBlank()) {
        sequentialMinutes += minutes;
      } else {
        parallelGroups.merge(group, minutes, Math::max);
      }
    }
    return sequentialMinutes
        + parallelGroups.values().stream().mapToInt(Integer::intValue).sum();
  }

  private String parallelGroup(JsonNode drug) {
    JsonNode source = drug.path("source");
    String named = firstText(
        drug, "parallelGroup", "administrationGroup", "grupoParalelo", "grupoAdministracion");
    if (named.isBlank()) {
      named = firstText(
          source, "parallelGroup", "administrationGroup", "grupoParalelo", "grupoAdministracion");
    }
    if (!named.isBlank()) return "named:" + normalize(named);

    String time = normalize(firstText(
        drug, "administrationTime", "tiempoAdministracion", "time"));
    if (time.isBlank()) {
      time = normalize(firstText(
          source, "administrationTime", "tiempoAdministracion", "time"));
    }
    if (time.contains("paralel") || time.contains("simultan")
        || truthy(drug.path("parallel")) || truthy(source.path("parallel"))) {
      return "legacy-explicit-parallel";
    }
    return "";
  }

  private boolean truthy(JsonNode value) {
    if (value.isBoolean()) return value.asBoolean();
    return Set.of("1", "true", "si", "yes").contains(normalize(value.asText("")));
  }

  private int administrationMinutes(JsonNode drug) {
    String time = normalize(firstText(drug, "administrationTime", "tiempoAdministracion", "time"));
    if (time.isBlank()) {
      time = normalize(firstText(drug.path("source"), "administrationTime", "tiempoAdministracion", "time"));
    }
    double minutes = sumUnits(HOURS, time) * 60 + sumUnits(MINUTES, time);
    if (minutes > 0) return Math.max(1, (int) Math.ceil(minutes));
    Matcher number = NUMBER.matcher(time);
    if (number.find()) return positiveMinutes(number.group(1));
    if (time.contains("bolo")) return 5;
    String route = normalize(firstText(drug, "route", "viaAdministracion", "via"));
    if (route.contains("subcut") || route.contains("intramus")) return 10;
    return 0;
  }

  private double sumUnits(Pattern pattern, String value) {
    double result = 0;
    Matcher matcher = pattern.matcher(value);
    while (matcher.find()) {
      try {
        result += Double.parseDouble(matcher.group(1).replace(',', '.'));
      } catch (NumberFormatException ignored) {
        // Continue parsing other explicit units in the same legacy instruction.
      }
    }
    return result;
  }

  private int positiveMinutes(String value) {
    try {
      double parsed = Double.parseDouble(value.replace(',', '.'));
      return parsed > 0 ? Math.max(1, (int) Math.ceil(parsed)) : 0;
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }

  private String drugSummary(ArrayNode drugs) {
    Set<String> names = new LinkedHashSet<>();
    for (JsonNode drug : drugs) {
      String name = firstText(drug, "drugName", "droga", "name", "nombre");
      if (!name.isBlank()) names.add(name);
    }
    return String.join(" + ", names);
  }

  private String firstText(JsonNode node, String... fields) {
    for (String field : fields) {
      String value = node.path(field).asText("").trim();
      if (!value.isBlank()) return value;
    }
    return "";
  }

  private LocalDate date(String value) {
    try {
      return value == null || value.isBlank() ? null : LocalDate.parse(value);
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  private int roundUp(int value, int slotMinutes) {
    return Math.max(slotMinutes, ((Math.max(1, value) + slotMinutes - 1) / slotMinutes) * slotMinutes);
  }

  private int supportedSlot(int value) {
    return Set.of(5, 10, 15, 20, 30).contains(value) ? value : DEFAULT_SLOT_MINUTES;
  }

  private String normalize(String value) {
    return java.text.Normalizer.normalize(
            value == null ? "" : value, java.text.Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .trim();
  }

  private record Draft(
      int cycleNumber,
      int applicationDay,
      LocalDate plannedDate,
      int rawMinutes,
      String drugSummary,
      ArrayNode drugs) {
  }

  public record ApplicationPlan(
      int cycleNumber,
      int applicationDay,
      LocalDate plannedDate,
      int durationMinutes,
      String durationSource,
      String drugSummary,
      ArrayNode drugs) {
  }
}
