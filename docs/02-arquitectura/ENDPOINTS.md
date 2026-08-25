# Catálogo completo de endpoints

> Archivo generado desde el OpenAPI real de HCOP JP. No editar a mano.

- Especificación: `GET /v3/api-docs/hcop-jp-completa`
- Swagger UI: `GET /swagger-ui.html`
- Versión declarada: `1.0.0`
- Operaciones documentadas: **113**
- Autenticación: cookie HttpOnly `HCOP_SESSION`; las operaciones públicas se identifican expresamente.

Los permisos se validan en el servidor. `authenticated` significa que la ruta exige una sesión activa pero no aplica un permiso granular adicional en el controlador.

## Autenticación

### `PUT /api/auth/active-patient` - Cambiar paciente activo

Asocia o limpia el paciente activo únicamente para la sesión actual.

- **Controlador MVC:** `AuthController`
- **Operación Java/OpenAPI:** `activePatient`
- **Acceso requerido:** `authenticated`
- **Parámetros:** Ninguno.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/auth/login` - Iniciar sesión

Valida usuario y contraseña y crea una cookie HttpOnly SameSite=Strict.

- **Controlador MVC:** `AuthController`
- **Operación Java/OpenAPI:** `login`
- **Acceso requerido:** `public`
- **Parámetros:** Ninguno.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Usuario o contraseña incorrectos.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/auth/logout` - Cerrar sesión

Revoca la sesión actual y elimina su cookie.

- **Controlador MVC:** `AuthController`
- **Operación Java/OpenAPI:** `logout`
- **Acceso requerido:** `authenticated`
- **Parámetros:** Ninguno.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/auth/me` - Consultar sesión

Devuelve el usuario, roles, permisos y paciente activo; no expone el token.

- **Controlador MVC:** `AuthController`
- **Operación Java/OpenAPI:** `me`
- **Acceso requerido:** `public`
- **Parámetros:** Ninguno.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `500` Error interno sin exposición de detalles sensibles.

### `PUT /api/auth/password` - Cambiar contraseña

Cambia la contraseña y revoca las otras sesiones del usuario.

- **Controlador MVC:** `AuthController`
- **Operación Java/OpenAPI:** `password`
- **Acceso requerido:** `authenticated`
- **Parámetros:** Ninguno.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

## Pacientes e historia

### `GET /api/clinical/patients` - Buscar pacientes

Sin consulta devuelve los pacientes recientes; con texto filtra por nombre, apellido, DNI, historia clínica o identificador local.

- **Controlador MVC:** `PatientController`
- **Operación Java/OpenAPI:** `search`
- **Acceso requerido:** `section.history.view`
- **Parámetros:** `q` (query, opcional): Texto de búsqueda; admite coincidencia parcial.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/clinical/patients` - Crear paciente

Crea un paciente local, su hoja clínica en blanco y lo deja activo.

- **Controlador MVC:** `PatientController`
- **Operación Java/OpenAPI:** `create_3`
- **Acceso requerido:** `section.history.edit`
- **Parámetros:** Ninguno.
- **Cuerpo:** `application/json`
- **Respuestas:** `201` Recurso creado correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/clinical/patients/{patientId}/activate` - Activar paciente y abrir espacio clínico

Asocia el paciente a la sesión y devuelve identidad, historia, tratamientos, turnos y conteos, omitiendo secciones sin permiso.

- **Controlador MVC:** `PatientWorkspaceController`
- **Operación Java/OpenAPI:** `activate`
- **Acceso requerido:** `section.history.view`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/clinical/patients/{patientId}/diagnosis` - Listar diagnósticos

Lista todos los diagnósticos oncológicos no archivados del paciente.

- **Controlador MVC:** `DiagnosisController`
- **Operación Java/OpenAPI:** `list`
- **Acceso requerido:** `section.history.view`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `PUT /api/clinical/patients/{patientId}/diagnosis` - Validar diagnóstico de tratamiento

Confirma que el diagnóstico seleccionado pertenece a la historia del paciente.

- **Controlador MVC:** `DiagnosisController`
- **Operación Java/OpenAPI:** `link`
- **Acceso requerido:** `section.history.edit`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `422` Los datos son válidos sintácticamente pero no cumplen una regla clínica.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/clinical/patients/{patientId}/workspace` - Abrir espacio clínico del paciente

Devuelve el agregado de trabajo del paciente sin cambiar otra sesión y aplica la misma proyección por permisos.

- **Controlador MVC:** `PatientWorkspaceController`
- **Operación Java/OpenAPI:** `workspace`
- **Acceso requerido:** `section.history.view`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/diagnosis-catalogs/search` - Buscar SNOMED CT o CIE-10

Filtra el catálogo diagnóstico local por sistema, texto y límite de resultados.

