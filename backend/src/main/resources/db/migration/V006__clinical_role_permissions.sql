INSERT INTO local_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM local_roles r
JOIN local_permissions p ON p.permission_key = ANY (
  CASE r.role_key
    WHEN 'oncologist' THEN ARRAY[
      'section.history.view','section.history.edit','section.studies.view','section.studies.edit',
      'section.day-hospital.view','section.prescriptions.view','section.prescriptions.edit',
      'section.agent.view','section.research.view','section.research.edit','section.timeline.view',
      'section.protocols.view','section.tools.view','section.tools.use','section.configuration.view',
      'workflow.suspend','workflow.resume','workflow.request-prescription','workflow.request-continuity',
      'workflow.resolve-prescription','workflow.resolve-continuity'
    ]
    WHEN 'nursing' THEN ARRAY[
      'section.history.view','section.history.edit','section.studies.view','section.studies.edit',
      'section.day-hospital.view','section.day-hospital.edit','section.prescriptions.view',
      'section.timeline.view','section.protocols.view','section.tools.view','section.tools.use',
      'workflow.suspend','workflow.request-prescription','workflow.request-continuity'
    ]
    WHEN 'pharmacy' THEN ARRAY[
      'section.history.view','section.studies.view','section.day-hospital.view',
      'section.day-hospital.edit','section.prescriptions.view','section.protocols.view',
      'workflow.request-prescription'
    ]
    WHEN 'admissions' THEN ARRAY[
      'section.history.view','section.studies.view','section.day-hospital.view',
      'section.day-hospital.edit','section.prescriptions.view','section.timeline.view',
      'workflow.request-prescription','workflow.request-continuity'
    ]
    ELSE ARRAY[]::text[]
  END
)
WHERE r.role_key IN ('oncologist','nursing','pharmacy','admissions')
ON CONFLICT DO NOTHING;
