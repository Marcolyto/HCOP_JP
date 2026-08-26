-- F2.8: modo cookie/opaco eliminado del código (AuthService/AuthController/AuthInterceptor solo
-- hablan JWT desde este commit) — local_sessions ya no tiene lector ni escritor. local_session_state
-- (V013) es su reemplazo desde F2.5.
DROP TABLE local_sessions;