- **Controlador MVC:** `DiagnosisCatalogController`
- **Operación Java/OpenAPI:** `search_1`
- **Acceso requerido:** `authenticated`
- **Parámetros:** `system` (query, obligatorio): Sistema terminológico: snomed o cie10.; `q` (query, obligatorio): Texto de búsqueda; admite coincidencia parcial.; `limit` (query, opcional): Cantidad máxima de resultados devueltos.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/hc` - Leer historia clínica

Recupera la hoja del paciente activo o la plantilla en blanco; requiere acceso a Historia, omite prescriptions sin permiso de lectura de Prescripción y omite studies/externalStudies sin permiso de lectura de Estudios.

- **Controlador MVC:** `ClinicalDocumentController`
- **Operación Java/OpenAPI:** `get`
- **Acceso requerido:** `section.history.view`
- **Parámetros:** Ninguno.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `PUT /api/hc` - Guardar historia clínica

Guarda con control optimista de revisión y devuelve el estado canónico confirmado. Si prescriptions cambia exige edición de Prescripción; si studies o externalStudies cambian exige edición de Estudios; si esos campos fueron ocultados, conserva sus valores existentes. Los cambios de narrative.chiefComplaint, narrative.currentIllness, narrative.backgroundClinical, narrative.currentMedication, narrative.familyOncology, narrative.gynecology, narrative.physicalExam, narrative.summary y narrative.plan deben ser texto de hasta 50.000 caracteres; exam.weightKg acepta 0.01 a 500 kg y exam.heightM conserva 0.3 a 2.5 metros aunque la UI muestre centímetros; valores legacy atípicos que no cambian se preservan. Java genera actor, fecha, versión y auditoría de Motivo de consulta, Antecedentes de enfermedad actual, Antecedentes personales, Examen físico y Conclusión / resumen usando la sesión y no confía en esos metadatos enviados por el cliente. La validación 400 de Motivo de consulta usa CLINICAL_CHIEF_COMPLAINT_INVALID, CLINICAL_CHIEF_COMPLAINT_TOO_LONG, CLINICAL_CHIEF_COMPLAINT_EMPTY, CLINICAL_CHIEF_COMPLAINT_REASON_REQUIRED, CLINICAL_CHIEF_COMPLAINT_REASON_INVALID o CLINICAL_CHIEF_COMPLAINT_REASON_TOO_LONG. Antecedentes de enfermedad actual usa CLINICAL_CURRENT_ILLNESS_INVALID, CLINICAL_CURRENT_ILLNESS_TOO_LONG, CLINICAL_CURRENT_ILLNESS_EMPTY, CLINICAL_CURRENT_ILLNESS_REASON_REQUIRED, CLINICAL_CURRENT_ILLNESS_REASON_INVALID o CLINICAL_CURRENT_ILLNESS_REASON_TOO_LONG. Antecedentes personales usa CLINICAL_PERSONAL_HISTORY_BACKGROUND_CLINICAL_INVALID, CLINICAL_PERSONAL_HISTORY_BACKGROUND_CLINICAL_TOO_LONG, CLINICAL_PERSONAL_HISTORY_CURRENT_MEDICATION_INVALID, CLINICAL_PERSONAL_HISTORY_CURRENT_MEDICATION_TOO_LONG, CLINICAL_PERSONAL_HISTORY_FAMILY_ONCOLOGY_INVALID, CLINICAL_PERSONAL_HISTORY_FAMILY_ONCOLOGY_TOO_LONG, CLINICAL_PERSONAL_HISTORY_GYNECOLOGY_INVALID, CLINICAL_PERSONAL_HISTORY_GYNECOLOGY_TOO_LONG, CLINICAL_PERSONAL_HISTORY_EMPTY, CLINICAL_PERSONAL_HISTORY_REASON_REQUIRED, CLINICAL_PERSONAL_HISTORY_REASON_INVALID o CLINICAL_PERSONAL_HISTORY_REASON_TOO_LONG. Examen físico usa CLINICAL_PHYSICAL_EXAM_WEIGHT_INVALID, CLINICAL_PHYSICAL_EXAM_WEIGHT_OUT_OF_RANGE, CLINICAL_PHYSICAL_EXAM_HEIGHT_INVALID, CLINICAL_PHYSICAL_EXAM_HEIGHT_OUT_OF_RANGE, CLINICAL_PHYSICAL_EXAM_TEXT_INVALID, CLINICAL_PHYSICAL_EXAM_TEXT_TOO_LONG, CLINICAL_PHYSICAL_EXAM_EMPTY, CLINICAL_PHYSICAL_EXAM_REASON_REQUIRED, CLINICAL_PHYSICAL_EXAM_REASON_INVALID o CLINICAL_PHYSICAL_EXAM_REASON_TOO_LONG. La validación de Conclusión / resumen usa CLINICAL_SUMMARY_INVALID, CLINICAL_SUMMARY_TOO_LONG, CLINICAL_PLAN_INVALID, CLINICAL_PLAN_TOO_LONG, CLINICAL_SUMMARY_PLAN_EMPTY, CLINICAL_SUMMARY_PLAN_REASON_REQUIRED, CLINICAL_SUMMARY_PLAN_REASON_INVALID o CLINICAL_SUMMARY_PLAN_REASON_TOO_LONG. Los conflictos 409 usan ACTIVE_PATIENT_REQUIRED, CLINICAL_REVISION_REQUIRED, CLINICAL_PATIENT_MISMATCH o VERSION_CONFLICT.

- **Controlador MVC:** `ClinicalDocumentController`
- **Operación Java/OpenAPI:** `put`
- **Acceso requerido:** `section.history.edit + permiso específico de edición si prescriptions, studies o externalStudies cambian`
- **Parámetros:** Ninguno.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/hc/restore-demo-on-reload` - Compatibilidad de persistencia

Confirma que la historia es persistente y que no se restaura un demo.

- **Controlador MVC:** `ClinicalDocumentController`
- **Operación Java/OpenAPI:** `restoreDemo`
- **Acceso requerido:** `authenticated`
- **Parámetros:** Ninguno.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/lira/patients` - Buscar pacientes

Sin consulta devuelve los pacientes recientes; con texto filtra por nombre, apellido, DNI, historia clínica o identificador local.

- **Controlador MVC:** `PatientController`
- **Operación Java/OpenAPI:** `search_2`
- **Acceso requerido:** `section.history.view`
- **Parámetros:** `q` (query, opcional): Texto de búsqueda; admite coincidencia parcial.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/lira/patients/{patientId}/import` - Abrir paciente local

Activa una historia ya consolidada en PostgreSQL; no consulta Lira y proyecta las secciones según permisos.

- **Controlador MVC:** `PatientController`
- **Operación Java/OpenAPI:** `importPatient_1`
- **Acceso requerido:** `section.history.view`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/lira/patients/{patientId}/preview` - Previsualizar paciente

Resume disponibilidad y cantidad de registros antes de abrir la historia.

- **Controlador MVC:** `PatientController`
- **Operación Java/OpenAPI:** `preview`
- **Acceso requerido:** `section.history.view`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/lira/patients/{patientId}/refresh` - Abrir paciente local

Activa una historia ya consolidada en PostgreSQL; no consulta Lira y proyecta las secciones según permisos.

- **Controlador MVC:** `PatientController`
- **Operación Java/OpenAPI:** `importPatient`
- **Acceso requerido:** `section.history.view`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

## Tratamientos

### `GET /api/clinical/patients/{patientId}/treatment-options` - Opciones de prescripción

Devuelve diagnósticos y esquemas con su grupo clínico, tipos, intención y estados de consentimiento.

- **Controlador MVC:** `TreatmentController`
- **Operación Java/OpenAPI:** `options`
- **Acceso requerido:** `section.prescriptions.view`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/clinical/patients/{patientId}/treatment-requirements/{schemeId}` - Calcular requisitos del esquema

Indica antropometría y variables necesarias antes de iniciar el protocolo.

- **Controlador MVC:** `TreatmentController`
- **Operación Java/OpenAPI:** `requirements`
- **Acceso requerido:** `section.prescriptions.view`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.; `schemeId` (path, obligatorio): Identificador estable del protocolo o esquema terapéutico.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/clinical/patients/{patientId}/treatments` - Listar tratamientos

Devuelve tratamientos oncológicos locales y su estado actual.

- **Controlador MVC:** `TreatmentController`
- **Operación Java/OpenAPI:** `list_3`
- **Acceso requerido:** `section.prescriptions.view`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/clinical/patients/{patientId}/treatments` - Prescribir tratamiento

Crea tratamiento, ciclos y una logística independiente para cada día con medicación; también agrega una evolución clínica inmutable. Una discordancia diagnóstica evidente exige confirmación y motivo clínico.

- **Controlador MVC:** `TreatmentController`
- **Operación Java/OpenAPI:** `create_4`
- **Acceso requerido:** `section.prescriptions.edit`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.
- **Cuerpo:** `application/json`
- **Respuestas:** `201` Recurso creado correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `422` Los datos son válidos sintácticamente pero no cumplen una regla clínica.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/clinical/patients/{patientId}/treatments/{treatmentId}/detail` - Abrir detalle de tratamiento

Integra protocolo, drogas, ciclos y turnos reales de PostgreSQL.

- **Controlador MVC:** `TreatmentController`
- **Operación Java/OpenAPI:** `detail_1`
- **Acceso requerido:** `section.prescriptions.view`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.; `treatmentId` (path, obligatorio): Identificador local o importado del tratamiento.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/clinical/patients/{patientId}/treatments/{treatmentId}/documents/prescription` - Abrir prescripción

Entrega el documento de prescripción guardado sin reconstruir uno inexistente.

- **Controlador MVC:** `TreatmentDocumentController`
- **Operación Java/OpenAPI:** `prescription`
- **Acceso requerido:** `section.prescriptions.view`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.; `treatmentId` (path, obligatorio): Identificador local o importado del tratamiento.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/clinical/patients/{patientId}/treatments/{treatmentId}/documents/treatment-sheet` - Generar hoja de tratamiento

