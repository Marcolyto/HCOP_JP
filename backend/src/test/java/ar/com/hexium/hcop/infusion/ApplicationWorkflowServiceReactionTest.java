package ar.com.hexium.hcop.infusion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class ApplicationWorkflowServiceReactionTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void completionWithoutInterruptionUsesTheReportedReaction() {
    ObjectNode administration = mapper.createObjectNode();

    var none = ApplicationWorkflowService.resolveAdministrationReaction(
        administration, false, "texto que no corresponde");
    var reported = ApplicationWorkflowService.resolveAdministrationReaction(
        administration, true, "Eritema leve, tratado con antihistamínico");

    assertThat(none.occurred()).isFalse();
    assertThat(none.description()).isEmpty();
    assertThat(none.derivedFromInterruptions()).isFalse();
    assertThat(none.interruptionCount()).isZero();
    assertThat(reported.occurred()).isTrue();
    assertThat(reported.description())
        .isEqualTo("Eritema leve, tratado con antihistamínico");
  }

  @Test
  void interruptionCannotBeOverwrittenByACompletionReportedWithoutReaction() {
    ObjectNode administration = mapper.createObjectNode();
    administration.withArray("interruptions").addObject()
        .put("interruptedAt", "2026-07-30T13:15:00Z")
        .put("reason", "Broncoespasmo")
        .put("measures", "Se detuvo la infusión y se administró rescate")
        .put("patientCondition", "Estable luego del rescate")
        .put("disposition", "medical_review");

    var result = ApplicationWorkflowService.resolveAdministrationReaction(
        administration, false, "");

    assertThat(result.occurred()).isTrue();
    assertThat(result.derivedFromInterruptions()).isTrue();
    assertThat(result.interruptionCount()).isEqualTo(1);
    assertThat(result.description())
        .contains("Interrupción 1 (2026-07-30T13:15:00Z)")
        .contains("Motivo: Broncoespasmo")
        .contains("Medidas: Se detuvo la infusión y se administró rescate")
        .contains("Condición: Estable luego del rescate")
        .contains("Destino: Evaluación médica inmediata");
  }

  @Test
  void completionPreservesBothExplicitDescriptionAndEveryHistoricalInterruption() {
    ObjectNode administration = mapper.createObjectNode();
    administration.withArray("interruptions")
        .addObject()
        .put("reason", "Hipotensión")
        .put("measures", "Pausa y control")
        .put("patientCondition", "Recuperado")
        .put("disposition", "observation");
    administration.withArray("interruptions")
        .addObject()
        .put("reason", "Reaparición de síntomas")
        .put("measures", "Evaluación médica")
        .put("patientCondition", "Estable")
        .put("disposition", "emergency_transfer");

    var result = ApplicationWorkflowService.resolveAdministrationReaction(
        administration, true, "Reacción resuelta; se completó a menor velocidad");

    assertThat(result.occurred()).isTrue();
    assertThat(result.interruptionCount()).isEqualTo(2);
    assertThat(result.description())
        .startsWith("Reacción resuelta; se completó a menor velocidad")
        .contains("Antecedentes de la administración:")
        .contains("Interrupción 1: Motivo: Hipotensión")
        .contains("Interrupción 2: Motivo: Reaparición de síntomas")
        .contains("Destino: Observación en Hospital de día")
        .contains("Destino: Derivación a guardia/emergencia");
  }

  @Test
  void evenAnIncompleteLegacyInterruptionRemainsAnIncidentAtClosure() {
    ObjectNode administration = mapper.createObjectNode();
    administration.withArray("interruptions").addObject();

    var result = ApplicationWorkflowService.resolveAdministrationReaction(
        administration, false, "");

    assertThat(result.occurred()).isTrue();
    assertThat(result.description())
        .isEqualTo("Interrupción 1: incidencia registrada durante la administración.");
  }
}
