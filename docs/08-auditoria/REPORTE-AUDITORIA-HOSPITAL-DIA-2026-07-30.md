# Reporte de auditoría de Hospital de día

**Fecha:** 30 de julio de 2026
**Alcance:** prescripción, Farmacia, turnos, triaje, preparación,
administración, cierre y consulta longitudinal.
**Método:** recorrido cualitativo por rol, prueba de errores humanos y matriz de
100 casos. La línea de base se tomó antes de las correcciones descriptas en
este documento.

## Resultado ejecutivo

La auditoría detectó que el circuito principal estaba presente, pero varias
acciones permitían interpretaciones inseguras: el cierre heredado por ID de
turno convivía con el flujo canónico, una reserva manual podía parecer un
bloqueo real de inventario, las dosis sin unidad podían llegar a Farmacia y una
reacción durante la administración no tenía un estado intermedio suficientemente
claro.

Las correcciones consolidan una única identidad operativa:

`patientId + treatmentId + cycleNumber + applicationDay`

El recorrido canónico queda:

`Prescripción → Farmacia → Agenda → Triaje → Preparación → Sala → Cierre`

Una administración interrumpida ya no debe forzarse como completada: se
interrumpe, se documenta y luego se reanuda o se cierra sin completar.

## Línea de base cualitativa

Cada rol recorrió 25 situaciones; en conjunto forman los 100 casos iniciales.
`PASS` significa comportamiento claro y utilizable, `PARCIAL` que la función
existía con fricción o ambigüedad, y `FAIL` que faltaba una condición necesaria
o el comportamiento era inseguro.

| Rol | PASS | PARCIAL | FAIL | Total |
|---|---:|---:|---:|---:|
| Farmacia | 10 | 8 | 7 | 25 |
| Enfermería | 7 | 6 | 12 | 25 |
| Oncología | 9 | 9 | 7 | 25 |
| Turnos / Admisión | 14 | 4 | 7 | 25 |
| **Total** | **40** | **27** | **33** | **100** |

Esta tabla es una fotografía previa a la remediación. No debe usarse como
resultado de aceptación de la versión corregida. El detalle reproducible está
en [HOSPITAL-DIA-100-CASOS.md](HOSPITAL-DIA-100-CASOS.md) y los resultados de
cada ejecución se guardan en `docs/08-auditoria/resultados/`.

## Resultado final automatizado

La ejecución final reproducible está en
[hospital-dia-100-casos-20260730-100711.md](resultados/hospital-dia-100-casos-20260730-100711.md).
Evaluó los 100 casos contra la instancia QA y obtuvo exactamente:

| Estado | Cantidad |
|---|---:|
| PASS | **100** |
| FAIL | **0** |
| NO_DATA | **0** |
| MANUAL | **0** |
| **Total** | **100** |

La semilla de carga aportó 2000 filas de Farmacia y candidatos de Agenda, y las
pruebas de seguridad automatizaron los casos antes manuales de concurrencia,
bordes de jornada e interrupción. El resultado final no contiene casos sin
datos ni validaciones manuales pendientes dentro de esta matriz.

### Prueba integral E2E multidroga

La matriz de 100 casos y la prueba integral tienen objetivos distintos y sus
resultados no se mezclan. La matriz verifica colas, contratos, búsquedas,
volumen y concurrencia. Por separado,
`scripts/integration-test.ps1` completó el recorrido transaccional:

`paciente → diagnóstico → tratamiento multidroga → Farmacia → reserva → turno → PASS → preparación → QR → administración → interrupción/reacción → reanudación → cierre`

La aceptación final utilizó una aplicación de **cuatro drogas**. Interrumpió
**Carboplatino al 50 %**, documentó la reacción, reanudó la administración y
terminó en `completed`. Comprobó que el cierre conserva la droga interrumpida,
la dosis parcial, el historial de interrupciones, su resolución y la reacción
final. La suite Java complementaria terminó con **101/101 pruebas aprobadas**.

## Correcciones aplicadas

### 1. Un único cierre de administración

- Se retiró el endpoint heredado
  `POST /api/clinical/infusions/{id}/finalize`.
