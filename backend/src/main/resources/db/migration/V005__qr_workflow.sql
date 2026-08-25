CREATE TABLE clinical_qr_scan_events (
  id uuid PRIMARY KEY,
  operation_id varchar(128) NOT NULL UNIQUE,
  code_sha256 char(64) NOT NULL,
  patient_id bigint NOT NULL REFERENCES patients(source_id) ON DELETE RESTRICT,
  treatment_id varchar(255) NOT NULL REFERENCES clinical_treatments(id) ON DELETE RESTRICT,
  cycle_number smallint NOT NULL CHECK (cycle_number BETWEEN 1 AND 500),
  infusion_session_id bigint NOT NULL REFERENCES unified_infusion_sessions(id) ON DELETE RESTRICT,
  actor_user_id bigint NOT NULL REFERENCES local_users(id) ON DELETE RESTRICT,
  scanned_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_clinical_qr_scan_treatment
  ON clinical_qr_scan_events (patient_id, treatment_id, cycle_number, scanned_at DESC);
