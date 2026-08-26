# 03 · Diseñar PostgreSQL y Flyway

## PostgreSQL como garantía

Las reglas que no pueden depender de la velocidad o buena conducta de un
navegador deben existir en la base:

- PK y FK;
- `NOT NULL`;
- `UNIQUE`;
- `CHECK` para estados/rangos;
- índices parciales;
- control de revisión;
- trigger/bloqueo para superposición de turnos.

## Orden de construcción

1. identidad y seguridad;
2. paciente y hoja clínica;
3. tratamientos y ciclos;
4. Hospital de Día;
5. configuración/versiones;
6. workflows, QR y auditoría;
7. archivos y settings.

El modelo actual completo está en el
[diccionario de las 35 tablas](../03-base-de-datos/DICCIONARIO-DE-DATOS.md).

## Migraciones Flyway

Ubicación (dentro de `backend/`, único servicio con base de datos):

```text
backend/src/main/resources/db/migration/
V001__core_schema.sql
V002__rbac_seed.sql
V003__scheduler_overlap_guard.sql
...
V013__jwt_auth.sql
V014__drop_local_sessions.sql
```

Reglas:

- una migración aplicada nunca se edita;
- el nombre explica intención, no ticket;
- DDL y backfill deben ser determinísticos;
- no depender de una ruta personal;
- los seeds usan claves naturales y `ON CONFLICT` cuando deben ser repetibles;
- probar desde una base vacía y desde la versión anterior;
- `clean` permanece deshabilitado.

La instalación actual contiene 14 migraciones, de `V001` a `V014`. `V013`
agrega la sesión JWT (`local_session_state`, `local_refresh_tokens`) y `V014`
elimina `local_sessions` (aditiva-terminal: el modo cookie fue retirado del
código antes de esta migración, no al revés — nunca borre una tabla que el
código todavía lee). El documento demostrativo no se inserta mediante SQL:
Java lee el único recurso de paciente demostrativo versionado,
`backend/src/main/resources/bootstrap/patients/test-savatierra-v3.json`,
después de crear el administrador y los catálogos. La combinación de búsqueda por
`seedKey`, `ON CONFLICT DO NOTHING`, índice único e inserción condicional de la
hoja debe producir una sola ficha incluso con reinicios o arranques
concurrentes.

Para recrear esta capacidad:

- mantenga `HCOP_SEED_EXAMPLE_PATIENT=true` como valor predeterminado y acepte
  `false` como exclusión explícita;
- use una identidad y una historia creadas íntegramente desde cero, marcadas con
  `meta.demo` y `meta.demoSeedKey`; jamás derive, anonimice o pseudonimice una
  historia real para generar el recurso;
- distribuya el caso compuesto ficticio de colon y melanoma con
  `meta.demoContentVersion=3` y registre en
  `meta.demoManagedRevision` la revisión escrita por el bootstrap;
- conserve `hcop-default-test-savatierra-v1` como `meta.demoSeedKey` estable para
  mantener la compatibilidad con instalaciones ya sembradas;
- trate la misma versión como no-op;
- aplique una versión más nueva únicamente si
  `revision == meta.demoManagedRevision`; cualquier edición humana impide
  futuras actualizaciones automáticas;
- si existe la identidad marcada pero falta su hoja, repare únicamente esa
  ausencia;
- trate el seed como best-effort y nunca bloquee el arranque: una colisión de DNI
  o historia clínica con un paciente sin la marca, o la falta del actor de
  auditoría, debe producir warning y omisión sin apropiarse de esa fila;
- ante un conflicto optimista concurrente, relea y acepte la escritura ganadora
  cuando ya satisface el contrato; si no puede resolverlo, registre warning y
  finalice como no-op;
- no escriba `local_session_state.active_patient_id` durante el bootstrap;
- pruebe desactivación, repetición, concurrencia, colisiones y recurso inválido;
- considere un recurso empaquetado inválido un defecto de release y haga que la
  validación previa a publicación rechace el artefacto, sin convertir una
  contingencia de datos operativos en una caída del servicio.

## Elección entre columnas y JSONB

Use columnas cuando el dato:

- participa en relaciones;
- se filtra/ordena con frecuencia;
- necesita restricción;
- interviene en concurrencia;
- representa un estado operativo.

Use JSONB cuando:

- la estructura clínica varía;
- se preserva una hoja o snapshot;
- el catálogo permite definiciones heterogéneas;
- la lectura suele ser del documento completo.

No coloque toda la aplicación en un único JSON. Tampoco normalice cada línea
narrativa hasta volver imposible conservar el documento clínico.

## Identificadores

- IDs internos generados por la base para filas técnicas;
- UUID para archivos/eventos distribuidos;
- ID estable textual para tratamiento cuando debe conservar compatibilidad;
- `source_id` local y estable para paciente;
- no reutilizar IDs borrados;
- no usar DNI como PK.

## Tiempo

- `date` para fecha clínica civil;
- `timestamptz` para instantes;
- zona institucional definida;
- no guardar horas formateadas como texto;
- mostrar en la zona del usuario, conservar el instante.

## Control optimista

Agregue `revision bigint NOT NULL DEFAULT 1 CHECK (revision > 0)` a recursos
mutables compartidos. Toda respuesta devuelve la revisión y toda escritura
compara la esperada.

## Turnos sin superposición

La UI puede colorear dónde entra un bloque, pero la base debe impedir la carrera
“ambos vieron libre y ambos guardaron”. La estrategia actual:

1. normaliza sillón;
2. toma un advisory lock transaccional por sillón;
3. compara intervalos activos;
4. lanza `23P01` ante intersección;
5. el Service lo traduce a `409`.

Pruebe:

- mismo sillón con cruce parcial;
- un turno contenido en otro;
- límites contiguos sin cruce;
- distinto sillón;
- turno cancelado;
- dos transacciones simultáneas.

## Índices

Cada índice debe responder una consulta observada. Verifique con `EXPLAIN
ANALYZE` sobre volumen representativo. Mantenga:

- búsqueda de pacientes;
- tratamiento por paciente/fecha;
- turnos por fecha/estado y sillón;
- workflow por destinatario/estado;
- auditoría por paciente/entidad;
- archivos por paciente/tipo/fecha.

## Backup y restauración

El backup íntegro combina:

- dump de PostgreSQL;
- volumen de archivos;
- secretos QR/cifrado;
- configuración externa.

La restauración se prueba periódicamente en otro entorno. Un backup nunca
probado es sólo una suposición.

## Hito de aceptación

Desde una base vacía:

```powershell
docker compose up --build --wait
```

Debe aplicar todas las migraciones una vez. Un segundo inicio no altera el
esquema ni duplica seeds. Flyway debe informar todas las versiones como
exitosas.