- El cierre normal usa exclusivamente
  `POST .../application-workflows/{patientId}/{treatmentId}/{cycleNumber}/{applicationDay}/administration/complete`.
- El comando exige revisión optimista, clave de idempotencia, hora real, dosis
  administrada y observación.
- La aplicación completada queda inmutable y conserva evolución y auditoría.

Esto evita cerrar por error un turno sin recorrer los gates de la aplicación.

### 2. Interrupción y resolución de una administración

Se incorporaron dos comandos canónicos:

- `POST .../administration/interrupt`: registra hora, motivo, dosis parcial,
  medidas adoptadas, condición del paciente y destino clínico.
- `POST .../administration/resolve`: permite `resume` o `terminate`.

La interrupción deja la administración pausada. Reanudar requiere una decisión
documentada; cerrar sin completar exige además la dosis total administrada.
Ambas acciones generan evoluciones clínicas inmutables y evitan presentar una
administración parcial como completa.

### 3. Unidad de dosis obligatoria

- La validación farmacéutica rechaza drogas sin nombre, dosis, unidad explícita
  o vía.
- La unidad se conserva desde el protocolo y se muestra junto a la dosis.
- Los protocolos incompletos deben corregirse en **Configuración → Protocolos**
  antes de aprobar la orden.

La unidad no debe inferirse a partir del nombre de la droga ni de una dosis
histórica.

### 4. Reserva manual documentada sin falsa garantía de inventario

- La interfaz la identifica como **Reserva manual documentada**.
- Exige una nota verificable y nombre, cantidad y unidad para cada componente.
- Registra usuario, aplicación y trazabilidad.
- La documentación advierte que la modalidad manual no descuenta ni bloquea
  existencias y no es una reserva atómica.

Cuando se dispone de inventario cuantificado, la reserva debe respaldarse por
lote y cantidad. No se deben mezclar ambos significados bajo una misma
garantía.

### 5. Agenda y concurrencia

- Se valida sillón configurado, jornada, fracción horaria, duración exacta y
  fecha.
- PostgreSQL rechaza superposiciones incluso ante intentos simultáneos.
- Mover un turno elimina su confirmación previa y obliga a reconfirmar la nueva
  franja.
- Quitar un turno exige un motivo, libera la franja y conserva Farmacia y
  Administración; la agenda no reescribe estados clínicos históricos.
- La búsqueda admite múltiples términos y coincidencias sin depender de tildes.

### 6. Sala de hoy

- La cola conserva también las aplicaciones completadas del día.
- El filtro permite elegir **Todo el día**, **En atención** o **Finalizadas**.
- El operador puede reducir la carga visual sin perder la evidencia de cierre
  de la jornada.

### 7. Preparación y trazabilidad

- Cada componente exige droga, lote, vencimiento, cantidad, unidad, diluyente,
  volumen final, concentración y TTL.
- Una preparación vencida aparece como **Mezcla vencida**, puede filtrarse y no
  puede liberarse.
- La repetición conserva la preparación previa como descartada y retorna la
  aplicación al circuito correspondiente.

### 8. Errores humanos y concurrencia

- Los comandos clínicos usan `expectedRevision` para impedir que una pantalla
  desactualizada pise otro cambio.
- `idempotencyKey` evita duplicar una acción por doble clic o reintento de red.
- El sistema registra actor, fecha, estado anterior/posterior y evolución
  cuando corresponde.
- Los mensajes bloqueantes indican qué condición falta en lugar de habilitar un
  atajo alternativo.

## Correcciones de esta iteración

La revisión posterior a la primera remediación agregó las siguientes defensas:

1. **Reserva idéntica a la prescripción.** `components[]` debe contener
   exactamente todos los componentes del ciclo y día. Se rechazan faltantes,
   extras, duplicados y diferencias de nombre, ID de droga, cantidad o unidad.
2. **Identidad canónica por componente.** Se conserva `sourceItemRef`; cuando
   falta, se genera `<drugId/nombre>-<ordinal>`. El ordinal distingue dos
   apariciones válidas de la misma droga.
