# Campos y relaciones

## Paciente

| Pantalla | Tabla/campo | Recuperación |
|---|---|---|
| Nombre y apellido | `patients.first_name`, `last_name` | paciente activo |
| DNI | `patients.document_number` | búsqueda e identidad |
| HC | `patients.medical_record_number` | búsqueda e identidad |
| Obra social | `patients.health_insurance` | cabecera, farmacia, turno |
| N.º afiliado | `patients.health_insurance_number` | receta, farmacia, turno |
| Fecha de nacimiento | `patients.birth_date` | cabecera y edad calculada |
| Sexo | `patients.sex` | historia y requisitos |

## Historia clínica

La vista de papel lee `hcop_patient_documents.document_json`. La columna
`revision` se copia a `meta.persistenceRevision` y debe volver en cada guardado.

Rutas frecuentes del JSON:

- `oncology.diagnosisRecords`;
- `narrative.chiefComplaint`;
- `narrative.currentIllness`;
- `personalHistory`;
- `exam`;
- `studies`;
- `oncology.systemicTreatments`;
- `oncology.surgeries`;
- `evolutions`.

## Diagnóstico y tratamiento

`clinical_treatments.patient_id` referencia al paciente y `diagnosis_id`
referencia lógicamente el `id` inmutable del diagnóstico dentro de la historia.
También conserva la descripción para lectura histórica.

El esquema se guarda como `scheme_id` y `scheme_name`: el nombre histórico no
cambia aunque el protocolo sea editado después.

## Ciclos, aplicaciones y turnos

Cada tratamiento crea una fila de `treatment_cycle_logistics` por ciclo. La
fecha planificada es:

```text
primer ciclo + (número de ciclo - ciclo inicial) × intervalo
```

El snapshot del protocolo se expande en `treatment_application_logistics`: una
fila por cada combinación de ciclo y día que tenga drogas administrables en
Hospital de Día. Su fecha es:

```text
fecha del ciclo + (día de aplicación - 1)
```

Cada fila guarda las drogas activas, estado de Farmacia y una duración propia.
Al turnar, `unified_infusion_sessions` relaciona paciente, tratamiento, ciclo y
`application_day`. La fecha real del turno prevalece en la vista operativa.

## Circuito operativo por aplicación

`treatment_application_workflows` comparte la misma clave compuesta que
`treatment_application_logistics`. No representa “todo el ciclo”: representa
un día real con medicación. La pantalla recupera cada paso así:

| Dato o acción visible | Fuente de verdad | Recuperación |
|---|---|---|
| Prescripción y drogas del día | `treatment_application_logistics.prescription_state`, `application_drugs` | cola y detalle de aplicación |
| Validación farmacéutica | `treatment_application_workflows.pharmacy_validation_*` | cola Farmacia |
| Quién aporta la medicación | `treatment_application_workflows.medication_source` | filtro “Debe traer” y detalle |
| Reserva de stock | cabecera `stock_reservation_*` + filas `application_stock_reservations` | detalle Farmacia |
| Fecha/hora/sillón | `unified_infusion_sessions` | turnero, triaje, preparación y sala |
| Laboratorio, signos y toxicidad | `clinical_assessment` | detalle de triaje |
| PASS/FAIL y causa | `clinical_authorization_*` | triaje y escalón actual |
| Preparación y vencimiento | `preparation_*`, `preparation_data` | cola Preparación |
| Lotes, dilución y TTL | `application_preparation_lots` | detalle e impresión de etiqueta |
| Inicio, doble control, interrupción, resolución y cierre | `administration_*`, `administration_data` | cola Sala |
| Historial técnico inmutable | `treatment_application_workflow_events` | auditoría del detalle |
| Acto clínico legible | `hcop_patient_documents.document_json.evolutions` | hoja clínica |

### Estados y reglas de recuperación

- `patient_to_bring` significa que Farmacia auditó la orden, pero el paciente
  aún debe aportar la medicación; `patient_has_medication` confirma que ya la
  posee y `received_center` que el centro la recibió.
- Para `center_stock`, el turno sólo se habilita con
  `stock_reservation_status=reserved`.
- Un FAIL guarda su causa, libera la reserva activa y retira el turno para
  permitir la reprogramación. La evidencia del intento permanece.
- Una preparación `prepared` puede imprimirse mediante
  `GET .../preparation-label`; debe liberarse antes de administrar.
- Si `preparation_expires_at` pasó, no puede liberarse ni administrarse.
  `POST .../preparation/restart` descarta sus lotes activos, conserva la
  trazabilidad y devuelve la aplicación al paso de medicación/preparación.
- Una interrupción conserva en `administration_data` hora, motivo, dosis
  parcial, medidas, condición, destino y resolución. `withheld` representa la
  pausa hasta decidir `resume` o `terminate`.
- Una administración `completed` es inmutable, desaparece de las colas
  pendientes y permanece consultable en **Sala de hoy → Finalizadas**.

Todos los comandos de cambio incluyen `expectedRevision` e `idempotencyKey`.
La primera evita sobrescribir el trabajo de otro usuario; la segunda hace
seguro reintentar la misma acción.

### Identificación por QR

El QR contiene una referencia firmada a paciente, tratamiento, ciclo y día, no
datos clínicos abiertos. `clinical_qr_scan_events` conserva sólo el hash del
código y un `operation_id` único. Tras validarlo, la interfaz debe abrir el
mismo detalle de `treatment_application_workflows` en el paso Administración;
el escaneo agrega una evolución, pero no inicia ni completa la infusión por sí
solo.

## Archivos

`clinical_files.storage_key` apunta a un archivo del volumen Docker. La base
guarda nombre original, tipo MIME, tamaño, SHA-256, paciente, tratamiento y
metadatos. No se almacena un archivo clínico en Git.
