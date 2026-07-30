-- Safety hardening for the application-level Hospital de Día workflow.
--
-- V008 imported pre-existing appointments. Their existence is not evidence that a
-- pharmacist reviewed the order, so every non-final legacy approval without actor,
-- timestamp or audit event must return to an explicit pending review.

UPDATE treatment_application_workflows workflow
   SET pharmacy_validation_status = 'pending',
       pharmacy_validation_notes = concat_ws(
         E'\n',
         NULLIF(workflow.pharmacy_validation_notes, ''),
         'Migrado sin validación farmacéutica trazable; requiere revisión.'
       ),
       workflow_status = CASE
         WHEN EXISTS (
           SELECT 1
             FROM unified_infusion_sessions session
            WHERE session.patient_id = workflow.patient_id
              AND session.treatment_id = workflow.treatment_id
              AND session.cycle_number = workflow.cycle_number
              AND session.application_day = workflow.application_day
              AND session.clinical_status <> 'cancelled'
         ) THEN 'scheduled'
         ELSE 'prescribed'
       END,
       revision = revision + 1,
       updated_at = clock_timestamp()
 WHERE workflow.pharmacy_validation_status = 'approved'
   AND workflow.pharmacy_validated_by IS NULL
   AND workflow.pharmacy_validated_at IS NULL
   AND workflow.administration_status <> 'completed'
   AND workflow.preparation_status IN ('not_started', 'cancelled')
   AND workflow.clinical_authorization_status IN ('pending', 'failed')
   AND NOT EXISTS (
     SELECT 1
       FROM treatment_application_workflow_events event
      WHERE event.patient_id = workflow.patient_id
        AND event.treatment_id = workflow.treatment_id
        AND event.cycle_number = workflow.cycle_number
        AND event.application_day = workflow.application_day
        AND event.action IN (
          'pharmacy_validation_approved',
          'pharmacy_validation_rejected'
        )
   );