Genera una hoja imprimible con paciente, esquema, drogas, turno y estados.

- **Controlador MVC:** `TreatmentDocumentController`
- **Operación Java/OpenAPI:** `treatmentSheet`
- **Acceso requerido:** `section.prescriptions.view`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.; `treatmentId` (path, obligatorio): Identificador local o importado del tratamiento.; `cycle` (query, obligatorio): Número de ciclo, comenzando en 1.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/clinical/schemes` - Buscar esquemas

Busca protocolos COIR y personalizados activos.

- **Controlador MVC:** `TreatmentController`
- **Operación Java/OpenAPI:** `schemes`
- **Acceso requerido:** `section.protocols.view`
- **Parámetros:** `q` (query, opcional): Texto de búsqueda; admite coincidencia parcial.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/clinical/schemes/{id}/duration` - Consultar duración

Devuelve la duración operativa estimada del esquema.

- **Controlador MVC:** `TreatmentController`
- **Operación Java/OpenAPI:** `duration`
- **Acceso requerido:** `section.protocols.view`
- **Parámetros:** `id` (path, obligatorio): Identificador del recurso solicitado.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/clinical/treatments/{treatmentId}/consent` - Abrir consentimiento

Entrega el archivo de consentimiento guardado; un estado firmado sin archivo se informa como documento pendiente y este endpoint responde 404.

- **Controlador MVC:** `TreatmentDocumentController`
- **Operación Java/OpenAPI:** `consent`
- **Acceso requerido:** `section.prescriptions.view`
- **Parámetros:** `treatmentId` (path, obligatorio): Identificador local o importado del tratamiento.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

## Hospital de Día

### `GET /api/clinical/application-workflows` - Listar una cola operativa por aplicación

Devuelve una fila por ciclo y día real con medicación. `pharmacy` permite filtrar
`patient_to_bring`; `triage`, `preparation` y `administration` usan por defecto la
fecha de hoy y se ordenan por hora del turno y sillón.

- **Controlador MVC:** `InfusionApplicationWorkflowController`
- **Operación Java/OpenAPI:** `list_10`
- **Acceso requerido:** `section.day-hospital.view`
- **Parámetros:** `queue` (query, opcional): pharmacy, triage, preparation o administration.; `date` (query, opcional): Fecha ISO. En triaje/preparación/administración omitirla equivale a hoy.; `q` (query, opcional): Busca por paciente, DNI, esquema, diagnóstico o droga.; `medicationSource` (query, opcional): Fuente/custodia; use patient_to_bring para quienes deben traer medicación.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/clinical/application-workflows/{patientId}/{treatmentId}/{cycleNumber}/{applicationDay}` - Abrir el circuito completo de una aplicación

Incluye identidad, turno, drogas, duración, estados, trazas y revisión optimista.

- **Controlador MVC:** `InfusionApplicationWorkflowController`
- **Operación Java/OpenAPI:** `get_2`
- **Acceso requerido:** `section.day-hospital.view`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.; `treatmentId` (path, obligatorio): Identificador local o importado del tratamiento.; `cycleNumber` (path, obligatorio): Número de ciclo, comenzando en 1.; `applicationDay` (path, obligatorio): Día del ciclo en el que se administra esta aplicación.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/clinical/application-workflows/{patientId}/{treatmentId}/{cycleNumber}/{applicationDay}/administration/complete` - Cerrar la aplicación con datos reales

Registra hora final, dosis efectivamente administrada, reacción y observación.
La aplicación completada queda inmutable y sale de las colas operativas.

- **Controlador MVC:** `InfusionApplicationWorkflowController`
- **Operación Java/OpenAPI:** `administrationComplete`
- **Acceso requerido:** `application.administration.manage`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.; `treatmentId` (path, obligatorio): Identificador local o importado del tratamiento.; `cycleNumber` (path, obligatorio): Número de ciclo, comenzando en 1.; `applicationDay` (path, obligatorio): Día del ciclo en el que se administra esta aplicación.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/clinical/application-workflows/{patientId}/{treatmentId}/{cycleNumber}/{applicationDay}/administration/interrupt` - Interrumpir una administración en curso

Detiene inmediatamente la aplicación y registra hora, dosis parcial, motivo,
medidas adoptadas, condición del paciente y destino clínico. La interrupción
queda pendiente de una resolución explícita y genera una evolución inmutable.

- **Controlador MVC:** `InfusionApplicationWorkflowController`
- **Operación Java/OpenAPI:** `administrationInterrupt`
- **Acceso requerido:** `application.administration.manage`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.; `treatmentId` (path, obligatorio): Identificador local o importado del tratamiento.; `cycleNumber` (path, obligatorio): Número de ciclo, comenzando en 1.; `applicationDay` (path, obligatorio): Día del ciclo en el que se administra esta aplicación.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/clinical/application-workflows/{patientId}/{treatmentId}/{cycleNumber}/{applicationDay}/administration/resolve` - Resolver una administración interrumpida

Permite reanudar bajo una decisión documentada o cerrar la aplicación sin
completarla. Ambas decisiones preservan la trazabilidad y generan una evolución.

- **Controlador MVC:** `InfusionApplicationWorkflowController`
- **Operación Java/OpenAPI:** `administrationResolve`
- **Acceso requerido:** `application.administration.manage`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.; `treatmentId` (path, obligatorio): Identificador local o importado del tratamiento.; `cycleNumber` (path, obligatorio): Número de ciclo, comenzando en 1.; `applicationDay` (path, obligatorio): Día del ciclo en el que se administra esta aplicación.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/clinical/application-workflows/{patientId}/{treatmentId}/{cycleNumber}/{applicationDay}/administration/start` - Iniciar administración con doble control

Exige PASS, preparación liberada, paciente y etiqueta confirmados, y un segundo
profesional habilitado distinto del usuario activo.

- **Controlador MVC:** `InfusionApplicationWorkflowController`
- **Operación Java/OpenAPI:** `administrationStart`
- **Acceso requerido:** `application.administration.manage`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.; `treatmentId` (path, obligatorio): Identificador local o importado del tratamiento.; `cycleNumber` (path, obligatorio): Número de ciclo, comenzando en 1.; `applicationDay` (path, obligatorio): Día del ciclo en el que se administra esta aplicación.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/clinical/application-workflows/{patientId}/{treatmentId}/{cycleNumber}/{applicationDay}/clinical-authorization` - Registrar triaje y emitir PASS o FAIL

PASS habilita preparación. FAIL exige causa, libera una reserva blanda, retira el turno
activo y mantiene la aplicación disponible para reprogramación.

- **Controlador MVC:** `InfusionApplicationWorkflowController`
- **Operación Java/OpenAPI:** `clinicalAuthorization`
- **Acceso requerido:** `application.triage.manage`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.; `treatmentId` (path, obligatorio): Identificador local o importado del tratamiento.; `cycleNumber` (path, obligatorio): Número de ciclo, comenzando en 1.; `applicationDay` (path, obligatorio): Día del ciclo en el que se administra esta aplicación.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/clinical/application-workflows/{patientId}/{treatmentId}/{cycleNumber}/{applicationDay}/pharmacy-validation` - Validar la orden en Farmacia

Aprueba o rechaza dosis, vía, intervalo y premedicación y fija la procedencia/custodia.
No crea disponibilidad ficticia ni reserva stock.

