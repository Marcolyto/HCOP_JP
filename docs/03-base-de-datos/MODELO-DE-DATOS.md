# Modelo de datos

PostgreSQL es la única base operacional. Flyway crea el esquema de forma
reproducible al primer inicio.

La definición exacta está en `backend/src/main/resources/db/migration` —
única base de datos del sistema (`bff/` no tiene esquema propio, sólo usa
Redis como caché de sesión). El [diccionario de datos](DICCIONARIO-DE-DATOS.md)
explica las 35 tablas y sus relaciones sin reemplazar esas migraciones.

Diagramas ER (relaciones entre tablas, divididas por dominio para que se
puedan leer): `docs/diagrams/` — `04-modelo-datos-identidad-paciente`,
`05-modelo-datos-tratamiento`, `06-modelo-datos-circuito-farmacia`.

El esquema actual se construye con **14 migraciones**, de `V001` a `V014`.
`V012__patient_seed_identity.sql` no agrega tablas: incorpora la garantía de
unicidad usada por el paciente demostrativo. `V013__jwt_auth.sql` agrega la
sesión JWT (`local_session_state`, `local_refresh_tokens`) y `V014__drop_
local_sessions.sql` elimina la tabla de la sesión-cookie que reemplazan
(aditiva-terminal: el código dejó de leerla/escribirla en un commit previo).

## Identidad y acceso

- `local_users`
- `local_roles`
- `local_permissions`
- `local_user_roles`
- `local_role_permissions`
- `local_session_state`
- `local_refresh_tokens`
- `local_security_settings`

## Paciente e historia

- `patients`: identidad y cobertura;
- `hcop_patient_documents`: hoja clínica JSON versionada;
- `patient_records`: registros normalizados opcionales;
- `local_patient_record_overlays`: cambios locales sobre importaciones.

La hoja JSON conserva la estructura y el orden visual. Los dominios operativos
críticos también se guardan en tablas relacionales.

El único seed de paciente incluido con el producto es sintético. La identidad
guarda `identity_json.seedKey = "hcop-default-test-savatierra-v1"` y la hoja
guarda `meta.demo = true`, el mismo valor en `meta.demoSeedKey`, la versión del
recurso en `meta.demoContentVersion` y la última revisión administrada en
`meta.demoManagedRevision`. El índice único parcial
`uq_patients_identity_seed_key` impide que dos filas compartan una clave de seed
no vacía, incluso ante dos arranques concurrentes.

Una versión más nueva del recurso puede reemplazar únicamente el documento
demostrativo que continúa intacto:

```text
versiónRecurso > meta.demoContentVersion
y revision == meta.demoManagedRevision
```

La misma versión no escribe. Si una persona guarda cualquier cambio, `revision`
deja de coincidir con `demoManagedRevision` y el bootstrap conserva íntegramente
esa edición, aunque exista un recurso más nuevo.

El recurso vigente declara `demoContentVersion=3` y contiene un caso compuesto
de colon y melanoma íntegramente ficticio. No es una transformación,
anonimización ni pseudonimización de una ficha real.

La ejecución es best-effort: una colisión de identidad o la falta de actor de
auditoría sólo genera una advertencia y omite el seed. Si una actualización
optimista pierde una carrera, relee la hoja y acepta al ganador; si el estado no
puede confirmarse, registra una advertencia y termina sin escribir. Ninguna de
estas condiciones bloquea el arranque.

La creación o recuperación de esa ficha no escribe
`local_session_state.active_patient_id`: el ejemplo queda disponible en el
buscador, pero nunca se activa automáticamente para un usuario.

## Tratamiento y Hospital de Día

- `clinical_treatments`;
- `treatment_details`;
- `treatment_cycle_logistics`;
- `treatment_application_logistics`;
- `unified_infusion_sessions`;
- `unified_infusion_medications`;
- `treatment_management_states`;
- `treatment_workflow_requests`;
- `clinical_workflow_events`;
- `clinical_qr_scan_events`;
- `treatment_application_workflows`;
- `application_stock_reservations`;
- `pharmacy_inventory_lots`;
- `application_preparation_lots`;
- `treatment_application_workflow_events`.

La logística planificada y la ejecución clínica son capas distintas:

```text
tratamiento
  └─ ciclo
      └─ día con medicación (`treatment_application_logistics`)
          ├─ circuito seguro (`treatment_application_workflows`)
          │   ├─ reserva por componente (`application_stock_reservations`)
          │   ├─ lote/mezcla preparada (`application_preparation_lots`)
          │   └─ eventos idempotentes (`treatment_application_workflow_events`)
          └─ turno real (`unified_infusion_sessions`)
```

El turno aporta fecha, hora, sillón y duración reales. El circuito por
aplicación es la autoridad de validación farmacéutica, procedencia y reserva,
PASS/FAIL, preparación/TTL y administración. Los estados reflejados en
`unified_infusion_sessions` existen para la grilla operativa y se sincronizan
desde ese circuito; no deben adelantarse directamente.

## Configuración

- `clinical_configuration_items`;
- `clinical_configuration_versions`;
- `scheme_duration_estimates`;
- `system_settings`;
- `reference_records`;
- `local_reference_record_overlays`.

## Archivos y auditoría

- `clinical_files`: metadatos y hash; el binario está en el volumen;
- `clinical_files.upload_session_hash` y `deletable_until`: autorización
  temporal para eliminar una carga de la misma sesión;
- `unified_clinical_audit`: antes/después, actor, entidad y motivo.

## Concurrencia

Las tablas mutables incluyen `revision`. Los `UPDATE` escriben solamente si la
revisión esperada coincide.

Cada comando del circuito además exige una `idempotency_key` y deja el antes y
el después en `treatment_application_workflow_events`. Así, reintentar una
solicitud no repite una transición clínica.

La protección del turnero está implementada en PostgreSQL mediante
`prevent_infusion_overlap()` y el trigger `trg_prevent_infusion_overlap`.
Serializa reservas del mismo sillón y rechaza cualquier intersección incluso si
dos equipos intentan reservar a la vez.
