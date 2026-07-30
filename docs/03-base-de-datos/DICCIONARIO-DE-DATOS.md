# Diccionario de datos

Este documento describe las 34 tablas creadas por Flyway. La fuente ejecutable
es `src/main/resources/db/migration`; nunca se cambia una migración que ya fue
aplicada: se agrega una nueva versión.

## Convenciones

- `created_at` y `updated_at` son instantes con zona.
- `created_by`, `updated_by` y `actor_user_id` identifican al usuario local.
- `revision` implementa control optimista; aumenta en cada cambio.
- `source_id` es el identificador estable del paciente. Los pacientes creados
  localmente usan la secuencia `local_patient_id_sequence`.
- `jsonb` conserva estructuras clínicas variables; las relaciones operativas
  críticas siguen normalizadas.
- `ON DELETE RESTRICT` impide borrar un padre que conserva hechos clínicos;
  `CASCADE` se reserva para partes inseparables.

## Identidad, sesiones y permisos

### 1. `local_users`

Usuarios autenticables. PK `id`. `username` y `email` son únicos; contiene hash
de contraseña, nombre, especialidad, matrícula, estado y último ingreso. Nunca
guarda la contraseña en claro.

### 2. `local_permissions`

Catálogo inmutable de capacidades. PK `id`; `permission_key` es único. Los
controladores validan estas claves, por ejemplo `section.history.edit`.

### 3. `local_roles`

Roles del sistema y personalizados. PK `id`; `role_key` único. `system_role`
distingue los roles iniciales y `enabled` permite retirarlos sin perder
asignaciones históricas.

### 4. `local_role_permissions`

Relación N:N entre roles y permisos. PK compuesta `(role_id, permission_id)`.
Ambas FK eliminan la asociación al borrar su extremo.

### 5. `local_user_roles`

Relación N:N entre usuarios y roles. PK `(user_id, role_id)`. Conserva quién y
cuándo realizó la asignación.

### 6. `local_sessions`

Sesiones del navegador. PK `token_hash`: sólo se persiste el SHA-256 del token.
FK a usuario y, opcionalmente, paciente activo. Guarda vencimiento, última
actividad, IP y agente del navegador.

### 7. `local_security_settings`

Fila única (`id=1`) con login obligatorio, duración de sesión y revisión. El
sistema mantiene `login_required=true`; `auto_user_id` existe únicamente por
compatibilidad con configuraciones antiguas.

## Paciente e historia clínica

### 8. `patients`

Identidad maestra. PK `source_id`. Campos indexados para DNI, número de historia,
apellido y nombre. Incluye cobertura y número de afiliado. `identity_json`
conserva datos adicionales y `local_only` identifica altas propias de HCOP JP.

### 9. `hcop_patient_documents`

Una hoja clínica por paciente. PK/FK `patient_id`. `document_json` contiene el
documento visual completo; `revision` evita pisar cambios. Conserva autores y
fechas.

Rutas principales del JSON:

| Ruta | Contenido |
|---|---|
| `patient` | copia de contexto para representación de la hoja |
| `narrative.chiefComplaint` | motivo de consulta |
| `narrative.currentIllness` | enfermedad actual |
| `narrative.backgroundClinical` | antecedentes clínicos |
| `narrative.currentMedication` | medicación habitual |
| `narrative.familyOncology` | antecedentes familiares |
| `narrative.gynecology` | antecedentes ginecológicos |
| `exam.weightKg` | peso en kg |
| `exam.heightM` | talla normalizada; la UI edita y muestra cm |
| `oncology.diagnosisRecords` | diagnósticos SNOMED, CIE-10, AJCC, TNM y estadio |
| `oncology.systemicTreatments` | representación narrativa de tratamientos |
| `oncology.surgeries` | cirugías oncológicas |
| `studies` | estudios, orden, metadatos y vínculos a archivos |
| `evolutions` | evoluciones clínicas inmutables/append-only |
| `researchRecords` | formularios de investigación |
| `meta` | revisión, auditoría visual y compatibilidad |

Los diagnósticos y evoluciones no viven en tablas llamadas
`patient_diagnoses` o `clinical_evolutions`: esas tablas no existen.

### 10. `patient_records`

Registros normalizados por paciente, categoría e identificador de origen. PK
`id`; unicidad `(patient_id, category, source_record_id)`. `source_ordinal`
preserva orden y `payload_sha256` detecta cambios.

### 11. `reference_records`

Registros de referencia compartidos, sin paciente. Unicidad
`(category, source_record_id)`. Sirve para material importado o catalogado cuyo
payload requiere orden y hash.