3. **Preparación uno a uno.** El cierre de preparación exige una traza por cada
   componente prescripto, incluidas las drogas repetidas, sin aceptar
   multiplicidades distintas.
4. **TTL también al reanudar.** Una administración interrumpida no puede volver
   a `in_progress` si venció la preparación; debe descartarse y repetirse.
5. **Descarte con causa operativa.** `preparation/restart` admite descartar una
   mezcla aún vigente por error, rotura o contaminación, además del
   vencimiento. Siempre exige motivo, conserva la traza previa y sólo funciona
   antes de comenzar la administración.
6. **Búsqueda de Farmacia sin encierro temporal.** Mientras el usuario escribe
   una búsqueda textual, la interfaz consulta fuera de la ventana temporal
   visible para encontrar el paciente o día requerido. Al limpiar el texto
   vuelve a aplicarse el período; un `date` enviado explícitamente a la API
   continúa siendo estricto.
7. **Acción de Farmacia explícita.** Cada fila muestra como acción principal la
   siguiente tarea real según su estado: **Validar orden**, **Revalidar orden**,
   **Confirmar disponibilidad**, **Actualizar procedencia** o **Reservar
   stock**. Sólo muestra **Ver detalle** cuando no queda una acción farmacéutica
   inmediata, evitando que el operador deba inferir qué corresponde hacer.
8. **Paso activo coherente con el circuito.** El indicador de siete pasos marca
   como actual el primer gate operativo todavía no resuelto. Distingue
   prescripción, validación/disponibilidad de Farmacia, turno, triaje,
   preparación, sala y cierre sin adelantar visualmente el proceso.
9. **Autoenfoque del sillón al buscar.** Cuando la búsqueda coincide con un
   turno ubicado fuera de los sillones visibles, la agenda desplaza
   automáticamente su ventana horizontal hasta incluir ese sillón y conserva
   el resaltado del resultado. La navegación no modifica el turno.
10. **Traza canónica de preparación (`V011`).** La migración
    `V011__preparation_component_trace.sql` agrega `component_key` a
    `application_preparation_lots` y admite una sola traza activa por
    aplicación y componente. El servicio exige exactamente una preparación
    por componente prescripto y valida clave, droga, cantidad y unidad; las
    drogas repetidas conservan claves diferentes.
11. **Un solo flujo por aplicación.** Se retiró la acción heredada
    **Programar ciclo**. Cada combinación
    `patientId + treatmentId + cycleNumber + applicationDay` recorre Farmacia,
    Agenda, Triaje, Preparación, Sala y Cierre; el turno se asigna únicamente
    desde Agenda a esa aplicación concreta.
12. **Hospital de Día separado de la medicación domiciliaria.** Los
    componentes orales o marcados como domiciliarios permanecen visibles en el
    plan terapéutico, pero no crean logística, cola, sillón ni QR de Hospital de
    Día. El QR sólo se genera cuando ciclo y día tienen una aplicación HDD real.
13. **Duración conservadora y explícita.** El planificador interpreta horas
    escritas como `h`, `hs`, `hr`, `hrs`, `hora` o `horas`, suma además los
    `min`/`minutos` de una misma indicación y redondea a la fracción configurada.
    Los componentes se suman en forma secuencial; sólo se toma el mayor tiempo
    de un grupo cuando el protocolo declara paralelismo o simultaneidad.
14. **Límite único del día de aplicación.** Base de datos, planificación,
    turnos, workflow y QR comparten el rango válido **1–3650**. Un valor fuera
    de ese rango se rechaza en lugar de crear identidades divergentes.
15. **Orden preservada durante la preparación.** Cuando la cantidad y la unidad
    provienen de una prescripción verificable, la interfaz las muestra como no
    editables y el servidor rechaza cualquier valor distinto. Sólo se solicita
    corregir la orden cuando alguno de esos valores no es verificable.
16. **Segundo profesional declarado, no cofirma.** La selección registra la
    declaración del usuario activo acerca de quién realizó el control y exige
    una persona habilitada diferente. No solicita credenciales del segundo
    profesional y no constituye firma ni cofirma electrónica.
