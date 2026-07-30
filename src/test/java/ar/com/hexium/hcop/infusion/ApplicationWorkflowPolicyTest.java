package ar.com.hexium.hcop.infusion;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.hexium.hcop.common.ApiException;
import org.junit.jupiter.api.Test;

class ApplicationWorkflowPolicyTest {

  @Test
  void patientToBringCanBeScheduledAfterPharmacyApproval() {
    assertThatCode(() -> ApplicationWorkflowPolicy.schedule(
        "approved", "patient_to_bring", "none",
        "pending", "not_started", "not_started"))
        .doesNotThrowAnyException();
  }

  @Test
  void centerStockCannotBeScheduledUntilItIsReallyReserved() {
    assertThatThrownBy(() -> ApplicationWorkflowPolicy.schedule(
        "approved", "center_stock", "pending_verification",
        "pending", "not_started", "not_started"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Reserve el stock");

    assertThatCode(() -> ApplicationWorkflowPolicy.schedule(
        "approved", "center_stock", "reserved",
        "pending", "not_started", "not_started"))
        .doesNotThrowAnyException();
  }

  @Test
  void pendingSupplierCannotBeScheduled() {
    assertThatThrownBy(() -> ApplicationWorkflowPolicy.schedule(
        "approved", "pending_supplier", "none",
        "pending", "not_started", "not_started"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("cómo se obtendrá");
  }

  @Test
  void appointmentCannotMoveOrConfirmAfterClinicalWorkStarted() {
    assertThatThrownBy(() -> ApplicationWorkflowPolicy.schedule(
        "approved", "center_stock", "reserved",
        "passed", "not_started", "not_started"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("etapa clínica");

    assertThatThrownBy(() -> ApplicationWorkflowPolicy.schedule(
        "approved", "center_stock", "reserved",
        "pending", "released", "not_started"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("etapa clínica");

    assertThatThrownBy(() -> ApplicationWorkflowPolicy.schedule(
        "approved", "center_stock", "reserved",
        "pending", "not_started", "in_progress"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("etapa clínica");
  }

  @Test
  void failedTriageCanBeRescheduledAfterMedicationIsSecuredAgain() {
    assertThatCode(() -> ApplicationWorkflowPolicy.schedule(
        "approved", "center_stock", "reserved",
        "failed", "not_started", "not_started"))
        .doesNotThrowAnyException();
  }

  @Test
  void triagePassWaitsUntilPatientActuallyHasOrDeliversMedication() {
    var mustBring = state(
        "scheduled", "confirmed", "patient_to_bring", "approved",
        "none", "pending", "not_started", "not_started");
    assertThatThrownBy(() ->
        ApplicationWorkflowPolicy.clinicalAuthorization(mustBring, true, true))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("medicación todavía no está asegurada");

    var patientHasIt = state(
        "scheduled", "confirmed", "patient_has_medication", "approved",
        "not_applicable", "pending", "not_started", "not_started");
    assertThatCode(() ->
        ApplicationWorkflowPolicy.clinicalAuthorization(patientHasIt, true, true))
        .doesNotThrowAnyException();
  }

  @Test
  void clinicalFailCanPostponeWithoutPretendingMedicationIsReady() {
    var waiting = state(
        "scheduled", "confirmed", "patient_to_bring", "approved",
        "none", "pending", "not_started", "not_started");
    assertThatCode(() ->
        ApplicationWorkflowPolicy.clinicalAuthorization(waiting, false, true))
        .doesNotThrowAnyException();
  }

  @Test
  void clinicalFailCanRevokeAPassEvenWhenTheOriginalAppointmentIsNoLongerToday() {
    var previouslyPassed = state(
        "clinically_authorized", "confirmed", "center_stock", "approved",
        "reserved", "passed", "not_started", "not_started");
    assertThatCode(() ->
        ApplicationWorkflowPolicy.clinicalAuthorization(previouslyPassed, false, false))
        .doesNotThrowAnyException();
  }

  @Test
  void reservationIsLimitedToApprovedCenterStock() {
    var patientSupply = state(
        "medication_pending", "confirmed", "patient_to_bring", "approved",
        "none", "pending", "not_started", "not_started");
    assertThatThrownBy(() -> ApplicationWorkflowPolicy.reserveStock(patientSupply))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("sólo corresponde");

    var centerSupply = state(
        "medication_pending", "confirmed", "center_stock", "approved",
        "none", "pending", "not_started", "not_started");
    assertThatCode(() -> ApplicationWorkflowPolicy.reserveStock(centerSupply))
        .doesNotThrowAnyException();
  }

  @Test
  void preparationRequiresPassAndSecuredMedication() {
    var passed = state(
        "clinically_authorized", "confirmed", "center_stock", "approved",
        "reserved", "passed", "not_started", "not_started");
    assertThatCode(() -> ApplicationWorkflowPolicy.startPreparation(passed))
        .doesNotThrowAnyException();

    var withoutPass = state(
        "scheduled", "confirmed", "center_stock", "approved",
        "reserved", "pending", "not_started", "not_started");
    assertThatThrownBy(() -> ApplicationWorkflowPolicy.startPreparation(withoutPass))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("PASS");
  }

  @Test
  void administrationRequiresReleasedPreparationAndActiveAppointment() {
    var released = state(
        "released", "confirmed", "center_stock", "approved",
        "consumed", "passed", "released", "not_started");
    assertThatCode(() ->
        ApplicationWorkflowPolicy.startAdministration(released, true, true))
        .doesNotThrowAnyException();
    assertThatThrownBy(() ->
        ApplicationWorkflowPolicy.startAdministration(released, false, true))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("turno confirmado");
  }

  @Test
  void administrationRejectsLegacyPreparationWithoutLotsAndTtl() {
    var legacyReleased = state(
        "released", "confirmed", "center_stock", "approved",
        "consumed", "passed", "released", "not_started");

    assertThatThrownBy(() ->
        ApplicationWorkflowPolicy.startAdministration(legacyReleased, true, false))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("lotes y vencimiento verificables");
  }

  @Test
  void completedApplicationsAreImmutable() {
    var completed = state(
        "completed", "confirmed", "center_stock", "approved",
        "consumed", "passed", "released", "completed");
    assertThatThrownBy(() ->
        ApplicationWorkflowPolicy.clinicalAuthorization(completed, true, true))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("inmutable");
  }

  @Test
  void onlyAnActiveAdministrationCanBeInterrupted() {
    var active = state(
        "in_administration", "confirmed", "center_stock", "approved",
        "consumed", "passed", "released", "in_progress");
    assertThatCode(() -> ApplicationWorkflowPolicy.interruptAdministration(active))
        .doesNotThrowAnyException();

    var waiting = state(
        "released", "confirmed", "center_stock", "approved",
        "consumed", "passed", "released", "not_started");
    assertThatThrownBy(() -> ApplicationWorkflowPolicy.interruptAdministration(waiting))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("en curso");
  }

  @Test
  void onlyAWithheldAdministrationCanBeResolved() {
    var interrupted = state(
        "in_administration", "confirmed", "center_stock", "approved",
        "consumed", "passed", "released", "withheld");
    assertThatCode(() ->
        ApplicationWorkflowPolicy.resolveAdministration(interrupted, true, true))
        .doesNotThrowAnyException();

    var active = state(
        "in_administration", "confirmed", "center_stock", "approved",
        "consumed", "passed", "released", "in_progress");
    assertThatThrownBy(() ->
        ApplicationWorkflowPolicy.resolveAdministration(active, true, true))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("pendiente de resolución");
  }

  @Test
  void interruptedAdministrationCannotResumeWithExpiredPreparation() {
    var interrupted = state(
        "in_administration", "confirmed", "center_stock", "approved",
        "consumed", "passed", "released", "withheld");

    assertThatThrownBy(() ->
        ApplicationWorkflowPolicy.resolveAdministration(interrupted, true, false))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("venció durante la interrupción");
    assertThatCode(() ->
        ApplicationWorkflowPolicy.resolveAdministration(interrupted, false, false))
        .doesNotThrowAnyException();
  }

  @Test
  void pharmacyAndTriageCannotRewindAStartedPreparation() {
    var preparing = state(
        "in_preparation", "confirmed", "center_stock", "approved",
        "reserved", "passed", "in_preparation", "not_started");

    assertThatThrownBy(() ->
        ApplicationWorkflowPolicy.pharmacyValidation(preparing, true))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("no puede retrocederse");
    assertThatThrownBy(() ->
        ApplicationWorkflowPolicy.clinicalAuthorization(preparing, false, true))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("no puede retrocederse");
  }

  @Test
  void pharmacyCannotChangeAfterClinicalPass() {
    var passed = state(
        "clinically_authorized", "confirmed", "center_stock", "approved",
        "reserved", "passed", "not_started", "not_started");

    assertThatThrownBy(() ->
        ApplicationWorkflowPolicy.pharmacyValidation(passed, false))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("triaje ya fue aprobado");
    assertThatThrownBy(() ->
        ApplicationWorkflowPolicy.supplySource(passed, "received_center"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("triaje ya fue aprobado");
    assertThatThrownBy(() ->
        ApplicationWorkflowPolicy.releaseStock(passed))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("triaje ya fue aprobado");
  }

  @Test
  void preparationRequiresCurrentPharmacyApproval() {
    var rejected = state(
        "clinically_authorized", "confirmed", "received_center", "rejected",
        "not_applicable", "passed", "not_started", "not_started");

    assertThatThrownBy(() -> ApplicationWorkflowPolicy.startPreparation(rejected))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("farmacéutica ya no está validada");
    assertThatThrownBy(() -> ApplicationWorkflowPolicy.completePreparation(rejected))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("farmacéutica ya no está validada");
  }

  @Test
  void anyFinalizedUnadministeredPreparationCanRestartWithReasonValidatedByService() {
    var prepared = state(
        "prepared", "confirmed", "center_stock", "approved",
        "consumed", "passed", "prepared", "not_started");
    assertThatCode(() ->
        ApplicationWorkflowPolicy.restartPreparation(prepared))
        .doesNotThrowAnyException();

    var released = state(
        "released", "confirmed", "center_stock", "approved",
        "consumed", "passed", "released", "not_started");
    assertThatCode(() ->
        ApplicationWorkflowPolicy.restartPreparation(released))
        .doesNotThrowAnyException();

    var administering = state(
        "in_administration", "confirmed", "center_stock", "approved",
        "consumed", "passed", "released", "in_progress");
    assertThatThrownBy(() ->
        ApplicationWorkflowPolicy.restartPreparation(administering))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("administración comenzó");
  }

  @Test
  void preparationDiscardReasonRequiresTenCharactersAtTheApiBoundary() {
    assertThatThrownBy(() ->
        ApplicationWorkflowPolicy.preparationRestartReason("corto"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("al menos 10 caracteres");
    assertThatCode(() ->
        ApplicationWorkflowPolicy.preparationRestartReason("Contaminación detectada"))
        .doesNotThrowAnyException();
  }

  @Test
  void admissionsCannotCancelAfterClinicalWorkStarted() {
    var waiting = state(
        "scheduled", "confirmed", "patient_to_bring", "approved",
        "none", "pending", "not_started", "not_started");
    assertThatCode(() -> ApplicationWorkflowPolicy.cancelAppointment(waiting))
        .doesNotThrowAnyException();

    var released = state(
        "released", "confirmed", "center_stock", "approved",
        "consumed", "passed", "released", "not_started");
    assertThatThrownBy(() -> ApplicationWorkflowPolicy.cancelAppointment(released))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Admisión");
  }

  private ApplicationWorkflowPolicy.State state(
      String workflow,
      String prescription,
      String source,
      String pharmacy,
      String stock,
      String clinical,
      String preparation,
      String administration) {
    return new ApplicationWorkflowPolicy.State(
        workflow, prescription, source, pharmacy, stock,
        clinical, preparation, administration);
  }
}