- **Controlador MVC:** `InfusionApplicationWorkflowController`
- **Operación Java/OpenAPI:** `pharmacyValidation`
- **Acceso requerido:** `application.pharmacy.manage`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.; `treatmentId` (path, obligatorio): Identificador local o importado del tratamiento.; `cycleNumber` (path, obligatorio): Número de ciclo, comenzando en 1.; `applicationDay` (path, obligatorio): Día del ciclo en el que se administra esta aplicación.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/clinical/application-workflows/{patientId}/{treatmentId}/{cycleNumber}/{applicationDay}/preparation/complete` - Registrar mezcla, lotes, etiqueta y TTL

Guarda trazabilidad por droga. Para stock del centro debe vincular todas las reservas
activas y consume las cantidades correspondientes sin perder el historial.

- **Controlador MVC:** `InfusionApplicationWorkflowController`
- **Operación Java/OpenAPI:** `preparationComplete`
- **Acceso requerido:** `application.preparation.manage`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.; `treatmentId` (path, obligatorio): Identificador local o importado del tratamiento.; `cycleNumber` (path, obligatorio): Número de ciclo, comenzando en 1.; `applicationDay` (path, obligatorio): Día del ciclo en el que se administra esta aplicación.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/clinical/application-workflows/{patientId}/{treatmentId}/{cycleNumber}/{applicationDay}/preparation/release` - Liberar mezcla hacia la sala

Rechaza automáticamente preparaciones cuyo TTL ya venció.

- **Controlador MVC:** `InfusionApplicationWorkflowController`
- **Operación Java/OpenAPI:** `preparationRelease`
- **Acceso requerido:** `application.preparation.manage`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.; `treatmentId` (path, obligatorio): Identificador local o importado del tratamiento.; `cycleNumber` (path, obligatorio): Número de ciclo, comenzando en 1.; `applicationDay` (path, obligatorio): Día del ciclo en el que se administra esta aplicación.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/clinical/application-workflows/{patientId}/{treatmentId}/{cycleNumber}/{applicationDay}/preparation/restart` - Descartar y repetir una preparación

Permite descartar una mezcla preparada o liberada por vencimiento, error, rotura o
contaminación, siempre con un motivo documentado y antes de iniciar la administración.
Conserva los lotes anteriores como descartados y devuelve la aplicación a Farmacia para
obtener o reservar nuevamente la medicación y realizar un nuevo control clínico.

- **Controlador MVC:** `InfusionApplicationWorkflowController`
- **Operación Java/OpenAPI:** `preparationRestart`
- **Acceso requerido:** `application.preparation.manage`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.; `treatmentId` (path, obligatorio): Identificador local o importado del tratamiento.; `cycleNumber` (path, obligatorio): Número de ciclo, comenzando en 1.; `applicationDay` (path, obligatorio): Día del ciclo en el que se administra esta aplicación.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/clinical/application-workflows/{patientId}/{treatmentId}/{cycleNumber}/{applicationDay}/preparation/start` - Iniciar preparación estéril

Exige PASS clínico y medicación asegurada para esa aplicación.

- **Controlador MVC:** `InfusionApplicationWorkflowController`
- **Operación Java/OpenAPI:** `preparationStart`
- **Acceso requerido:** `application.preparation.manage`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.; `treatmentId` (path, obligatorio): Identificador local o importado del tratamiento.; `cycleNumber` (path, obligatorio): Número de ciclo, comenzando en 1.; `applicationDay` (path, obligatorio): Día del ciclo en el que se administra esta aplicación.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/clinical/application-workflows/{patientId}/{treatmentId}/{cycleNumber}/{applicationDay}/preparation-label` - Imprimir etiqueta trazable de la mezcla

Incluye dos identificadores del paciente, esquema/ciclo/día, droga y dosis,
lote, vencimiento, diluyente, volumen, concentración, preparador, verificador
declarado, TTL y enlace al QR de identificación.

- **Controlador MVC:** `InfusionApplicationWorkflowController`
- **Operación Java/OpenAPI:** `preparationLabel`
- **Acceso requerido:** `application.preparation.manage`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.; `treatmentId` (path, obligatorio): Identificador local o importado del tratamiento.; `cycleNumber` (path, obligatorio): Número de ciclo, comenzando en 1.; `applicationDay` (path, obligatorio): Día del ciclo en el que se administra esta aplicación.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/clinical/application-workflows/{patientId}/{treatmentId}/{cycleNumber}/{applicationDay}/stock-reservation` - Reservar o liberar stock por componente

La reserva blanda sólo admite `center_stock`. Puede respaldarse con un lote cuantificado
del inventario (evita sobre-reserva de forma atómica) o mediante verificación manual
explícita y documentada cuando aún no existe inventario electrónico.

- **Controlador MVC:** `InfusionApplicationWorkflowController`
- **Operación Java/OpenAPI:** `stockReservation`
- **Acceso requerido:** `application.pharmacy.manage`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.; `treatmentId` (path, obligatorio): Identificador local o importado del tratamiento.; `cycleNumber` (path, obligatorio): Número de ciclo, comenzando en 1.; `applicationDay` (path, obligatorio): Día del ciclo en el que se administra esta aplicación.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/clinical/infusion-candidates` - Listar aplicaciones pendientes

Ordena por fecha cada ciclo y día con medicación que todavía no tiene turno.

- **Controlador MVC:** `InfusionController`
- **Operación Java/OpenAPI:** `candidates`
- **Acceso requerido:** `section.day-hospital.view`
- **Parámetros:** `q` (query, opcional): Texto de búsqueda; admite coincidencia parcial.; `includeScheduled` (query, opcional): Incluye aplicaciones que ya poseen un turno activo; se usa en Farmacia.; `onlySchedulingEligible` (query, opcional): Si es true, devuelve sólo aplicaciones que ya cumplen los requisitos de Farmacia para recibir un turno; false también incluye las bloqueadas para seguimiento.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/clinical/infusions` - Listar turnos

Lista turnos por paciente y/o fecha con farmacia, administración y medicación.

- **Controlador MVC:** `InfusionController`
- **Operación Java/OpenAPI:** `list_4`
- **Acceso requerido:** `section.day-hospital.view`
- **Parámetros:** `patientId` (query, opcional): Identificador local inmutable del paciente.; `date` (query, opcional): Fecha operativa en formato ISO 8601 (AAAA-MM-DD).
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/clinical/infusions` - Asignar aplicación a sillón

Reserva el bloque de un ciclo y día de aplicación concretos; PostgreSQL rechaza duplicados y superposiciones concurrentes.

- **Controlador MVC:** `InfusionController`
- **Operación Java/OpenAPI:** `create_5`
- **Acceso requerido:** `application.schedule.manage`
- **Parámetros:** Ninguno.
- **Cuerpo:** `application/json`
- **Respuestas:** `201` Recurso creado correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `PATCH /api/clinical/infusions/{id}` - Actualizar turno

Mueve, cancela o avanza un turno usando control de versión.

- **Controlador MVC:** `InfusionController`
- **Operación Java/OpenAPI:** `update_3`
- **Acceso requerido:** `application.schedule.manage`
- **Parámetros:** `id` (path, obligatorio): Identificador del recurso solicitado.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/clinical/patients/{patientId}/treatments/{treatmentId}/documents/qr` - Imprimir QR