17. **QR sin flujo paralelo.** El escaneo registra el control de identidad y
    abre la ficha canónica de Administración. Iniciar, interrumpir, reanudar y
    cerrar usan el mismo circuito de Sala.
18. **Estados conservados al quitar turno.** La acción pide un motivo, audita
    actor y fecha y devuelve la aplicación a pendientes, sin cancelar ni borrar
    su historia de Farmacia o Administración.
19. **Cierre con interrupción preservada.** Una reacción o pausa resuelta queda
    dentro del historial aun cuando la aplicación termine completada.
20. **Filtros operativos completos.** Farmacia distingue validación,
    procedencia y stock reservado; Triaje incluye postergados/no aptos;
    Preparación separa mezclas vencidas de preparaciones listas.

## Riesgos residuales

Los siguientes puntos siguen abiertos y no deben presentarse como garantías ya
resueltas.

| Prioridad | Riesgo | Estado actual | Mejora requerida |
|---|---|---|---|
| Alta | Inventario global y atomismo entre aplicaciones | La correspondencia con la prescripción es estricta y un lote integrado controla cantidades, pero la reserva manual sigue siendo una constatación y no existe todavía una autoridad institucional única para todo el stock. | Integrar catálogo, lotes, vencimientos y reserva/liberación atómica entre aplicaciones concurrentes con la fuente institucional. |
| Alta | Cofirma auténtica | El usuario activo selecciona al segundo profesional y esa declaración queda auditada. | Exigir PIN, reautenticación o firma real del segundo profesional, con identidad propia y no reutilizable. |
| Alta | Umbrales y recencia de laboratorio por protocolo | El triaje guarda resultados, fecha y rangos técnicos generales. | Configurar por protocolo qué análisis se exigen, su antigüedad máxima y umbrales clínicos de PASS/FAIL. |
| Alta | QR en tratamientos de alto riesgo | El QR está disponible y deja trazabilidad, pero el inicio no lo exige en todos los casos. | Permitir una política institucional que haga obligatorio el escaneo para drogas o protocolos de alto riesgo. |
| Alta | Dosis real por componente | La reserva y preparación son por componente, pero el cierre registra todavía una dosis real textual global para la aplicación. | Capturar dosis prescrita, preparada, iniciada, interrumpida y administrada por cada droga/componente. |
| Media | Paginación de colas | La consulta tiene un límite operativo amplio, sin cursor ni total confiable para volúmenes extraordinarios. | Agregar paginación por cursor, total y navegación accesible sin perder filtros ni orden. |

## Criterios para una aceptación clínica

Antes de usar el circuito con pacientes reales, la institución debe validar:

1. permisos de cada rol y separación de funciones;
2. protocolos, unidades y días de aplicación;
3. requisitos y umbrales de laboratorio;
4. política de inventario y custodia;
5. procedimiento de doble control y firma;
6. criterio para QR obligatorio;
7. manejo de reacción, emergencia y cierre sin completar;
8. recuperación ante caída de red, doble envío y cambio concurrente;
9. trazabilidad en historia clínica y auditoría;
10. pruebas con datos ficticios en una base separada.

## Evidencia y repetición

El arnés se ejecuta únicamente contra una instancia QA distinta de producción:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\qa\hospital-day-100-cases.ps1 `
  -BaseUrl http://127.0.0.1:5181
```

El arnés bloquea explícitamente el puerto `5180`, inicia sesión y hace lecturas
de contrato y colas. Si no se pasa un objeto `-Credential`, solicita las
credenciales QA en un diálogo seguro sin exponer la contraseña en el historial.
Los casos de carga, interacción clínica y concurrencia que integran la matriz
quedaron cubiertos por semilla reproducible, contrato o pruebas automatizadas.
La validación clínica institucional con usuarios reales continúa siendo una
etapa distinta de aceptación previa a producción.

## Conclusión

El circuito corregido es más coherente y reduce los errores más peligrosos de
interpretación: cierre fuera de flujo, dosis sin unidad, falsa reserva de stock
y reacción sin estado intermedio. La próxima etapa no debe agregar más botones;
debe cerrar las garantías residuales de inventario, cofirma, reglas clínicas,
QR de alto riesgo, dosis por componente y paginación.
