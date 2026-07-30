# Guía operativa por roles — Hospital de día

## Propósito

Esta guía resume qué debe ver y hacer cada rol durante una aplicación
oncológica. Todos trabajan sobre el mismo identificador:

La capacitación visual está en el
[video detallado del circuito](../../src/main/resources/static/help/media/circuito-hospital-dia-paso-a-paso.mp4)
y en su [índice de capítulos y alternativas](VIDEO-CIRCUITO-HOSPITAL-DIA-PASO-A-PASO.md).
El video remarca los controles con recuadros y subtítulos azul intenso; para
Agenda muestra en particular la lista de espera, el arrastre a un sillón y las
acciones para confirmar, mover, inspeccionar o quitar un turno.

`patientId + treatmentId + cycleNumber + applicationDay`

No se copian estados entre aplicaciones. Un tratamiento con cuatro días de
medicación en un ciclo produce cuatro recorridos operativos.

## Vista rápida por rol

| Rol | Pantalla principal | Qué busca | Acción principal |
|---|---|---|---|
| Oncología | Nuevo tratamiento | Paciente y diagnóstico | Prescribir |
| Farmacia | Farmacia | Nombre, DNI, HC, diagnóstico o esquema | Validar procedencia y reservar |
| Admisión | Turnos y sala → Agenda | Aplicaciones listas para agendar | Asignar sillón y horario |
| Triaje | Triaje | Turnos de hoy, ordenados por hora | PASS o FAIL |
| Mezclas | Preparación | Aplicaciones con PASS | Preparar, trazar y liberar |
| Enfermería | Turnos y sala → Sala de hoy | Turnos del día | Doble control, iniciar, interrumpir o completar |
| Auditoría/oncología | Tratamientos | Paciente, ciclo y día | Revisar trazabilidad |

## Inicio de jornada

### Farmacia

1. Abra **Hospital de día → Farmacia**.
2. Use el buscador para localizar por paciente, DNI, historia, diagnóstico o
   esquema. Mientras haya texto, la búsqueda incluye aplicaciones fuera de la
   ventana temporal visible; al limpiarlo vuelve al período seleccionado.
3. Filtre la cola:
   - prescripción confirmada o faltante;
   - validación pendiente, aprobada o rechazada;
   - todos;
   - debe traerla el paciente;
   - pendiente de stock;
   - reservado;
   - recibido o ya en poder del paciente.
4. Trabaje primero los pendientes cuya fecha planificada sea más próxima.
5. No confirme una existencia que no haya sido constatada.

### Triaje

1. Abra **Hospital de día → Triaje**.
2. Verifique la fecha.
3. Recorra la lista en orden de hora y sillón.
4. Abra cada paciente, complete laboratorio, signos vitales y toxicidad.
5. Emita PASS o FAIL con su motivo.

### Preparación

1. Abra **Hospital de día → Preparación**.
2. Atienda sólo aplicaciones con PASS.
3. Registre lote, vencimiento, dilución, concentración, volumen y TTL.
4. Compruebe etiqueta y vigencia antes de liberar.
5. Use el filtro **Mezcla vencida** para localizar preparaciones que deben
   descartarse y repetirse antes de administrar.

### Enfermería

1. Abra **Hospital de día → Turnos y sala → Sala de hoy**.
2. Identifique la aplicación por la lista o mediante QR.
3. Use **Todo el día**, **En atención** o **Finalizadas** para reducir la cola
   sin perder los cierres ya realizados.
4. Confirme identidad, etiqueta y segundo profesional.
5. Registre inicio, dosis real, incidentes y finalización.

## Gates que no pueden omitirse

| Transición | Condiciones obligatorias |
|---|---|
| Validar en Farmacia | Prescripción confirmada; cada droga tiene nombre, dosis, unidad explícita y vía |
| Reservar stock del centro | Validación aprobada y procedencia “stock del centro”; correspondencia exacta de todos los componentes, claves, IDs, cantidades y unidades |
| Emitir PASS | Turno activo, validación aprobada y medicación asegurada |
| Iniciar preparación | PASS y medicación asegurada |
| Finalizar preparación | Exactamente una traza por componente prescripto, incluso si una droga se repite; lotes válidos y TTL |
| Liberar a sala | Preparación finalizada y no vencida |
| Iniciar administración | Turno activo, PASS, preparación liberada, paciente y etiqueta verificados, segundo profesional |
| Completar administración | Administración iniciada, dosis real y observación de cierre |

