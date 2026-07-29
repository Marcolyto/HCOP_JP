package ar.com.hexium.hcop.treatment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class TreatmentCycleTimelineTest {
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final TreatmentCycleTimeline timeline = new TreatmentCycleTimeline(mapper);

  @Test
  void keepsEveryProtocolDayWhenARealApplicationExists() throws Exception {
    ObjectNode detail = detail();
    timeline.enrich(detail, List.of(Map.of(
        "id", "10",
        "cycleNumber", 1,
        "scheduledAt", "2026-07-29T09:15:00Z",
        "clinicalStatus", "checked_in",
        "administrationStatus", "not_started",
        "medications", List.of(Map.of(
            "drugName", "Irinotecan",
            "prescribedDoseText", "125 mg",
            "administrationStatus", "not_started")))));

    JsonNode firstCycle = detail.path("cycles").get(0);
    assertThat(firstCycle.path("state").asText()).isEqualTo("current");
    assertThat(firstCycle.path("days").size()).isEqualTo(4);
    assertThat(firstCycle.path("days").get(0).path("day").asInt()).isEqualTo(1);
    assertThat(firstCycle.path("days").get(1).path("day").asInt()).isEqualTo(8);
    assertThat(firstCycle.path("days").get(2).path("day").asInt()).isEqualTo(15);
    assertThat(firstCycle.path("days").get(3).path("day").asInt()).isEqualTo(21);
    assertThat(firstCycle.path("days").get(0).path("status").asText()).isEqualTo("current");
    assertThat(firstCycle.path("days").get(1).path("status").asText()).isEqualTo("pending");
    assertThat(detail.path("cycles").get(1).path("state").asText()).isEqualTo("pending");
    assertThat(detail.path("activeCycle").asInt()).isEqualTo(1);
  }

  @Test
  void distinguishesCompletedPartialAndCancelledCycles() throws Exception {
    ObjectNode detail = detail();
    timeline.enrich(detail, List.of(
        Map.of(
            "id", "11",
            "cycleNumber", 1,
            "scheduledAt", "2026-07-29T09:15:00Z",
            "clinicalStatus", "completed",
            "administrationStatus", "completed",
            "medications", List.of()),
        Map.of(
            "id", "12",
            "cycleNumber", 2,
            "scheduledAt", "2026-09-09T09:15:00Z",
            "clinicalStatus", "cancelled",
            "administrationStatus", "not_started",
            "medications", List.of())));

    assertThat(detail.path("cycles").get(0).path("state").asText()).isEqualTo("partial");
    assertThat(detail.path("cycles").get(1).path("state").asText()).isEqualTo("cancelled");
    assertThat(detail.path("activeCycle").asInt()).isEqualTo(1);
  }

  private ObjectNode detail() throws Exception {
    return (ObjectNode) mapper.readTree("""
        {
          "activeCycle": 1,
          "cycles": [
            {
              "number": 1,
              "plannedDate": "2026-07-29",
              "drugs": [
                {
                  "drugName": "Irinotecan",
                  "prescribedDoseText": "125 mg",
                  "applicationDays": "1 - 8 - 15 - 21"
                }
              ],
              "days": [],
              "applications": []
            },
            {
              "number": 2,
              "plannedDate": "2026-09-09",
              "drugs": [
                {
                  "drugName": "Irinotecan",
                  "prescribedDoseText": "125 mg",
                  "applicationDays": "1 - 8 - 15 - 21"
                }
              ],
              "days": [],
              "applications": []
            }
          ]
        }
        """);
  }
}
