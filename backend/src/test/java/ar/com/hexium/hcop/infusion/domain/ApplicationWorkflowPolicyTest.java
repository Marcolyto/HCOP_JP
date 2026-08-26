package ar.com.hexium.hcop.infusion.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApplicationWorkflowPolicyTest {

  @Test
  void patientToBringCanBeScheduledAfterPharmacyApproval() {
    assertOk(ApplicationWorkflowPolicy.schedule(
        "approved", "patient_to_bring", "none",
        "pending", "not_started", "not_started"));
  }

  @Test
  void centerStockCannotBeScheduledUntilItIsReallyReserved() {
    assertViolation(ApplicationWorkflowPolicy.schedule(
        "approved", "center_stock", "pending_verification",
        "pending", "not_started", "not_started"), "Reserve el stock");

    assertOk(ApplicationWorkflowPolicy.schedule(
        "approved", "center_stock", "reserved",
        "pending", "not_started", "not_started"));
  }

  @Test
  void pendingSupplierCannotBeScheduled() {
    assertViolation(ApplicationWorkflowPolicy.schedule(
        "approved", "pending_supplier", "none",
        "pending", "not_started", "not_started"), "cómo se obtendrá");
  }

  @Test
  void appointmentCannotMoveOrConfirmAfterClinicalWorkStarted() {
    assertViolation(ApplicationWorkflowPolicy.schedule(
        "approved", "center_stock", "reserved",
        "passed", "not_started", "not_started"), "etapa clínica");

    assertViolation(ApplicationWorkflowPolicy.schedule(
        "approved", "center_stock", "reserved",
        "pending", "released", "not_started"), "etapa clínica");

    assertViolation(ApplicationWorkflowPolicy.schedule(
        "approved", "center_stock", "reserved",
        "pending", "not_started", "in_progress"), "etapa clínica");
  }

  @Test
  void failedTriageCanBeRescheduledAfterMedicationIsSecuredAgain() {
    assertOk(ApplicationWorkflowPolicy.schedule(
        "approved", "center_stock", "reserved",
        "failed", "not_started", "not_started"));
  }

  @Test
  void triagePassWaitsUntilPatientActuallyHasOrDeliversMedication() {
    var mustBring = state(
        "scheduled", "confirmed", "patient_to_bring", "approved",
        "none", "pending", "not_started", "not_started");
    assertViolation(
        ApplicationWorkflowPolicy.clinicalAuthorization(mustBring, true, true),
        "medicación todavía no está asegurada");

    var patientHasIt = state(
        "scheduled", "confirmed", "patient_has_medication", "approved",
        "not_applicable", "pending", "not_started", "not_started");
    assertOk(ApplicationWorkflowPolicy.clinicalAuthorization(patientHasIt, true, true));
  }

  @Test
  void clinicalFailCanPostponeWithoutPretendingMedicationIsReady() {
    var waiting = state(
        "scheduled", "confirmed", "patient_to_bring", "approved",
        "none", "pending", "not_started", "not_started");
    assertOk(ApplicationWorkflowPolicy.clinicalAuthorization(waiting, false, true));
  }

  @Test
  void clinicalFailCanRevokeAPassEvenWhenTheOriginalAppointmentIsNoLongerToday() {
    var previouslyPassed = state(
        "clinically_authorized", "confirmed", "center_stock", "approved",
        "reserved", "passed", "not_started", "not_started");
    assertOk(ApplicationWorkflowPolicy.clinicalAuthorization(previouslyPassed, false, false));
  }

  @Test
  void reservationIsLimitedToApprovedCenterStock() {
    var patientSupply = state(
        "medication_pending", "confirmed", "patient_to_bring", "approved",
        "none", "pending", "not_started", "not_started");
    assertViolation(ApplicationWorkflowPolicy.reserveStock(patientSupply), "sólo corresponde");

    var centerSupply = state(
        "medication_pending", "confirmed", "center_stock", "approved",
        "none", "pending", "not_started", "not_started");
    assertOk(ApplicationWorkflowPolicy.reserveStock(centerSupply));
  }

  @Test
  void preparationRequiresPassAndSecuredMedication() {
    var passed = state(
        "clinically_authorized", "confirmed", "center_stock", "approved",
        "reserved", "passed", "not_started", "not_started");
    assertOk(ApplicationWorkflowPolicy.startPreparation(passed));

    var withoutPass = state(
        "scheduled", "confirmed", "center_stock", "approved",
        "reserved", "pending", "not_started", "not_started");
    assertViolation(ApplicationWorkflowPolicy.startPreparation(withoutPass), "PASS");
  }

  @Test
  void administrationRequiresReleasedPreparationAndActiveAppointment() {
    var released = state(
        "released", "confirmed", "center_stock", "approved",
        "consumed", "passed", "released", "not_started");
    assertOk(ApplicationWorkflowPolicy.startAdministration(released, true, true));
    assertViolation(
        ApplicationWorkflowPolicy.startAdministration(released, false, true), "turno confirmado");
  }

  @Test
  void administrationRejectsLegacyPreparationWithoutLotsAndTtl() {
    var legacyReleased = state(
        "released", "confirmed", "center_stock", "approved",
        "consumed", "passed", "released", "not_started");

    assertViolation(
        ApplicationWorkflowPolicy.startAdministration(legacyReleased, true, false),
        "lotes y vencimiento verificables");
  }

  @Test
  void completedApplicationsAreImmutable() {
    var completed = state(
        "completed", "confirmed", "center_stock", "approved",
        "consumed", "passed", "released", "completed");
    assertViolation(ApplicationWorkflowPolicy.clinicalAuthorization(completed, true, true), "inmutable");
  }

  @Test
  void onlyAnActiveAdministrationCanBeInterrupted() {
    var active = state(
        "in_administration", "confirmed", "center_stock", "approved",
        "consumed", "passed", "released", "in_progress");
    assertOk(ApplicationWorkflowPolicy.interruptAdministration(active));

    var waiting = state(
        "released", "confirmed", "center_stock", "approved",
        "consumed", "passed", "released", "not_started");
    assertViolation(ApplicationWorkflowPolicy.interruptAdministration(waiting), "en curso");
  }

  @Test
  void onlyAWithheldAdministrationCanBeResolved() {
    var interrupted = state(
        "in_administration", "confirmed", "center_stock", "approved",
        "consumed", "passed", "released", "withheld");
    assertOk(ApplicationWorkflowPolicy.resolveAdministration(interrupted, true, true));

    var active = state(
        "in_administration", "confirmed", "center_stock", "approved",
        "consumed", "passed", "released", "in_progress");
    assertViolation(
        ApplicationWorkflowPolicy.resolveAdministration(active, true, true), "pendiente de resolución");
  }

  @Test
  void interruptedAdministrationCannotResumeWithExpiredPreparation() {
    var interrupted = state(
        "in_administration", "confirmed", "center_stock", "approved",
        "consumed", "passed", "released", "withheld");

    assertViolation(
        ApplicationWorkflowPolicy.resolveAdministration(interrupted, true, false),
        "venció durante la interrupción");
    assertOk(ApplicationWorkflowPolicy.resolveAdministration(interrupted, false, false));
  }

  @Test
  void pharmacyAndTriageCannotRewindAStartedPreparation() {
    var preparing = state(
        "in_preparation", "confirmed", "center_stock", "approved",
        "reserved", "passed", "in_preparation", "not_started");

    assertViolation(ApplicationWorkflowPolicy.pharmacyValidation(preparing, true), "no puede retrocederse");
    assertViolation(
        ApplicationWorkflowPolicy.clinicalAuthorization(preparing, false, true), "no puede retrocederse");
  }

  @Test
  void pharmacyCannotChangeAfterClinicalPass() {
    var passed = state(
        "clinically_authorized", "confirmed", "center_stock", "approved",
        "reserved", "passed", "not_started", "not_started");

    assertViolation(ApplicationWorkflowPolicy.pharmacyValidation(passed, false), "triaje ya fue aprobado");
    assertViolation(
        ApplicationWorkflowPolicy.supplySource(passed, "received_center"), "triaje ya fue aprobado");
    assertViolation(ApplicationWorkflowPolicy.releaseStock(passed), "triaje ya fue aprobado");
  }

  @Test
  void preparationRequiresCurrentPharmacyApproval() {
    var rejected = state(
        "clinically_authorized", "confirmed", "received_center", "rejected",
        "not_applicable", "passed", "not_started", "not_started");

    assertViolation(
        ApplicationWorkflowPolicy.startPreparation(rejected), "farmacéutica ya no está validada");
    assertViolation(
        ApplicationWorkflowPolicy.completePreparation(rejected), "farmacéutica ya no está validada");
  }

  @Test
  void anyFinalizedUnadministeredPreparationCanRestartWithReasonValidatedByService() {
    var prepared = state(
        "prepared", "confirmed", "center_stock", "approved",
        "consumed", "passed", "prepared", "not_started");
    assertOk(ApplicationWorkflowPolicy.restartPreparation(prepared));

    var released = state(
        "released", "confirmed", "center_stock", "approved",
        "consumed", "passed", "released", "not_started");
    assertOk(ApplicationWorkflowPolicy.restartPreparation(released));

    var administering = state(
        "in_administration", "confirmed", "center_stock", "approved",
        "consumed", "passed", "released", "in_progress");
    assertViolation(
        ApplicationWorkflowPolicy.restartPreparation(administering), "administración comenzó");
  }

  @Test
  void preparationDiscardReasonRequiresTenCharactersAtTheApiBoundary() {
    assertViolation(
        ApplicationWorkflowPolicy.preparationRestartReason("corto"), "al menos 10 caracteres");
    assertOk(ApplicationWorkflowPolicy.preparationRestartReason("Contaminación detectada"));
  }

  @Test
  void admissionsCannotCancelAfterClinicalWorkStarted() {
    var waiting = state(
        "scheduled", "confirmed", "patient_to_bring", "approved",
        "none", "pending", "not_started", "not_started");
    assertOk(ApplicationWorkflowPolicy.cancelAppointment(waiting));

    var released = state(
        "released", "confirmed", "center_stock", "approved",
        "consumed", "passed", "released", "not_started");
    assertViolation(ApplicationWorkflowPolicy.cancelAppointment(released), "Admisión");
  }

  private void assertOk(Optional<ApplicationWorkflowPolicy.Violation> violation) {
    assertThat(violation).isEmpty();
  }

  private void assertViolation(Optional<ApplicationWorkflowPolicy.Violation> violation, String contains) {
    assertThat(violation).isPresent();
    assertThat(violation.get().message()).contains(contains);
  }

  private ApplicationWorkflowPolicy.State state(
      String workflow, String prescription, String source, String pharmacy, String stock,
      String clinical, String preparation, String administration) {
    return new ApplicationWorkflowPolicy.State(
        workflow, prescription, source, pharmacy, stock, clinical, preparation, administration);
  }
}
