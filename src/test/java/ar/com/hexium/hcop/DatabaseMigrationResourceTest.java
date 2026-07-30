package ar.com.hexium.hcop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class DatabaseMigrationResourceTest {

    @Test
    void packagesEveryFlywayMigrationRequiredByAnEmptyInstallation() throws Exception {
        Resource[] migrations = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/migration/V*.sql");

        assertThat(Arrays.stream(migrations).map(Resource::getFilename))
                .containsExactlyInAnyOrder(
                        "V001__core_schema.sql",
                        "V002__rbac_seed.sql",
                        "V003__scheduler_overlap_guard.sql",
                        "V004__file_session_grants.sql",
                        "V005__qr_workflow.sql",
                        "V006__clinical_role_permissions.sql",
                        "V007__application_level_day_hospital.sql",
                        "V008__application_workflow.sql",
                        "V009__workflow_safety_and_legacy_trace.sql",
                        "V010__treatment_creation_idempotency.sql",
                        "V011__preparation_component_trace.sql");
    }

    @Test
    void treatmentCreationHasADatabaseLevelIdempotencyGuard() throws Exception {
        String sql = new ClassPathResource(
                "db/migration/V010__treatment_creation_idempotency.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("CREATE UNIQUE INDEX")
                .contains("patient_id")
                .contains("payload ->> 'clinicalEntryId'");
    }

    @Test
    void preparationTraceKeepsCanonicalComponentIdentityAndLegacyRowsReadable()
            throws Exception {
        String sql = new ClassPathResource(
                "db/migration/V011__preparation_component_trace.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("ADD COLUMN component_key")
                .contains("reservation.component_key")
                .contains("component_key IS NOT NULL")
                .contains("Puede ser NULL únicamente en trazas legacy");
    }

    @Test
    void untraceableLegacyPharmacyApprovalsReturnToPendingReview() throws Exception {
        String sql = new ClassPathResource(
                "db/migration/V009__workflow_safety_and_legacy_trace.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("pharmacy_validation_status = 'pending'")
                .contains("pharmacy_validated_by IS NULL")
                .contains("pharmacy_validated_at IS NULL")
                .contains("Migrado sin validación farmacéutica trazable")
                .contains("treatment_application_workflow_events");
    }

    @Test
    void applicationWorkflowMigrationContainsSafetyAndAuditStructures() throws Exception {
        String sql = new ClassPathResource(
                "db/migration/V008__application_workflow.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("CREATE TABLE treatment_application_workflows")
                .contains("CREATE TABLE application_stock_reservations")
                .contains("CREATE TABLE pharmacy_inventory_lots")
                .contains("quantity_reserved <= quantity_on_hand")
                .contains("CREATE TABLE application_preparation_lots")
                .contains("CREATE TABLE treatment_application_workflow_events")
                .contains("uq_application_workflow_idempotency")
                .contains("application.pharmacy.manage")
                .contains("application.schedule.manage")
                .contains("application.triage.manage")
                .contains("application.preparation.manage")
                .contains("application.administration.manage");
    }

    @Test
    void legacyReadyOrReleasedApplicationsMustRebuildPreparationTraceability()
            throws Exception {
        String sql = new ClassPathResource(
                "db/migration/V008__application_workflow.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        String backfill = sql.substring(sql.indexOf(
                "INSERT INTO treatment_application_workflows"));

        assertThat(backfill)
                .contains("Legacy ready/released sessions have no preparation lots or TTL")
                .contains("WHEN session.pharmacy_status IN ('ready','released')")
                .contains("WHEN 'ready' THEN CASE")
                .contains("WHEN 'released' THEN CASE")
                .contains("ELSE 'not_started'")
                .doesNotContain("WHEN 'ready' THEN 'prepared'")
                .doesNotContain("WHEN 'released' THEN 'released'");
    }
}