### 12. `local_patient_record_overlays`

Cambios locales sobre registros de paciente importables. Una fila por paciente,
categoría y `record_id`. `operation` sólo acepta `upsert` o `delete`; una
restricción exige payload únicamente para `upsert`.

### 13. `local_reference_record_overlays`

Equivalente anterior para datos de referencia. Unicidad por categoría y
`record_id`.

## Tratamientos, ciclos y Hospital de Día

### 14. `clinical_treatments`

Cabecera longitudinal del tratamiento. PK textual `id`; FK a paciente. Conserva
diagnóstico seleccionado, fechas, ciclo inicial/cantidad/intervalo, tipo,
intención, esquema, oncólogo, estado, consentimiento y duración estimada.
`scheme_name` y `diagnosis` son copias históricas para que una edición posterior
del catálogo no reescriba un tratamiento firmado.

### 15. `treatment_details`

Detalle completo 1:1 del tratamiento. PK/FK `treatment_id`. `detail_json`
contiene protocolo, componentes, drogas, requisitos y representación visual.

### 16. `unified_infusion_sessions`

Turnos/aplicaciones reales. PK `id`; FK a paciente y tratamiento; identifica
ciclo, día de aplicación, fecha/hora, sillón y duración. `application_day`
comparte el rango **1–3650** con logística, workflow y QR. Mantiene por separado:

- `clinical_status`: planned, checked_in, ready, in_progress, observation,
  paused, completed o cancelled;
- `pharmacy_status`: not_required, pending, in_preparation, ready, released o
  cancelled;
- `administration_status`: not_started, in_progress, completed, withheld o
  cancelled;
- confirmación, notas, revisión y referencias de origen.

El trigger `trg_prevent_infusion_overlap` rechaza intervalos superpuestos en el
mismo sillón. Un índice único evita dos turnos activos para la misma combinación
tratamiento/ciclo/día.

### 17. `unified_infusion_medications`

Drogas preparadas/administradas dentro de un turno. FK
`infusion_session_id`. Conserva droga, dosis prescrita, unidad, vía, estados de
preparación y administración, notas y revisión.

### 18. `treatment_cycle_logistics`

Una fila por paciente, tratamiento y ciclo. PK compuesta. Conserva la cabecera
de logística del ciclo y compatibilidad con flujos longitudinales.

### 19. `treatment_application_logistics`

Una fila por paciente, tratamiento, ciclo y día con medicación administrable en
Hospital de Día. Guarda fecha planificada, drogas activas en JSONB y resumen,
duración operativa y su fuente, medicación (`pending`, `received`,
`with_patient`), prescripción, notas y revisión. Es la autoridad de Farmacia y
la lista de espera. Los componentes orales o marcados como domiciliarios se
conservan en el detalle longitudinal del tratamiento, pero no crean una fila
HDD. El planificador reconoce horas (`h`, `hs`, `hr`, `hrs`, `hora`, `horas`) y
minutos, suma tiempos secuenciales y sólo agrupa en paralelo componentes que lo
declaran explícitamente.

### 20. `treatment_management_states`

Estado de continuidad 1:1 por tratamiento: `active`, `temporary_hold` o
`discontinued`. Conserva ciclo efectivo, motivo, fecha de reanudación y si exige
nueva prescripción.

### 21. `treatment_workflow_requests`

Solicitudes entre usuarios para prescripción o continuidad. Relaciona paciente,
tratamiento, ciclo, solicitante y destinatario. Estados pending/resolved/
cancelled; resultado, motivo, reanudación y marcas de lectura/resolución. Un
índice único impide dos solicitudes pendientes iguales.

### 22. `clinical_workflow_events`

Eventos inmutables del flujo. PK UUID. Relaciona opcionalmente la solicitud y
siempre paciente, tratamiento, actor y tipo de evento. `event_json` conserva el
contexto documentado en la historia.

### 23. `clinical_qr_scan_events`

Escaneos QR exitosos. PK UUID. `operation_id` es único para idempotencia;
`code_sha256` permite reconocer el código sin guardar su contenido. Relaciona
paciente, tratamiento, ciclo, día de aplicación, turno y usuario.

## Configuración y catálogos

### 24. `clinical_configuration_items`

Definiciones administrables: protocolos, guías, calculadoras, formularios,
plantillas y parámetros. Clave única `(item_kind, item_key)`. Contiene nombre,
descripción, activo, JSON de definición y revisión.

### 25. `clinical_configuration_versions`

