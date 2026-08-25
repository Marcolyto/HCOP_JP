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

  @Test
  void separatesHomeDosesFromDayHospitalApplications() throws Exception {
    ObjectNode detail = (ObjectNode) mapper.readTree("""
        {
          "activeCycle": 1,
          "cycles": [{
            "number": 1,
            "plannedDate": "2026-08-01",
            "drugs": [
              {
                "drugName": "Capecitabina",
                "prescribedDoseText": "1500",
                "doseUnit": "mg",
                "applicationDays": "1 - 2 - 3 - 4 - 5",
                "route": "Oral",
                "source": {"seAplicaEnHdd": "1"}
              },
              {
                "drugName": "Trastuzumab",
                "prescribedDoseText": "600",
                "doseUnit": "mg",
                "applicationDays": "1",
                "route": "Subcutanea",
                "source": {"seAplicaEnHdd": "1"}
              }
            ],
            "days": [
              {"day": 1, "status": "pending"},
              {"day": 2, "status": "pending"},
              {"day": 3, "status": "pending"}
            ],
            "applications": []
          }]
        }
        """);

    timeline.enrich(detail, List.of());

    JsonNode cycle = detail.path("cycles").get(0);
    assertThat(cycle.path("days")).hasSize(1);
    assertThat(cycle.path("days").get(0).path("day").asInt()).isEqualTo(1);
    assertThat(cycle.path("days").get(0).path("medications")).hasSize(1);
    assertThat(cycle.path("days").get(0).path("medications").get(0)
        .path("drugName").asText()).isEqualTo("Trastuzumab");
    assertThat(cycle.path("homeMedications")).hasSize(1);
    assertThat(cycle.path("homeMedications").get(0).path("drugName").asText())
        .isEqualTo("Capecitabina");
    assertThat(cycle.path("homeMedications").get(0).path("careSetting").asText())
        .isEqualTo("home");
  }

  @Test
  void oralOnlyTreatmentDoesNotInventADayHospitalApplication() throws Exception {
    ObjectNode detail = (ObjectNode) mapper.readTree("""
        {
          "cycles": [{
            "number": 1,
            "plannedDate": "2026-08-01",
            "drugs": [{
              "drugName": "Letrozol",
              "applicationDays": "1 - 2 - 3",
              "route": "Oral",
              "source": {"seAplicaEnHdd": "1"}
            }],
            "days": [],
            "applications": []
          }]
        }
        """);

    timeline.enrich(detail, List.of());

    JsonNode cycle = detail.path("cycles").get(0);
    assertThat(cycle.path("days")).isEmpty();
    assertThat(cycle.path("applications")).isEmpty();
    assertThat(cycle.path("homeMedications")).hasSize(1);
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
