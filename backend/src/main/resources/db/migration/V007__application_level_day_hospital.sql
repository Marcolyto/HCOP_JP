CREATE TABLE treatment_application_logistics (
  patient_id bigint NOT NULL REFERENCES patients(source_id) ON DELETE RESTRICT,
  treatment_id varchar(255) NOT NULL REFERENCES clinical_treatments(id) ON DELETE RESTRICT,
  cycle_number smallint NOT NULL CHECK (cycle_number BETWEEN 1 AND 500),
  application_day smallint NOT NULL CHECK (application_day BETWEEN 1 AND 3650),
  planned_date date,
  medication_state varchar(32) NOT NULL DEFAULT 'pending'
    CHECK (medication_state IN ('pending','received','with_patient')),
  prescription_state varchar(32)
    CHECK (prescription_state IN ('confirmed','required','requested','rejected')),
  duration_minutes smallint NOT NULL CHECK (duration_minutes BETWEEN 1 AND 1440),
  duration_source varchar(64) NOT NULL DEFAULT 'protocol-day-estimate',
  drug_summary varchar(2000),
  application_drugs jsonb NOT NULL DEFAULT '[]'::jsonb,
  notes varchar(2000),
  revision bigint NOT NULL DEFAULT 1 CHECK (revision > 0),
  updated_by bigint NOT NULL REFERENCES local_users(id),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  PRIMARY KEY (patient_id, treatment_id, cycle_number, application_day)
);

CREATE INDEX idx_treatment_application_waiting
  ON treatment_application_logistics (planned_date, patient_id, treatment_id, cycle_number, application_day);

ALTER TABLE unified_infusion_sessions
  ADD COLUMN application_day smallint;

UPDATE unified_infusion_sessions
   SET application_day = CASE
     WHEN (source_ref #>> '{scheduler,applicationDay}') ~ '^[0-9]+$'
       THEN LEAST(3650, GREATEST(1, (source_ref #>> '{scheduler,applicationDay}')::integer))
     WHEN (source_ref ->> 'applicationDay') ~ '^[0-9]+$'
       THEN LEAST(3650, GREATEST(1, (source_ref ->> 'applicationDay')::integer))
     ELSE 1
   END;

ALTER TABLE unified_infusion_sessions
  ALTER COLUMN application_day SET DEFAULT 1,
  ALTER COLUMN application_day SET NOT NULL,
  ADD CONSTRAINT chk_unified_infusion_application_day
    CHECK (application_day BETWEEN 1 AND 3650);

DROP INDEX IF EXISTS uq_unified_infusion_active_slot;

CREATE UNIQUE INDEX uq_unified_infusion_active_application
  ON unified_infusion_sessions (patient_id, treatment_id, cycle_number, application_day)
  WHERE clinical_status <> 'cancelled';

CREATE INDEX idx_unified_infusion_treatment_application
  ON unified_infusion_sessions (treatment_id, cycle_number, application_day);

ALTER TABLE clinical_qr_scan_events
  ADD COLUMN application_day smallint NOT NULL DEFAULT 1
    CHECK (application_day BETWEEN 1 AND 3650);

CREATE INDEX idx_clinical_qr_scan_application
  ON clinical_qr_scan_events
    (patient_id, treatment_id, cycle_number, application_day, scanned_at DESC);
