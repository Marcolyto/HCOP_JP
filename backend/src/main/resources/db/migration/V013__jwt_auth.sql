-- F2 (JWT real). Aditiva: local_sessions (modo cookie) convive hasta que el modo dual se cierre
-- (V014 recién ahí hace el DROP). local_session_state es la traducción honesta de local_sessions
-- sin token_hash — el "sid" (claim del JWT, generado en el login, preservado en cada refresh) es
-- su clave, no un token opaco. La revocación inmediata (F2.7) lee esta tabla por PK.
CREATE TABLE local_session_state (
  sid uuid PRIMARY KEY,
  user_id bigint NOT NULL REFERENCES local_users(id) ON DELETE CASCADE,
  active_patient_id bigint REFERENCES patients(source_id) ON DELETE SET NULL,
  revoked boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX idx_local_session_state_user ON local_session_state (user_id);

-- Un refresh token por fila (rotación: cada POST /api/auth/refresh emite un jti nuevo y revoca
-- el anterior). expires_at gobernado por local_security_settings.session_duration_minutes
-- (re-cableado como TTL del refresh, no del access token — ver docs de decisiones F2).
CREATE TABLE local_refresh_tokens (
  jti uuid PRIMARY KEY,
  sid uuid NOT NULL REFERENCES local_session_state(sid) ON DELETE CASCADE,
  user_id bigint NOT NULL REFERENCES local_users(id) ON DELETE CASCADE,
  expires_at timestamptz NOT NULL,
  revoked boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  client_address inet,
  user_agent varchar(1000)
);
CREATE INDEX idx_local_refresh_tokens_sid ON local_refresh_tokens (sid);
CREATE INDEX idx_local_refresh_tokens_user ON local_refresh_tokens (user_id);
CREATE INDEX idx_local_refresh_tokens_expiry ON local_refresh_tokens (expires_at);

-- ClinicalFileService.storeImage (F2.4) cambia sha256(rawSessionToken) por sid: la columna vieja
-- queda nullable para filas históricas del modo cookie, nunca se borra retroactivamente.
ALTER TABLE clinical_files ADD COLUMN upload_session_id uuid;