Genera un QR firmado para identificar paciente, tratamiento, ciclo y día de aplicación sin texto clínico abierto.

- **Controlador MVC:** `QrWorkflowController`
- **Operación Java/OpenAPI:** `document`
- **Acceso requerido:** `section.day-hospital.view`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.; `treatmentId` (path, obligatorio): Identificador local o importado del tratamiento.; `cycle` (query, obligatorio): Número de ciclo, comenzando en 1.; `applicationDay` (query, opcional): Día del ciclo en el que se administra esta aplicación.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/clinical/qr-scans` - Escanear QR

Verifica firma, abre el turno correcto y documenta el escaneo en la historia.

- **Controlador MVC:** `QrWorkflowController`
- **Operación Java/OpenAPI:** `scan`
- **Acceso requerido:** `application.administration.manage`
- **Parámetros:** Ninguno.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `PATCH /api/clinical/treatment-cycles/{patientId}/{treatmentId}/{cycleNumber}/logistics` - Actualizar farmacia por aplicación

Registra para un ciclo y día concretos la medicación recibida, en poder del paciente y el estado de prescripción.

- **Controlador MVC:** `InfusionController`
- **Operación Java/OpenAPI:** `logistics`
- **Acceso requerido:** `application.pharmacy.manage`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.; `treatmentId` (path, obligatorio): Identificador local o importado del tratamiento.; `cycleNumber` (path, obligatorio): Número de ciclo, comenzando en 1.; `applicationDay` (query, opcional): Día del ciclo en el que se administra esta aplicación.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

## Flujos clínicos

### `POST /api/clinical/treatments/{patientId}/{treatmentId}/resume` - Reanudar tratamiento

Reanuda desde un ciclo válido y exige nueva prescripción cuando corresponde.

- **Controlador MVC:** `TreatmentWorkflowController`
- **Operación Java/OpenAPI:** `resume`
- **Acceso requerido:** `workflow.resume`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.; `treatmentId` (path, obligatorio): Identificador local o importado del tratamiento.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/clinical/treatments/{patientId}/{treatmentId}/suspend` - Suspender tratamiento

Suspende transitoria o definitivamente y documenta el motivo.

- **Controlador MVC:** `TreatmentWorkflowController`
- **Operación Java/OpenAPI:** `suspend`
- **Acceso requerido:** `workflow.suspend`
- **Parámetros:** `patientId` (path, obligatorio): Identificador local inmutable del paciente.; `treatmentId` (path, obligatorio): Identificador local o importado del tratamiento.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/clinical/treatment-workflow-requests` - Crear solicitud clínica

Solicita prescripción o continuidad a un usuario autorizado.

- **Controlador MVC:** `TreatmentWorkflowController`
- **Operación Java/OpenAPI:** `create_1`
- **Acceso requerido:** `workflow.request-prescription | workflow.request-continuity`
- **Parámetros:** Ninguno.
- **Cuerpo:** `application/json`
- **Respuestas:** `201` Recurso creado correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/clinical/treatment-workflow-requests/{id}/resolve` - Resolver solicitud

Confirma, rechaza, suspende o continúa y deja trazabilidad clínica.

- **Controlador MVC:** `TreatmentWorkflowController`
- **Operación Java/OpenAPI:** `resolve`
- **Acceso requerido:** `workflow.resolve-prescription | workflow.resolve-continuity`
- **Parámetros:** `id` (path, obligatorio): Identificador del recurso solicitado.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `PATCH /api/clinical/treatment-workflow-requests/{id}/seen` - Marcar solicitud leída

Registra que el destinatario abrió la solicitud.

- **Controlador MVC:** `TreatmentWorkflowController`
- **Operación Java/OpenAPI:** `seen`
- **Acceso requerido:** `authenticated`
- **Parámetros:** `id` (path, obligatorio): Identificador del recurso solicitado.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/clinical/treatment-workflow-requests/inbox` - Consultar solicitudes

Lista las solicitudes asignadas al usuario activo.

- **Controlador MVC:** `TreatmentWorkflowController`
- **Operación Java/OpenAPI:** `inbox`
- **Acceso requerido:** `authenticated`
- **Parámetros:** Ninguno.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

## Configuración

### `GET /api/clinical/coir-catalog` - Listar catálogo COIR

Expone esquemas COIR, duración y periodicidad para vinculación.

- **Controlador MVC:** `ProtocolController`
- **Operación Java/OpenAPI:** `coir`
- **Acceso requerido:** `section.protocols.view`
- **Parámetros:** Ninguno.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/clinical/configuration/{kind}` - Listar configuración

Lista elementos activos o históricos de un tipo permitido.

- **Controlador MVC:** `ConfigurationController`
- **Operación Java/OpenAPI:** `list_5`
- **Acceso requerido:** `section.configuration.view`
- **Parámetros:** `kind` (path, obligatorio): Tipo de configuración permitido por el servicio.; `includeInactive` (query, opcional): Use 1 para incluir elementos archivados; 0 devuelve sólo activos.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/clinical/configuration/{kind}` - Crear configuración

Crea una definición versionada de guía, cálculo, formulario o parámetro.

- **Controlador MVC:** `ConfigurationController`
- **Operación Java/OpenAPI:** `create_6`
- **Acceso requerido:** `section.configuration.manage`
- **Parámetros:** `kind` (path, obligatorio): Tipo de configuración permitido por el servicio.
- **Cuerpo:** `application/json`
- **Respuestas:** `201` Recurso creado correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `PUT /api/clinical/configuration/{kind}/{id}` - Modificar configuración

Actualiza con revisión optimista y conserva la versión anterior.

- **Controlador MVC:** `ConfigurationController`
- **Operación Java/OpenAPI:** `update_1`
- **Acceso requerido:** `section.configuration.manage`
- **Parámetros:** `kind` (path, obligatorio): Tipo de configuración permitido por el servicio.; `id` (path, obligatorio): Identificador del recurso solicitado.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `DELETE /api/clinical/configuration/{kind}/{id}` - Archivar configuración

Desactiva el elemento sin borrar su historial.

- **Controlador MVC:** `ConfigurationController`
- **Operación Java/OpenAPI:** `archive_1`
- **Acceso requerido:** `section.configuration.manage`
- **Parámetros:** `kind` (path, obligatorio): Tipo de configuración permitido por el servicio.; `id` (path, obligatorio): Identificador del recurso solicitado.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/clinical/configuration/{kind}/{id}/versions` - Listar versiones

Devuelve el historial auditable del elemento.

- **Controlador MVC:** `ConfigurationController`
- **Operación Java/OpenAPI:** `versions`
- **Acceso requerido:** `section.configuration.view`
- **Parámetros:** `kind` (path, obligatorio): Tipo de configuración permitido por el servicio.; `id` (path, obligatorio): Identificador del recurso solicitado.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/clinical/configuration/{kind}/{id}/versions/{revision}` - Leer versión

Recupera una revisión histórica exacta.

- **Controlador MVC:** `ConfigurationController`
- **Operación Java/OpenAPI:** `version`
- **Acceso requerido:** `section.configuration.view`
- **Parámetros:** `kind` (path, obligatorio): Tipo de configuración permitido por el servicio.; `id` (path, obligatorio): Identificador del recurso solicitado.; `revision` (path, obligatorio): Revisión histórica exacta del recurso.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/clinical/drugs` - Buscar drogas de protocolo