Historial inmutable de cada elemento anterior. Unicidad
`(configuration_item_id, revision)`; guarda definición completa, actor y fecha.

### 26. `scheme_duration_estimates`

Duración operativa por esquema. PK `scheme_id`. Registra minutos, fuente
(`coir_catalog`, `protocol_components`, `manual`), método de matching,
confianza, esquema de origen, duración por componentes, notas y revisión.

### 27. `system_settings`

Configuraciones globales. PK `setting_key`. `setting_value` guarda la parte
pública y `secret_value` la parte cifrada, por ejemplo credenciales LLM.

## Archivos y auditoría

### 28. `unified_clinical_audit`

Auditoría transversal append-only. FK opcional a actor y paciente. Registra
tipo/ID de entidad, acción, antes, después, motivo, request ID y fecha. No
reemplaza la evolución: la auditoría explica la modificación técnica; la
evolución explica el acto clínico.

### 29. `clinical_files`

Metadatos de estudios, imágenes y documentos. PK UUID. Puede vincular paciente
y tratamiento. Guarda clase, nombre original, `storage_key` único, MIME,
tamaño, SHA-256, metadatos, autor y fecha. El binario queda en el volumen, no en
PostgreSQL ni Git.

`upload_session_hash` y `deletable_until` permiten borrar una carga reciente
sólo desde la sesión autorizada. No existe una tabla separada de grants.

## Circuito seguro por aplicación

Las cinco tablas siguientes fueron agregadas por `V008__application_workflow.sql`.
La identidad de una aplicación es siempre
`(patient_id, treatment_id, cycle_number, application_day)`: un ciclo puede
contener varios días y cada día con medicación recorre su propio circuito.

### 30. `pharmacy_inventory_lots`

Inventario opcional por lote. PK `id`. Identifica droga, lote, vencimiento,
unidad y cantidades física/reservada. `inventory_status` acepta `active`,
`quarantined`, `depleted` o `discarded`; una restricción impide reservar más que
la existencia disponible. La reserva manual documentada sigue disponible
cuando el centro aún no lleva inventario electrónico, pero sólo registra
constatación, cantidad y unidad: no descuenta existencias ni ofrece bloqueo
atómico.

### 31. `treatment_application_workflows`

Estado operativo 1:1 de cada aplicación planificada. Su PK compuesta y FK
apuntan a `treatment_application_logistics`. Mantiene por separado:

- `workflow_status`: `prescribed`, `pharmacy_rejected`,
  `medication_pending`, `medication_ready`, `scheduled`, `triage_pending`,
  `clinically_authorized`, `postponed`, `in_preparation`, `prepared`,
  `released`, `in_administration`, `completed` o `cancelled`;
- `medication_source`: `center_stock`, `patient_to_bring`,
  `patient_has_medication`, `received_center` o `pending_supplier`;
- `pharmacy_validation_status`: `pending`, `approved` o `rejected`;
- `stock_reservation_status`: `none`, `pending_verification`, `reserved`,
  `released`, `consumed` o `not_applicable`;
- `clinical_authorization_status`: `pending`, `passed` o `failed`;
- `preparation_status`: `not_started`, `in_preparation`, `prepared`,
  `released` o `cancelled`;
- `administration_status`: `not_started`, `in_progress`, `completed`,
  `withheld` o `cancelled`.

`clinical_assessment`, `preparation_data` y `administration_data` conservan el
detalle variable. Las columnas de usuario y fecha trazan validación, reserva,
triaje, preparación, verificación, liberación, doble control y administración.
`administration_data` conserva también cada interrupción y su resolución, con
dosis parcial, medidas, condición y destino clínico.
`preparation_expires_at` impide liberar, iniciar o reanudar una administración
con mezcla vencida;
`preparation_restart_count` cuenta los descartes y repeticiones. `revision`
protege cada comando contra cambios concurrentes.

### 32. `application_stock_reservations`

Reserva blanda por droga/componente y aplicación. PK UUID. Registra cantidad
solicitada y reservada, unidad, procedencia, método de verificación
(`pending`, `inventory`, `manual` o `patient`) y, cuando corresponde, FK al lote
de inventario. `reservation_status` acepta `pending`, `reserved`, `released`,
`consumed` o `cancelled`. Un índice parcial admite una sola reserva activa para
el mismo componente. La clave de componente proviene de `sourceItemRef` o de
`<drugId/nombre>-<ordinal>`; el ordinal evita fusionar componentes repetidos.
Antes de persistir, el servicio exige correspondencia uno a uno con la
prescripción y rechaza faltantes, extras, duplicados o diferencias de ID,
nombre, cantidad y unidad.