## Procedencia de la medicación

| Código | Etiqueta en interfaz | Se considera disponible |
|---|---|---|
| `center_stock` | Stock del centro | Sí, únicamente con reserva activa |
| `patient_to_bring` | Debe traerla el paciente | No |
| `patient_has_medication` | La tiene el paciente | Sí |
| `received_center` | Recibida en el centro | Sí |
| `pending_supplier` | Pendiente de proveedor | No |

La reserva de Farmacia es blanda y pertenece a una aplicación concreta. No
equivale a una preparación ni asigna por sí sola un lote a la mezcla.
En la interfaz, **Stock reservado en el centro** se considera medicación
asegurada, pero permanece como una categoría separada de “recibida en el
centro” y de “preparada”.

Si existe inventario cuantificado, se reservan cantidades respaldadas por lotes
reales. Si no existe, la **reserva manual documentada** exige una nota de
constatación física y nombre, cantidad y unidad de cada componente. El sistema
no crea stock ficticio ni completa lotes automáticamente.

La modalidad manual no descuenta ni bloquea un inventario electrónico y no
evita de forma atómica que dos operadores constaten la misma existencia. Debe
interpretarse como evidencia auditada, no como inventario institucional.

La reserva no es una lista libre. Debe contener todos los componentes de la
aplicación, sin faltantes, extras ni duplicados, y conservar el nombre, ID,
dosis numérica y unidad prescriptos. La clave canónica es `sourceItemRef` o,
cuando no existe, `<drugId/nombre>-<ordinal>`; el ordinal distingue drogas
repetidas.

## PASS, FAIL y reprogramación

### PASS

El profesional confirma que laboratorio, signos vitales, toxicidad y evaluación
clínica permiten administrar esa aplicación. El PASS habilita Preparación, pero
no sustituye la liberación farmacéutica.

Desde ese momento, Farmacia no puede cambiar la validación, la procedencia ni
la reserva de esa aplicación. Una corrección exige postergarla y repetir el
triaje, para que el PASS corresponda siempre a la orden realmente preparada.

### FAIL

El profesional registra un motivo clínico y, cuando está definida, una nueva
fecha. El sistema:

- libera la reserva blanda activa;
- deja sin efecto el turno operativo;
- mantiene la aplicación pendiente y reprogramable;
- conserva el evento completo para auditoría.

Nunca debe marcarse una aplicación FAIL como completada ni generar otra
aplicación manual para ocultar la postergación.

## QR y doble chequeo

El QR identifica una única combinación de paciente, tratamiento, ciclo y día.
Al escanear:

- se abre la ficha canónica de Administración de la aplicación correcta;
- se documenta el escaneo y un reintento con el mismo `operationId` no lo
  duplica;
- se mantienen todos los gates de seguridad.

El QR no ofrece un segundo cierre. Inicio, interrupción, resolución y
finalización se registran en la misma ficha que usa **Sala de hoy**.

Para iniciar, el usuario confirma paciente y etiqueta y selecciona a un segundo
profesional habilitado. El segundo profesional debe ser distinto de quien inicia
la administración.

Este registro es una declaración auditada del usuario activo. No equivale a una
contrafirma electrónica ni vuelve a autenticar al segundo profesional.

## Cierre y continuidad

El cierre registra:

- inicio y fin reales;
- dosis administrada;
- reacción y descripción, si existió;
- observación y condición final.

Una aplicación completada es inmutable. La siguiente aplicación del mismo ciclo
conserva su propia fecha y vuelve a recorrer Farmacia, turno, triaje,
preparación y administración.

### Interrupción o reacción durante la administración

