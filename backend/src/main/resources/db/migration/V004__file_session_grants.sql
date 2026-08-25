ALTER TABLE clinical_files
  ADD COLUMN upload_session_hash char(64),
  ADD COLUMN deletable_until timestamptz;

CREATE INDEX idx_clinical_files_delete_grant
  ON clinical_files (upload_session_hash, deletable_until)
  WHERE upload_session_hash IS NOT NULL;