Busca drogas locales para relacionarlas con componentes del protocolo.

- **Controlador MVC:** `ProtocolController`
- **Operación Java/OpenAPI:** `drugs`
- **Acceso requerido:** `section.protocols.view`
- **Parámetros:** `q` (query, opcional): Texto de búsqueda; admite coincidencia parcial.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/clinical/protocols` - Listar protocolos administrables

Combina protocolos personalizados y catálogo COIR no vinculado.

- **Controlador MVC:** `ProtocolController`
- **Operación Java/OpenAPI:** `list_2`
- **Acceso requerido:** `section.protocols.view`
- **Parámetros:** `includeArchived` (query, opcional): Incluye protocolos archivados cuando vale true.; `includeCatalog` (query, opcional): Incluye esquemas COIR todavía no vinculados cuando vale true.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/clinical/protocols` - Crear protocolo

Crea un protocolo completo y actualiza inmediatamente el catálogo clínico.

- **Controlador MVC:** `ProtocolController`
- **Operación Java/OpenAPI:** `create_2`
- **Acceso requerido:** `section.protocols.edit`
- **Parámetros:** Ninguno.
- **Cuerpo:** `application/json`
- **Respuestas:** `201` Recurso creado correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/clinical/protocols/{id}` - Abrir protocolo

Devuelve componentes, duración, periodicidad y vínculos a drogas.

- **Controlador MVC:** `ProtocolController`
- **Operación Java/OpenAPI:** `get_1`
- **Acceso requerido:** `section.protocols.view`
- **Parámetros:** `id` (path, obligatorio): Identificador del recurso solicitado.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `PUT /api/clinical/protocols/{id}` - Modificar protocolo

Edita componentes, preparación, tiempo y periodicidad con versionado.

- **Controlador MVC:** `ProtocolController`
- **Operación Java/OpenAPI:** `update`
- **Acceso requerido:** `section.protocols.edit`
- **Parámetros:** `id` (path, obligatorio): Identificador del recurso solicitado.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `DELETE /api/clinical/protocols/{id}` - Archivar protocolo

Retira un protocolo de nuevas prescripciones sin romper tratamientos existentes.

- **Controlador MVC:** `ProtocolController`
- **Operación Java/OpenAPI:** `archive`
- **Acceso requerido:** `section.protocols.edit`
- **Parámetros:** `id` (path, obligatorio): Identificador del recurso solicitado.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

## Catálogos

### `GET /api/ajcc8` - Listar sitios AJCC 8

Devuelve los sitios tumorales y variables de estadificación disponibles en el catálogo local.

- **Controlador MVC:** `AjccCatalogController`
- **Operación Java/OpenAPI:** `list_11`
- **Acceso requerido:** `section.tools.view`
- **Parámetros:** Ninguno.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/ajcc8/detail` - Abrir definición AJCC 8

Devuelve TNM, factores específicos y reglas del sitio AJCC seleccionado.

- **Controlador MVC:** `AjccCatalogController`
- **Operación Java/OpenAPI:** `detail_2`
- **Acceso requerido:** `section.tools.view`
- **Parámetros:** `id` (query, obligatorio): Identificador del recurso solicitado.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/ajcc8/stage` - Calcular estadio AJCC 8

Calcula el grupo de estadio de forma determinística a partir del sitio y los valores TNM/factores.

- **Controlador MVC:** `AjccCatalogController`
- **Operación Java/OpenAPI:** `stage`
- **Acceso requerido:** `section.tools.use`
- **Parámetros:** Ninguno.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/catalogs/status` - Consultar catálogos locales

Informa disponibilidad y cantidad de protocolos y esquemas TNM locales.

- **Controlador MVC:** `LegacyCatalogController`
- **Operación Java/OpenAPI:** `status_1`
- **Acceso requerido:** `authenticated`
- **Parámetros:** Ninguno.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/catalogs/update` - Releer catálogos locales

Confirma que los catálogos empaquetados ya están disponibles y versionados.

- **Controlador MVC:** `LegacyCatalogController`
- **Operación Java/OpenAPI:** `update_2`
- **Acceso requerido:** `authenticated`
- **Parámetros:** Ninguno.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/clinical/research/forms` - Listar formularios activos de investigación

Devuelve exclusivamente formularios activos para la sección Investigación. No expone
versiones inactivas ni habilita operaciones administrativas de Configuración.

- **Controlador MVC:** `ResearchFormCatalogController`
- **Operación Java/OpenAPI:** `list_9`
- **Acceso requerido:** `section.research.view`
- **Parámetros:** Ninguno.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Catálogo activo recuperado.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/clinical/tools/calculators` - Listar calculadoras operativas

Devuelve únicamente las calculadoras activas y los ajustes institucionales necesarios para ejecutarlas desde Herramientas, sin exponer administración ni historial.

- **Controlador MVC:** `CalculatorCatalogController`
- **Operación Java/OpenAPI:** `list_8`
- **Acceso requerido:** `section.tools.use`
- **Parámetros:** Ninguno.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/guides` - Listar guías clínicas

Lista las guías locales activas y, con permiso administrativo, también las archivadas.

- **Controlador MVC:** `GuideCatalogController`
- **Operación Java/OpenAPI:** `list_7`
- **Acceso requerido:** `section.tools.view`
- **Parámetros:** `includeInactive` (query, opcional): Use 1 para incluir elementos archivados; 0 devuelve sólo activos.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/guides/file` - Abrir guía clínica

Entrega el PDF local solicitado con nombre y tipo de contenido seguros.

- **Controlador MVC:** `GuideCatalogController`
- **Operación Java/OpenAPI:** `file`
- **Acceso requerido:** `section.tools.view`
- **Parámetros:** `name` (query, obligatorio): Nombre seguro del archivo o recurso.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `PUT /api/guides/import` - Importar guía clínica

Guarda un PDF institucional y actualiza el catálogo de guías.

- **Controlador MVC:** `GuideCatalogController`
- **Operación Java/OpenAPI:** `upload`
- **Acceso requerido:** `section.configuration.manage`
- **Parámetros:** `name` (query, obligatorio): Nombre seguro del archivo o recurso.
- **Cuerpo:** `application/pdf`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `413` El archivo supera el límite permitido.; `415` El tipo declarado o la firma binaria no están permitidos.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/medications/search` - Buscar medicamentos

Busca por genérico, marca o presentación en el catálogo local de drogas. Requiere permiso de lectura de Prescripción.

- **Controlador MVC:** `LegacyCatalogController`
- **Operación Java/OpenAPI:** `medicationSearch`
- **Acceso requerido:** `section.prescriptions.view`
- **Parámetros:** `q` (query, opcional): Texto de búsqueda; admite coincidencia parcial.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/protocols` - Listar protocolos compatibles

Mantiene el contrato histórico de la interfaz y responde desde los catálogos locales.

- **Controlador MVC:** `LegacyCatalogController`
- **Operación Java/OpenAPI:** `protocols`
- **Acceso requerido:** `section.protocols.view`
- **Parámetros:** `source` (query, opcional): Origen del catálogo compatible; por defecto coir.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/protocols/detail` - Abrir protocolo compatible

Devuelve el detalle local de un protocolo COIR o personalizado usando el contrato histórico.