Use **Interrumpir / reacción** mientras la administración está en curso. Son
obligatorios la hora, el motivo, la dosis administrada hasta ese momento, las
medidas adoptadas, la condición del paciente y el destino clínico. La
aplicación queda pausada y crea una evolución clínica.

La interrupción debe resolverse expresamente:

- `resume`: reanuda la administración después de documentar la decisión y la
  condición del paciente, únicamente si el TTL de la preparación sigue
  vigente;
- `terminate`: cierra sin completar y exige, además, la dosis total
  administrada.

No use **Completar aplicación** para ocultar una interrupción. Cuando una
aplicación interrumpida se reanuda y luego se completa, el cierre conserva el
evento, la reacción, la dosis parcial y la resolución en el historial.

## Quitar o mover un turno

Quitar un turno requiere un motivo, libera sillón y horario y devuelve la
aplicación a pendientes. La auditoría conserva actor y fecha. La operación no
borra el tratamiento ni modifica los estados de Farmacia o Administración.
Moverlo conserva la identidad de la aplicación, pero la nueva franja debe
confirmarse otra vez.

## Campos y variables operativas

### Identificación y planificación

| Campo | Tipo/formato | Uso |
|---|---|---|
| `patientId` | entero/string numérico | Paciente |
| `treatmentId` | string | Tratamiento prescripto |
| `cycleNumber` | entero ≥ 1 | Número de ciclo |
| `applicationDay` | entero ≥ 1 | Día del protocolo dentro del ciclo |
| `plannedDate` | `AAAA-MM-DD` | Fecha originalmente planificada |
| `durationMinutes` | entero | Tiempo estimado de sillón |
| `scheme` | texto | Protocolo o esquema |
| `applicationDrugs` | lista | Drogas activas de ese día |
| `prescriptionStatus` | código | Estado de la orden médica |

### Farmacia

| Campo | Tipo/valores | Uso |
|---|---|---|
| `medicationSource` | códigos de procedencia | Custodia o procedencia |
| `pharmacyValidationStatus` | `pending`, `approved`, `rejected` | Auditoría farmacéutica |
| `pharmacyValidationNotes` | texto | Hallazgos o motivo de rechazo |
| `stockReservationStatus` | `none`, `pending_verification`, `reserved`, `released`, `consumed`, `not_applicable` | Estado de reserva |
| `verificationMethod` | `inventory` o `manual` | Fuente de verificación |
| `components[]` | lista | Componentes reservados |
| `components[].componentKey` | `sourceItemRef` o clave `<drugId/nombre>-<ordinal>` | Identidad canónica y única del componente |
| `components[].drugName` | texto | Droga |
| `components[].drugId` | texto | Identificador que debe coincidir con la prescripción |
| `components[].requestedQuantity` | decimal | Cantidad numérica |
| `components[].requestedQuantityText` | texto | Dosis original |
| `components[].unit` | texto | Unidad |
| `components[].inventoryLotId` | entero opcional | Lote de inventario real |

### Turno y triaje

| Campo | Tipo/formato | Uso |
|---|---|---|
| `appointment.scheduledAt` | fecha-hora ISO 8601 | Hora del turno |
| `appointment.chair` | texto/número | Sillón |
| `appointment.confirmed` | booleano | Confirmación del turno |
| `decision` | `PASS` o `FAIL` | Gate clínico |
| `laboratory` | objeto JSON | Hemograma, renal, hepático y fecha |
| `vitalSigns` | objeto JSON | Presión, frecuencia, temperatura, peso |
| `toxicity` | objeto JSON | Toxicidad y estado funcional |
| `reason` | texto | Obligatorio en FAIL |
| `rescheduledDate` | `AAAA-MM-DD`, opcional | Nueva fecha sugerida |

### Preparación

