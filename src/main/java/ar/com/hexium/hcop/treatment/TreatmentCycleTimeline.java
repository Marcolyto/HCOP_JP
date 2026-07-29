package ar.com.hexium.hcop.treatment;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Builds the treatment-cycle timeline shown by the Lira-compatible detail view.
 *
 * <p>The protocol is the source of the expected application days. Infusion
 * records are then overlaid on those days, so a scheduled or completed
 * application never hides the remaining planned branches of the cycle.</p>
 */
@Component
public class TreatmentCycleTimeline {
  private static final Pattern DAY_NUMBER = Pattern.compile("\\d+");
  private static final Set<String> COMPLETED = Set.of("completed", "finalized", "finished");
  private static final Set<String> CANCELLED = Set.of("cancelled", "canceled", "suspended", "paused");
  private final ObjectMapper mapper;

  public TreatmentCycleTimeline(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public void enrich(ObjectNode detail, List<Map<String, Object>> sessions) {
    if (!detail.path("cycles").isArray()) return;
    Map<Integer, List<Map<String, Object>>> sessionsByCycle = new LinkedHashMap<>();
    for (Map<String, Object> session : sessions == null ? List.<Map<String, Object>>of() : sessions) {
      int cycle = integer(session.get("cycleNumber"), 0);
      if (cycle > 0) sessionsByCycle.computeIfAbsent(cycle, ignored -> new ArrayList<>()).add(session);
    }

    List<ObjectNode> cycles = new ArrayList<>();
    for (JsonNode value : detail.path("cycles")) {
      if (!(value instanceof ObjectNode cycle)) continue;
      int number = cycle.path("number").asInt();
      enrichCycle(cycle, sessionsByCycle.getOrDefault(number, List.of()));
      cycles.add(cycle);
    }

    ObjectNode active = cycles.stream()
        .filter(cycle -> isActionable(cycle.path("state").asText("")))
        .findFirst()
        .orElseGet(() -> cycles.isEmpty() ? null : cycles.get(cycles.size() - 1));
    if (active == null) return;
    int activeNumber = active.path("number").asInt();
    detail.put("activeCycle", activeNumber);
    if ("pending".equals(active.path("state").asText())) active.put("state", "current");
    markCurrentApplication(active);
  }

  private void enrichCycle(ObjectNode cycle, List<Map<String, Object>> sessions) {
    TreeSet<Integer> plannedDays = plannedDays(cycle.path("drugs"));
    Map<Integer, ObjectNode> existingDays = existingDays(cycle.path("days"));
    plannedDays.addAll(existingDays.keySet());

    List<ApplicationRecord> applications = new ArrayList<>();
    int sessionIndex = 0;
    for (Map<String, Object> session : sessions) {
      int applicationDay = applicationDay(session, plannedDays, sessionIndex++);
      plannedDays.add(applicationDay);
      ObjectNode application = mapper.valueToTree(session);
      application.put("applicationDay", applicationDay);
      application.put("date", string(session.get("scheduledAt")));
      application.put("applicationId", string(session.get("id")));
      if (!application.path("vitals").isObject()) application.set("vitals", mapper.createObjectNode());
      if (!application.path("observations").isArray()) application.set("observations", mapper.createArrayNode());
      applications.add(new ApplicationRecord(applicationDay, application, applicationState(session)));
    }
    if (plannedDays.isEmpty()) plannedDays.add(1);

    ArrayNode applicationNodes = mapper.createArrayNode();
    applications.forEach(item -> applicationNodes.add(item.application()));
    cycle.set("applications", applicationNodes);

    ArrayNode dayNodes = mapper.createArrayNode();
    LocalDate cycleDate = localDate(cycle.path("plannedDate").asText(cycle.path("date").asText("")));
    for (int dayNumber : plannedDays) {
      ObjectNode previous = existingDays.get(dayNumber);
      List<ApplicationRecord> dayApplications = applications.stream()
          .filter(item -> item.day() == dayNumber)
          .toList();
      String state = dayState(previous, dayApplications);
      ObjectNode day = mapper.createObjectNode();
      day.put("day", dayNumber);
      day.put("status", state);
      day.put("rest", previous != null && previous.path("rest").asBoolean(false));
      if (cycleDate != null) day.put("plannedDate", cycleDate.plusDays(dayNumber - 1L).toString());
      if (!dayApplications.isEmpty()) {
        ObjectNode application = dayApplications.get(0).application();
        day.put("applicationId", application.path("applicationId").asText(""));
        day.put("date", application.path("date").asText(""));
      } else if (previous != null) {
        day.put("applicationId", previous.path("applicationId").asText(""));
        day.put("date", previous.path("date").asText(""));
      }
      day.set("medications", medications(cycle.path("drugs"), dayNumber, previous, dayApplications));
      dayNodes.add(day);
    }
    cycle.set("days", dayNodes);
    cycle.put("state", cycleState(dayNodes, applications));
    cycle.put("disabled", false);
  }

  private TreeSet<Integer> plannedDays(JsonNode drugs) {
    TreeSet<Integer> result = new TreeSet<>();
    if (!drugs.isArray()) return result;
    for (JsonNode drug : drugs) result.addAll(dayNumbers(drug.path("applicationDays").asText("")));
    return result;
  }

  private Map<Integer, ObjectNode> existingDays(JsonNode days) {
    Map<Integer, ObjectNode> result = new LinkedHashMap<>();
    if (!days.isArray()) return result;
    for (JsonNode value : days) {
      if (value instanceof ObjectNode day && day.path("day").asInt() > 0) {
        result.put(day.path("day").asInt(), day);
      }
    }
    return result;
  }

  private TreeSet<Integer> dayNumbers(String value) {
    TreeSet<Integer> result = new TreeSet<>();
    Matcher matcher = DAY_NUMBER.matcher(value == null ? "" : value);
    while (matcher.find()) {
      int day = integer(matcher.group(), 0);
      if (day > 0 && day <= 366) result.add(day);
    }
    return result;
  }

  private int applicationDay(Map<String, Object> session, TreeSet<Integer> plannedDays, int index) {
    int direct = integer(session.get("applicationDay"), 0);
    if (direct <= 0 && session.get("sourceRef") instanceof JsonNode source) {
      direct = source.path("scheduler").path("applicationDay").asInt(
          source.path("applicationDay").asInt(0));
    }
    if (direct > 0) return direct;
    if (!plannedDays.isEmpty() && index < plannedDays.size()) {
      return new ArrayList<>(plannedDays).get(index);
    }
    return Math.max(1, index + 1);
  }

  private String applicationState(Map<String, Object> session) {
    String clinical = normalize(session.get("clinicalStatus"));
    String administration = normalize(session.get("administrationStatus"));
    if (COMPLETED.contains(clinical) || COMPLETED.contains(administration)) return "completed";
    if (CANCELLED.contains(clinical) || CANCELLED.contains(administration)) return "cancelled";
    return "current";
  }

  private String dayState(ObjectNode previous, List<ApplicationRecord> applications) {
    if (!applications.isEmpty()) {
      boolean allCompleted = applications.stream().allMatch(item -> "completed".equals(item.state()));
      boolean allCancelled = applications.stream().allMatch(item -> "cancelled".equals(item.state()));
      boolean anyCurrent = applications.stream().anyMatch(item -> "current".equals(item.state()));
      boolean anyCompleted = applications.stream().anyMatch(item -> "completed".equals(item.state()));
      if (allCompleted) return "completed";
      if (allCancelled) return "cancelled";
      if (anyCurrent) return "current";
      if (anyCompleted) return "partial";
    }
    String previousState = normalize(previous == null ? "" : previous.path("status").asText(""));
    if (COMPLETED.contains(previousState)) return "completed";
    if (CANCELLED.contains(previousState)) return "cancelled";
    if ("current".equals(previousState) || "partial".equals(previousState)) return previousState;
    return "pending";
  }

  private ArrayNode medications(
      JsonNode drugs,
      int dayNumber,
      ObjectNode previous,
      List<ApplicationRecord> applications) {
    ArrayNode actual = mapper.createArrayNode();
    for (ApplicationRecord record : applications) {
      JsonNode medications = record.application().path("medications");
      if (!medications.isArray()) continue;
      for (JsonNode medication : medications) {
        if (!(medication instanceof ObjectNode)) continue;
        ObjectNode row = (ObjectNode) medication.deepCopy();
        String administration = normalize(row.path("administrationStatus").asText(""));
        row.put("status", COMPLETED.contains(administration) ? "administered"
            : CANCELLED.contains(administration) ? "withheld" : "planned");
        actual.add(row);
      }
    }
    if (!actual.isEmpty()) return actual;
    if (previous != null && previous.path("medications").isArray()
        && !previous.path("medications").isEmpty()) {
      return (ArrayNode) previous.path("medications").deepCopy();
    }

    ArrayNode planned = mapper.createArrayNode();
    if (!drugs.isArray()) return planned;
    for (JsonNode drug : drugs) {
      TreeSet<Integer> days = dayNumbers(drug.path("applicationDays").asText(""));
      if (!days.isEmpty() && !days.contains(dayNumber)) continue;
      ObjectNode medication = planned.addObject();
      medication.put("drugName", drug.path("drugName").asText("Droga"));
      medication.put("actualDoseText", drug.path("prescribedDoseText").asText(""));
      medication.put("prescribedDoseText", "");
      medication.put("status", "planned");
    }
    return planned;
  }

  private String cycleState(ArrayNode days, List<ApplicationRecord> applications) {
    List<String> states = new ArrayList<>();
    days.forEach(day -> states.add(day.path("status").asText("pending")));
    if (!states.isEmpty() && states.stream().allMatch("completed"::equals)) return "completed";
    if (!applications.isEmpty() && applications.stream().allMatch(item -> "cancelled".equals(item.state()))) {
      return "cancelled";
    }
    if (states.stream().anyMatch("current"::equals)) return "current";
    if (states.stream().anyMatch("completed"::equals) || states.stream().anyMatch("partial"::equals)) {
      return "partial";
    }
    return "pending";
  }

  private void markCurrentApplication(ObjectNode cycle) {
    JsonNode days = cycle.path("days");
    if (!days.isArray()) return;
    boolean hasCurrent = false;
    for (JsonNode day : days) {
      if ("current".equals(day.path("status").asText())
          || "partial".equals(day.path("status").asText())) {
        hasCurrent = true;
        break;
      }
    }
    if (hasCurrent) return;
    for (JsonNode day : days) {
      if (day instanceof ObjectNode object && "pending".equals(day.path("status").asText())) {
        object.put("status", "current");
        break;
      }
    }
  }

  private boolean isActionable(String state) {
    return !"completed".equals(state) && !"cancelled".equals(state);
  }

  private LocalDate localDate(String value) {
    try {
      return value == null || value.isBlank() ? null : LocalDate.parse(value.substring(0, 10));
    } catch (DateTimeParseException | IndexOutOfBoundsException ignored) {
      return null;
    }
  }

  private int integer(Object value, int fallback) {
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private String string(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private String normalize(Object value) {
    return string(value).trim().toLowerCase();
  }

  private record ApplicationRecord(int day, ObjectNode application, String state) {
  }
}