- **Controlador MVC:** `LegacyCatalogController`
- **Operación Java/OpenAPI:** `protocolDetail`
- **Acceso requerido:** `section.protocols.view`
- **Parámetros:** `id` (query, obligatorio): Identificador del recurso solicitado.; `source` (query, opcional): Origen del catálogo compatible; por defecto coir.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/systemic-forms` - Listar formularios sistémicos

Devuelve los formularios institucionales y sus campos para prescripción y documentación.

- **Controlador MVC:** `SystemicFormController`
- **Operación Java/OpenAPI:** `forms`
- **Acceso requerido:** `section.prescriptions.view`
- **Parámetros:** Ninguno.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/tnm` - Listar esquemas TNM

Devuelve el catálogo SEER/TNM local disponible para las herramientas de estadificación.

- **Controlador MVC:** `SeerTnmCatalogController`
- **Operación Java/OpenAPI:** `list_6`
- **Acceso requerido:** `authenticated`
- **Parámetros:** Ninguno.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/tnm/detail` - Abrir esquema TNM

Devuelve campos, opciones y reglas del esquema TNM seleccionado.

- **Controlador MVC:** `SeerTnmCatalogController`
- **Operación Java/OpenAPI:** `detail`
- **Acceso requerido:** `authenticated`
- **Parámetros:** `id` (query, obligatorio): Identificador del recurso solicitado.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

## Archivos clínicos

### `POST /api/media/images` - Guardar imagen clínica

Guarda una imagen o anotación rasterizada y valida su firma binaria.

- **Controlador MVC:** `ClinicalFileController`
- **Operación Java/OpenAPI:** `uploadImage`
- **Acceso requerido:** `section.studies.edit`
- **Parámetros:** Ninguno.
- **Cuerpo:** `application/json`
- **Respuestas:** `201` Recurso creado correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `413` El archivo supera el límite permitido.; `415` El tipo declarado o la firma binaria no están permitidos.; `422` Los datos son válidos sintácticamente pero no cumplen una regla clínica.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/media/images/{name}` - Abrir imagen clínica

Entrega una imagen local autenticada y cacheable.

- **Controlador MVC:** `ClinicalFileController`
- **Operación Java/OpenAPI:** `image`
- **Acceso requerido:** `section.studies.view`
- **Parámetros:** `name` (path, obligatorio): Nombre seguro del archivo o recurso.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/media/studies` - Subir estudio

Guarda por streaming, valida formato y permite borrar durante la misma sesión.

- **Controlador MVC:** `ClinicalFileController`
- **Operación Java/OpenAPI:** `uploadStudy`
- **Acceso requerido:** `section.studies.edit`
- **Parámetros:** `patientId` (query, obligatorio): Identificador local inmutable del paciente.; `studyId` (query, obligatorio): Identificador del estudio dentro de la historia clínica del paciente.; `name` (query, obligatorio): Nombre seguro del archivo o recurso.
- **Cuerpo:** `application/octet-stream`
- **Respuestas:** `201` Recurso creado correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `413` El archivo supera el límite permitido.; `415` El tipo declarado o la firma binaria no están permitidos.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/media/studies/{name}` - Abrir archivo de estudio

Entrega el archivo autenticado con tipo y nombre seguros.

- **Controlador MVC:** `ClinicalFileController`
- **Operación Java/OpenAPI:** `study`
- **Acceso requerido:** `section.studies.view`
- **Parámetros:** `name` (path, obligatorio): Nombre seguro del archivo o recurso.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `DELETE /api/media/studies/{name}` - Eliminar carga reciente

Elimina únicamente con el token temporal de la sesión que subió el archivo.

- **Controlador MVC:** `ClinicalFileController`
- **Operación Java/OpenAPI:** `deleteStudy`
- **Acceso requerido:** `section.studies.edit`
- **Parámetros:** `name` (path, obligatorio): Nombre seguro del archivo o recurso.; `X-Study-Delete-Token` (header, opcional): Token temporal emitido al subir el archivo; sólo permite eliminarlo durante esa sesión.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/study-templates` - Listar plantillas anatómicas

Combina biblioteca incluida y plantillas personalizadas.

- **Controlador MVC:** `StudyTemplateController`
- **Operación Java/OpenAPI:** `list_1`
- **Acceso requerido:** `section.studies.view`
- **Parámetros:** `scope` (query, opcional): Origen de plantillas: all, bundled o custom.; `includeInactive` (query, opcional): Use 1 para incluir elementos archivados; 0 devuelve sólo activos.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/study-templates` - Crear plantilla anatómica

Guarda imagen, metadatos, licencia y confirmación de derechos.

- **Controlador MVC:** `StudyTemplateController`
- **Operación Java/OpenAPI:** `create`
- **Acceso requerido:** `section.configuration.manage`
- **Parámetros:** `title` (query, obligatorio): Título visible de la plantilla anatómica.; `category` (query, obligatorio): Categoría anatómica usada para ordenar y filtrar la plantilla.; `tags` (query, opcional): Etiquetas separadas por comas para facilitar la búsqueda y clasificación.; `author` (query, opcional): Autor o institución responsable de la imagen.; `attribution` (query, opcional): Texto de atribución requerido por el autor o la licencia.; `license` (query, opcional): Licencia o condición de uso declarada.; `description` (query, opcional): Descripción clínica y visual de la plantilla.; `sourceUrl` (query, opcional): URL de procedencia declarada; no se descarga automáticamente.; `licenseUrl` (query, opcional): URL donde puede verificarse la licencia.; `rightsConfirmed` (query, opcional): Debe valer 1 para confirmar que se poseen derechos de uso.; `name` (query, opcional): Nombre seguro del archivo o recurso.
- **Cuerpo:** `image/png`, `image/jpeg`, `image/gif`, `image/webp`, `image/bmp`, `image/tiff`
- **Respuestas:** `201` Recurso creado correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `413` El archivo supera el límite permitido.; `415` El tipo declarado o la firma binaria no están permitidos.; `500` Error interno sin exposición de detalles sensibles.

## Administración

### `GET /api/admin/roles` - Listar roles

Lista roles y permisos disponibles.

- **Controlador MVC:** `AdminController`
- **Operación Java/OpenAPI:** `roles`
- **Acceso requerido:** `admin.manage-roles`
- **Parámetros:** Ninguno.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/admin/roles` - Crear rol

Crea un rol personalizado con permisos explícitos.

- **Controlador MVC:** `AdminController`
- **Operación Java/OpenAPI:** `createRole`
- **Acceso requerido:** `admin.manage-roles`
- **Parámetros:** Ninguno.
- **Cuerpo:** `application/json`
- **Respuestas:** `201` Recurso creado correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `PUT /api/admin/roles/{id}` - Modificar rol

Actualiza nombre, estado y permisos del rol.

- **Controlador MVC:** `AdminController`
- **Operación Java/OpenAPI:** `updateRole`
- **Acceso requerido:** `admin.manage-roles`
- **Parámetros:** `id` (path, obligatorio): Identificador del recurso solicitado.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/admin/security-settings` - Leer seguridad

Devuelve la política de acceso obligatorio y duración de sesión.

- **Controlador MVC:** `AdminController`
- **Operación Java/OpenAPI:** `security`
- **Acceso requerido:** `admin.manage-security`
- **Parámetros:** Ninguno.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `PUT /api/admin/security-settings` - Modificar seguridad

Mantiene login obligatorio y actualiza la duración de sesión.