| Campo | Tipo/formato | Uso |
|---|---|---|
| `preparations[]` | lista exacta | Una traza por cada componente prescripto, incluidos los repetidos |
| `drugName` | texto | Droga |
| `lot` | texto | Lote usado |
| `expiryDate` | `AAAA-MM-DD` | Vencimiento del lote |
| `quantity` / `quantityText` | decimal / texto | Cantidad preparada |
| `unit` | texto | Unidad |
| `diluent` | texto | Diluyente |
| `finalVolume` | texto | Volumen final |
| `concentration` | texto | Concentración |
| `ttlMinutes` | 1 a 10080 | Vida útil desde la preparación |
| `reservationId` | UUID opcional | Reserva consumida |
| `inventoryLotId` | entero opcional | Lote de inventario |
| `preparationExpiresAt` | fecha-hora ISO 8601 | Vencimiento operativo calculado |

### Administración

| Campo | Tipo/formato | Uso |
|---|---|---|
| `patientVerified` | `true` obligatorio | Identidad comprobada |
| `labelVerified` | `true` obligatorio | Etiqueta/QR comprobado |
| `doubleCheckBy` | usuario o ID | Segundo profesional |
| `startedAt` | fecha-hora ISO 8601 | Inicio real |
| `completedAt` | fecha-hora ISO 8601 | Fin real |
| `actualDose` | texto | Dosis efectivamente administrada |
| `reactionOccurred` | booleano | Existencia de reacción |
| `reactionDescription` | texto | Obligatorio si hubo reacción |
| `observation` | texto | Condición y cierre |
| `interruptedAt` | fecha-hora ISO 8601 | Momento real de la interrupción |
| `reason` | texto | Motivo clínico u operativo de la interrupción |
| `measures` | texto | Medidas adoptadas inmediatamente |
| `patientCondition` | texto | Condición al interrumpir y al resolver |
| `disposition` | `observation`, `medical_review` o `emergency_transfer` | Destino clínico |
| `decision` | `resume` o `terminate` | Resolución de la interrupción |

### Concurrencia y trazabilidad

| Campo | Regla | Uso |
|---|---|---|
| `expectedRevision` | revisión positiva actual | Evita pisar cambios concurrentes |
| `idempotencyKey` | 8–128 caracteres seguros y única por acción | Evita comandos duplicados |
| `revision` | aumenta después de cada cambio | Versión vigente |
| `currentStep` | código calculado | Próxima etapa habilitada |
| `workflowStatus` | código | Estado global de la aplicación |

Ante un `VERSION_CONFLICT`, se debe recargar la ficha y revisar el cambio hecho
por el otro usuario. No se debe repetir el comando con una revisión inventada.

## Contrato HTTP esperado

Todos los endpoints exigen sesión autenticada. Las respuestas de lectura
entregan los campos de identificación, paciente, tratamiento, estados,
`currentStep`, turno, `revision` y fechas de actualización.

### Consultas

| Método y ruta | Parámetros | Resultado |
|---|---|---|
| `GET /api/clinical/application-workflows` | `queue=pharmacy|triage|preparation|administration`, `date=AAAA-MM-DD`, `q=texto`, `medicationSource=código` | Cola operativa filtrada |
| `GET /api/clinical/application-workflows/{patientId}/{treatmentId}/{cycle}/{day}` | Identificador completo | Detalle y reservas de una aplicación |

Para Farmacia, `date` puede omitirse para consultar pendientes de distintas
fechas. Triaje, Preparación y Administración usan la fecha operativa; si se
omite, corresponde al día actual del servidor. En la interfaz de Farmacia, una
búsqueda textual amplía temporalmente el alcance para encontrar coincidencias
fuera de la ventana; un `date` enviado explícitamente a la API sigue siendo un
filtro estricto.

### Comandos

Todos los cuerpos incluyen `expectedRevision` e `idempotencyKey`.

