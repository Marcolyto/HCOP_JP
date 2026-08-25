CREATE UNIQUE INDEX uq_patients_identity_seed_key
  ON patients ((identity_json ->> 'seedKey'))
  WHERE identity_json ? 'seedKey'
    AND NULLIF(btrim(identity_json ->> 'seedKey'), '') IS NOT NULL;