- **Controlador MVC:** `AdminController`
- **Operación Java/OpenAPI:** `updateSecurity`
- **Acceso requerido:** `admin.manage-security`
- **Parámetros:** Ninguno.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/admin/users` - Listar usuarios

Lista usuarios, roles y estado para administración.

- **Controlador MVC:** `AdminController`
- **Operación Java/OpenAPI:** `users`
- **Acceso requerido:** `admin.manage-users`
- **Parámetros:** Ninguno.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/admin/users` - Crear usuario

Crea una cuenta y asigna roles existentes.

- **Controlador MVC:** `AdminController`
- **Operación Java/OpenAPI:** `createUser`
- **Acceso requerido:** `admin.manage-users`
- **Parámetros:** Ninguno.
- **Cuerpo:** `application/json`
- **Respuestas:** `201` Recurso creado correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `PUT /api/admin/users/{id}` - Modificar usuario

Actualiza perfil, estado, contraseña y roles.

- **Controlador MVC:** `AdminController`
- **Operación Java/OpenAPI:** `updateUser`
- **Acceso requerido:** `admin.manage-users`
- **Parámetros:** `id` (path, obligatorio): Identificador del recurso solicitado.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/clinical/users` - Buscar destinatarios clínicos

Lista usuarios habilitados para una capacidad de flujo.

- **Controlador MVC:** `AdminController`
- **Operación Java/OpenAPI:** `clinicalUsers`
- **Acceso requerido:** `authenticated`
- **Parámetros:** `capability` (query, opcional): Permiso o capacidad clínica que debe poseer el usuario destinatario.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

## Integraciones

### `POST /api/agent/chat` - Consultar agente clínico

Requiere section.agent.view. Acepta una consulta de hasta 8000 caracteres y envía sólo los últimos 12 mensajes no vacíos del historial, con hasta 8000 caracteres cada uno. Solicita JSON estructurado común a proveedores OpenAI compatibles y Ollama, valida tablas, gráficos, seguimientos y resaltados, y conserva como respuesta textual cualquier salida tradicional o JSON incompleto. Si el último mensaje user repite la consulta actual, se elimina del historial antes de llamar al LLM. timelineEvents y consultAgents se aceptan por compatibilidad pero no se incorporan al prompt en esta versión.

- **Controlador MVC:** `LlmController`
- **Operación Java/OpenAPI:** `agent`
- **Acceso requerido:** `section.agent.view`
- **Parámetros:** Ninguno.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.; `502` El servicio LLM respondió con un resultado inválido.; `503` El servicio LLM está desactivado o no está configurado.; `504` El servicio LLM excedió el tiempo de espera.

### `GET /api/config` - Leer configuración LLM

Devuelve endpoint, modelo y parámetros sin revelar la API key.

- **Controlador MVC:** `LlmController`
- **Operación Java/OpenAPI:** `config`
- **Acceso requerido:** `section.configuration.view`
- **Parámetros:** Ninguno.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `PUT /api/config` - Guardar configuración LLM

Valida y cifra la API key antes de persistirla.

- **Controlador MVC:** `LlmController`
- **Operación Java/OpenAPI:** `updateConfig`
- **Acceso requerido:** `section.configuration.manage`
- **Parámetros:** Ninguno.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/llm/extract-timeline` - Extraer línea de tiempo

Solicita eventos estructurados y auditables a partir de texto clínico.

- **Controlador MVC:** `LlmController`
- **Operación Java/OpenAPI:** `timeline`
- **Acceso requerido:** `section.timeline.view`
- **Parámetros:** Ninguno.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.; `502` El servicio LLM respondió con un resultado inválido.; `503` El servicio LLM está desactivado o no está configurado.; `504` El servicio LLM excedió el tiempo de espera.

### `POST /api/llm/fill-systemic-form` - Completar formulario sistémico

Extrae únicamente campos configurados como asistidos por LLM.

- **Controlador MVC:** `LlmController`
- **Operación Java/OpenAPI:** `fillSystemic`
- **Acceso requerido:** `section.prescriptions.edit`
- **Parámetros:** Ninguno.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.; `502` El servicio LLM respondió con un resultado inválido.; `503` El servicio LLM está desactivado o no está configurado.; `504` El servicio LLM excedió el tiempo de espera.

### `GET /api/llm/status` - Consultar estado LLM

Requiere section.agent.view e informa proveedor, modelo y si la integración está habilitada y posee endpoint configurado. No prueba conectividad ni expone secretos.

- **Controlador MVC:** `LlmController`
- **Operación Java/OpenAPI:** `status`
- **Acceso requerido:** `section.agent.view`
- **Parámetros:** Ninguno.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/llm/summarize` - Resumir eventos

Resume hasta 250 eventos sin inventar información.

- **Controlador MVC:** `LlmController`
- **Operación Java/OpenAPI:** `summarize`
- **Acceso requerido:** `section.timeline.view`
- **Parámetros:** Ninguno.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.; `502` El servicio LLM respondió con un resultado inválido.; `503` El servicio LLM está desactivado o no está configurado.; `504` El servicio LLM excedió el tiempo de espera.

### `POST /api/llm/test` - Probar conexión LLM

Prueba un borrador de configuración sin guardarlo.

- **Controlador MVC:** `LlmController`
- **Operación Java/OpenAPI:** `test`
- **Acceso requerido:** `section.configuration.manage`
- **Parámetros:** Ninguno.
- **Cuerpo:** `application/json`
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.; `502` El servicio LLM respondió con un resultado inválido.; `503` El servicio LLM está desactivado o no está configurado.; `504` El servicio LLM excedió el tiempo de espera.

## Estado

### `GET /api/clinical/status` - Estado clínico

Comprueba PostgreSQL y confirma que el sistema es local, unificado e independiente.

- **Controlador MVC:** `StatusController`
- **Operación Java/OpenAPI:** `clinical`
- **Acceso requerido:** `public`
- **Parámetros:** Ninguno.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/lira/status` - Compatibilidad Lira

Informa que las rutas históricas operan sobre HCOP JP local.

- **Controlador MVC:** `StatusController`
- **Operación Java/OpenAPI:** `liraCompatibility`
- **Acceso requerido:** `public`
- **Parámetros:** Ninguno.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `500` Error interno sin exposición de detalles sensibles.

### `GET /api/runtime/status` - Estado de ejecución

Expone versión y motor para diagnóstico y automatización.

- **Controlador MVC:** `StatusController`
- **Operación Java/OpenAPI:** `runtime`
- **Acceso requerido:** `public`
- **Parámetros:** Ninguno.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `500` Error interno sin exposición de detalles sensibles.

### `POST /api/runtime/stop` - Instrucciones de detención

Indica el mecanismo seguro de parada del contenedor.

- **Controlador MVC:** `StatusController`
- **Operación Java/OpenAPI:** `stop`
- **Acceso requerido:** `admin.manage-security`
- **Parámetros:** Ninguno.
- **Cuerpo:** Sin cuerpo.
- **Respuestas:** `200` Solicitud procesada correctamente.; `400` Parámetros o cuerpo de solicitud inválidos.; `401` Sesión ausente, vencida o revocada.; `403` El usuario no posee el permiso requerido.; `404` Paciente, tratamiento, archivo o recurso inexistente.; `409` Conflicto de revisión, estado, superposición o integridad.; `500` Error interno sin exposición de detalles sensibles.
