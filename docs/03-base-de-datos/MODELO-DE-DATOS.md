# Modelo de datos

PostgreSQL es la única base operacional. Flyway crea el esquema de forma
reproducible al primer inicio.

La definición exacta está en `src/main/resources/db/migration`. El
[diccionario de datos](DICCIONARIO-DE-DATOS.md) explica las 34 tablas y sus
relaciones sin reemplazar esas migraciones.

## Identidad y acceso

- `local_users`
- `local_roles`
- `local_permissions`
- `local_user_roles`
- `local_role_permissions`
- `local_sessions`
- `local_security_settings`

## Paciente e historia

- `patients`: identidad y cobertura;
- `hcop_patient_documents`: hoja clínica JSON versionada;
- `patient_records`: registros normalizados opcionales;
- `local_patient_record_overlays`: cambios locales sobre importaciones.

La hoja JSON conserva la estructura y el orden visual. Los dominios operativos
críticos también se guardan en tablas relacionales.

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
