package ar.com.hexium.hcop.infusion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TreatmentApplicationPlannerTest {
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final TreatmentApplicationPlanner planner = new TreatmentApplicationPlanner();

  @Test
  void createsOneIndependentApplicationForEveryActiveDay() throws Exception {
    var detail = mapper.readTree("""
        {
          "cycles": [
            {
              "number": 1,
              "plannedDate": "2026-07-29",
              "drugs": [
                {
                  "drugName": "Ondansetron",
                  "applicationDays": "1 - 8 - 15 - 21",
                  "route": "Endovenosa",
                  "administrationTime": "EV BOLO",
                  "source": {"seAplicaEnHdd": "1"}
                },
                {
                  "drugName": "Irinotecan",
                  "applicationDays": "1 - 8 - 15 - 21",
                  "route": "Endovenosa",
                  "administrationTime": "EV 90 MIN",
                  "source": {"seAplicaEnHdd": "1"}
                }
              ]
            }
          ]
        }
        """);

    var applications = planner.plan(
        detail, LocalDate.of(2026, 7, 29), 1, 42, 120, 10);

    assertThat(applications).extracting(
        TreatmentApplicationPlanner.ApplicationPlan::applicationDay)
        .containsExactly(1, 8, 15, 21);
    assertThat(applications).extracting(
        TreatmentApplicationPlanner.ApplicationPlan::plannedDate)
        .containsExactly(
            LocalDate.of(2026, 7, 29),
            LocalDate.of(2026, 8, 5),
            LocalDate.of(2026, 8, 12),
            LocalDate.of(2026, 8, 18));
    assertThat(applications).extracting(
        TreatmentApplicationPlanner.ApplicationPlan::durationMinutes)
        .containsOnly(120);
    assertThat(applications).allSatisfy(application -> {
      assertThat(application.drugSummary()).isEqualTo("Ondansetron + Irinotecan");
      assertThat(application.drugs()).hasSize(2);
    });
  }

  @Test
  void givesShorterDaysAProportionalChairEstimate() throws Exception {
    var detail = mapper.readTree("""
        {
          "cycles": [
            {
              "number": 1,
              "plannedDate": "2026-08-01",
              "drugs": [
                {
                  "drugName": "Droga larga",
                  "applicationDays": "1",
                  "administrationTime": "EV 120 MIN",
                  "source": {"seAplicaEnHdd": "1"}
                },
                {
                  "drugName": "Droga corta",
                  "applicationDays": "1 - 8",
                  "administrationTime": "EV 30 MIN",
                  "source": {"seAplicaEnHdd": "1"}
                }
              ]
            }
          ]
        }
        """);

    var applications = planner.plan(
        detail, LocalDate.of(2026, 8, 1), 1, 21, 180, 10);

    assertThat(applications).hasSize(2);
    assertThat(applications.get(0).durationMinutes()).isEqualTo(180);
    assertThat(applications.get(1).durationMinutes()).isEqualTo(40);
  }

  @Test
  void recoversApplicationsFromTheProtocolForAnOlderSnapshot() throws Exception {
    var detail = mapper.readTree("""
        {
          "cycles": [
            {"number": 1, "plannedDate": "2026-07-29", "drugs": []}
          ]
        }
        """);
    var protocol = mapper.readTree("""
        {
          "drugs": [
            {
              "droga": "Ondansetron",
              "dia": "1 - 8 - 15 - 21",
              "tiempoAdministracion": "EV BOLO",
              "viaAdministracion": "Endovenosa",
              "seAplicaEnHdd": "1"
            },
            {
              "droga": "Irinotecan",
              "dia": "1 - 8 - 15 - 21",
              "tiempoAdministracion": "EV 90 MIN",
              "viaAdministracion": "Endovenosa",
              "seAplicaEnHdd": "1"
            }
          ]
        }
        """);

    var applications = planner.plan(
        detail, protocol, LocalDate.of(2026, 7, 29), 1, 42, 120, 5);

    assertThat(applications).hasSize(4);
    assertThat(applications).extracting(
        TreatmentApplicationPlanner.ApplicationPlan::applicationDay)
        .containsExactly(1, 8, 15, 21);
    assertThat(applications).extracting(
        TreatmentApplicationPlanner.ApplicationPlan::durationMinutes)
        .containsOnly(120);
  }

  @Test
  void excludesOralLegacyComponentsEvenWhenTheyWereMarkedForDayHospital() throws Exception {
    var detail = mapper.readTree("""
        {
          "cycles": [{
            "number": 1,
            "plannedDate": "2026-08-01",
            "drugs": [{
              "drugName": "Aprepitant",
              "applicationDays": "1 - 2 - 3",
              "route": "Oral",
              "source": {"seAplicaEnHdd": "1"}
            }]
          }]
        }
        """);

    assertThat(planner.plan(
        detail, LocalDate.of(2026, 8, 1), 1, 21, 60, 10)).isEmpty();
  }

  @Test
  void explicitChairRequirementCanOptAnOralComponentIntoAFutureWorkflow() throws Exception {
    var detail = mapper.readTree("""
        {
          "cycles": [{
            "number": 1,
            "plannedDate": "2026-08-01",
            "drugs": [{
              "drugName": "Tratamiento oral supervisado",
              "applicationDays": "1",
              "route": "Oral",
              "chairRequired": true,
              "source": {"seAplicaEnHdd": "1"}
            }]
          }]
        }
        """);

    assertThat(planner.plan(
        detail, LocalDate.of(2026, 8, 1), 1, 21, 30, 10))
        .singleElement()
        .satisfies(application -> assertThat(application.applicationDay()).isEqualTo(1));
  }

  @Test
  void parsesHourAbbreviationsAndCombinedHoursAndMinutes() throws Exception {
    var detail = mapper.readTree("""
        {
          "cycles": [{
            "number": 1,
            "plannedDate": "2026-08-01",
            "drugs": [
              {
                "drugName": "Droga A",
                "applicationDays": "1",
                "administrationTime": "1h y 30 min",
                "route": "Endovenosa"
              },
              {
                "drugName": "Droga B",
                "applicationDays": "8",
                "administrationTime": "2 hs",
                "route": "Endovenosa"
              },
              {
                "drugName": "Droga C",
                "applicationDays": "15",
                "administrationTime": "1,5 horas",
                "route": "Endovenosa"
              }
            ]
          }]
        }
        """);

    var applications = planner.plan(
        detail, LocalDate.of(2026, 8, 1), 1, 21, null, 10);

    assertThat(applications).extracting(
        TreatmentApplicationPlanner.ApplicationPlan::durationMinutes)
        .containsExactly(110, 140, 110);
  }

  @Test
  void countsOnlyTheLongestMedicationInAnExplicitParallelGroup() throws Exception {
    var detail = mapper.readTree("""
        {
          "cycles": [{
            "number": 1,
            "plannedDate": "2026-08-01",
            "drugs": [
              {
                "drugName": "Cisplatino",
                "applicationDays": "1",
                "administrationTime": "4 HORAS - puede pasar en paralelo con otras medicaciones",
                "route": "Endovenosa"
              },
              {
                "drugName": "Etoposido",
                "applicationDays": "1",
                "administrationTime": "4 hs - puede pasar en paralelo",
                "route": "Endovenosa"
              },
              {
                "drugName": "Ciclofosfamida",
                "applicationDays": "1",
                "administrationTime": "4h en paralelo",
                "route": "Endovenosa"
              },
              {
                "drugName": "Ondansetron",
                "applicationDays": "1",
                "administrationTime": "bolo lento",
                "route": "Endovenosa"
              }
            ]
          }]
        }
        """);

    var application = planner.plan(
        detail, LocalDate.of(2026, 8, 1), 1, 21, null, 10).getFirst();

    assertThat(application.durationMinutes()).isEqualTo(270);
    assertThat(application.durationSource()).isEqualTo("administration-times-plus-buffer");
  }

  @Test
  void keepsUnnamedMedicationsSequentialUnlessParallelismIsExplicit() throws Exception {
    var detail = mapper.readTree("""
        {
          "cycles": [{
            "number": 1,
            "plannedDate": "2026-08-01",
            "drugs": [
              {
                "drugName": "Droga A",
                "applicationDays": "1",
                "administrationTime": "1 h",
                "route": "Endovenosa"
              },
              {
                "drugName": "Droga B",
                "applicationDays": "1",
                "administrationTime": "1 hora",
                "route": "Endovenosa"
              }
            ]
          }]
        }
        """);

    var application = planner.plan(
        detail, LocalDate.of(2026, 8, 1), 1, 21, null, 10).getFirst();

    assertThat(application.durationMinutes()).isEqualTo(140);
  }
}