### 33. `application_preparation_lots`

Trazabilidad de cada mezcla preparada. PK UUID. Vincula aplicación, reserva y
lote de inventario; guarda droga, lote, vencimiento, cantidad, diluyente,
volumen final, concentración, TTL, preparador y verificador. Una preparación es
`active` o `discarded`; debe existir una traza por cada componente prescripto,
incluidas las drogas repetidas. Al reiniciar por vencimiento, error, rotura o
contaminación conserva el registro anterior con usuario, fecha y motivo del
descarte. El descarte sólo puede ejecutarse antes de iniciar la administración.

`V011__preparation_component_trace.sql` incorporó `component_key`. Su valor es
la identidad canónica de la orden (`sourceItemRef` o
`<drugId/nombre>-<ordinal>`); sólo puede ser `NULL` en una traza legacy que no
pudo resolverse. El índice parcial
`uq_application_preparation_active_component` permite una sola traza `active`
por aplicación y componente. Antes de guardar, el servicio exige la misma
cantidad de trazas que componentes prescriptos y valida clave, droga, cantidad
y unidad en correspondencia uno a uno.

### 34. `treatment_application_workflow_events`

Bitácora inmutable de comandos del circuito. Guarda acción, usuario,
`expected_revision`, `resulting_revision`, comando recibido y fotografías JSON
antes/después. La unicidad por aplicación e `idempotency_key` evita ejecutar dos
veces la misma orden. `ON DELETE RESTRICT` protege esta evidencia clínica.

## Relaciones principales

```text
local_users ──< local_sessions
     │
     ├──< local_user_roles >── local_roles >── local_role_permissions
     │
patients ──1 hcop_patient_documents
     │
     ├──< clinical_treatments ──1 treatment_details
     │             │
     │             ├──< treatment_cycle_logistics
     │             ├──< treatment_application_logistics
     │             ├──1 treatment_management_states
     │             ├──< treatment_workflow_requests ──< clinical_workflow_events
     │             └──< unified_infusion_sessions ──< unified_infusion_medications
     │                                      └──< clinical_qr_scan_events
     ├──< clinical_files
     └──< unified_clinical_audit
```

Relaciones específicas del circuito por aplicación:

```text
treatment_application_logistics
     └──1 treatment_application_workflows
              ├──< application_stock_reservations >──0..1 pharmacy_inventory_lots
              ├──< application_preparation_lots >────0..1 pharmacy_inventory_lots
              └──< treatment_application_workflow_events

unified_infusion_sessions
     └── identifica el turno activo por paciente + tratamiento + ciclo + día
```

## Roles y permisos iniciales

| Rol | Cobertura inicial |
|---|---|
| Administrador | Todos los permisos existentes. |
| Médico oncólogo | Historia, estudios, prescripción, agente, investigación, línea de tiempo, protocolos/herramientas, visualización de configuración y triaje clínico. |
| Enfermería | Historia/estudios, operación de Hospital de Día, consulta de prescripciones, herramientas y solicitudes operativas. |
| Farmacia | Consulta clínica, Hospital de Día editable, prescripciones/protocolos y solicitud de prescripción. |
| Admisión | Consulta clínica, agenda de Hospital de Día y solicitudes de prescripción/continuidad. |

Permisos específicos del circuito agregado por V008:

| Permiso | Roles iniciales |
|---|---|
| `application.pharmacy.manage` | Farmacia y administrador |
| `application.schedule.manage` | Admisión y administrador |
| `application.triage.manage` | Médico oncólogo, enfermería y administrador |
| `application.preparation.manage` | Farmacia y administrador |
| `application.administration.manage` | Enfermería y administrador |

Todos los roles con `section.day-hospital.view` pueden consultar las colas y el
detalle, pero sólo el permiso específico permite ejecutar su transición. El
documento QR requiere lectura de Hospital de Día; escanearlo y abrir el control
de administración requiere `application.administration.manage`.

La asignación exacta ejecutable está en `V002__rbac_seed.sql`,
`V006__clinical_role_permissions.sql` y `V008__application_workflow.sql`.

## Qué se respalda

Un backup íntegro necesita:

1. PostgreSQL completo;
2. el volumen/ruta `HCOP_STORAGE_ROOT`;
3. los secretos operativos necesarios para QR y cifrado;
4. el archivo `.env` custodiado fuera del repositorio.

Consulte [Backup y restauración](../05-operacion/BACKUP-Y-RESTAURACION.md).
