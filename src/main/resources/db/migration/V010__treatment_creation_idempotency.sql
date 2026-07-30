CREATE UNIQUE INDEX IF NOT EXISTS uq_clinical_treatments_patient_entry
  ON clinical_treatments (patient_id, (payload ->> 'clinicalEntryId'))
  WHERE COALESCE(payload ->> 'clinicalEntryId', '') <> '';