| Método y ruta | Campos propios | Permiso |
|---|---|---|
| `POST .../{cycle}/{day}/pharmacy-validation` | `validated`, `medicationSource`, `notes` | `application.pharmacy.manage` |
| `POST .../{cycle}/{day}/stock-reservation` | `reserved`, `medicationSource`, `verificationMethod`, `notes`, `components[]` | `application.pharmacy.manage` |
| `POST .../{cycle}/{day}/clinical-authorization` | `decision`, `laboratory`, `vitalSigns`, `toxicity`, `reason`, `rescheduledDate` | `application.triage.manage` |
| `POST .../{cycle}/{day}/preparation/start` | `notes` | `application.preparation.manage` |
| `POST .../{cycle}/{day}/preparation/restart` | `notes` obligatorio: vencimiento, error, rotura o contaminación; sólo antes de administrar | `application.preparation.manage` |
| `POST .../{cycle}/{day}/preparation/complete` | `preparations[]`, `verifiedBy`, `notes` | `application.preparation.manage` |
| `POST .../{cycle}/{day}/preparation/release` | `notes` | `application.preparation.manage` |
| `POST .../{cycle}/{day}/administration/start` | `patientVerified`, `labelVerified`, `doubleCheckBy`, `startedAt`, `notes` | `application.administration.manage` |
| `POST .../{cycle}/{day}/administration/interrupt` | `interruptedAt`, `reason`, `actualDose`, `measures`, `patientCondition`, `disposition` | `application.administration.manage` |
| `POST .../{cycle}/{day}/administration/resolve` | `resolvedAt`, `decision`, `notes`, `actualDose`, `patientCondition` | `application.administration.manage` |
| `POST .../{cycle}/{day}/administration/complete` | `completedAt`, `actualDose`, `reactionOccurred`, `reactionDescription`, `observation` | `application.administration.manage` |

El prefijo completo de cada comando es:

`/api/clinical/application-workflows/{patientId}/{treatmentId}/{cycle}/{day}`

Los permisos sugeridos por rol son:

- Farmacia y administrador:
  `application.pharmacy.manage` y `application.preparation.manage`.
- Admisión y administrador: `application.schedule.manage`.
- Oncología, enfermería y administrador: `application.triage.manage`.
- Enfermería y administrador: `application.administration.manage`.
- Lectura de las colas: `section.day-hospital.view`.

El agendamiento continúa integrado con la agenda existente:

| Método y ruta | Uso |
|---|---|
| `GET /api/clinical/infusion-candidates` | Aplicaciones disponibles para turnar |
| `GET /api/clinical/infusions` | Turnos por paciente o fecha |
| `POST /api/clinical/infusions` | Crear turno |
| `PATCH /api/clinical/infusions/{id}` | Mover, confirmar o cancelar turno |

## Secuencia mínima de prueba

1. Crear un paciente ficticio y guardar su diagnóstico.
2. Prescribir un protocolo con al menos dos días de aplicación.
3. En Farmacia, aprobar la orden.
4. Elegir `center_stock` y registrar una reserva manual con nota verificable,
   cantidad y unidad por componente, o seleccionar una procedencia real no
   dependiente del stock del centro.
5. Asignar turno a la primera aplicación.
6. En Triaje, emitir PASS con laboratorio, signos vitales y toxicidad.
7. Iniciar y completar la preparación con lote, vencimiento y TTL.
8. Liberar a sala.
9. Escanear el QR o abrir la aplicación en Sala.
10. Confirmar paciente, etiqueta y segundo profesional; iniciar.
11. Interrumpir la administración, registrar la dosis parcial y reanudarla.
12. Completar con dosis real y observación.
13. Verificar el cierre en **Sala de hoy → Finalizadas**, Tratamientos y la
    trazabilidad.
14. En la segunda aplicación, probar FAIL con motivo y reprogramación; comprobar
    que la reserva y el turno se liberan sin borrar la aplicación.

## Criterios de auditoría

Una aplicación está correctamente documentada cuando puede reconstruirse:

- quién prescribió, validó, reservó, autorizó, preparó, liberó, verificó y
  administró;
- qué versión vio cada actor;
- qué medicación y lote se utilizaron;
- cuándo vencía la preparación;
- qué turno, sillón e intervalos reales tuvo;
- qué decisión clínica se tomó y por qué;
- qué dosis se administró y cómo finalizó el paciente.

La ausencia de inventario integrado debe quedar como reserva manual explícita;
nunca como existencia inferida o lote ficticio.
