# Flujo de tratamiento y Hospital de Día

El flujo avanza en una sola dirección operativa:

**Nuevo tratamiento → Farmacia → Agenda → Triaje → Preparación → Sala de hoy → Tratamientos**

Cada etapa tiene una única superficie operativa. En el detalle de Tratamientos,
el turno, el estado de Farmacia y el resultado de la aplicación son indicadores
de sólo lectura; no abren formularios alternativos. Drogas, Aplicaciones y
documentos sirven para consultar el plan indicado y comprobar su ejecución
longitudinal.

## 1. Diagnóstico

El paciente debe tener un diagnóstico guardado. Puede incluir SNOMED, CIE-10,
AJCC, TNM y estadio. El tratamiento queda vinculado al identificador de ese
diagnóstico, no a un texto suelto.

## 2. Prescripción

**Nuevo tratamiento** solicita:

- diagnóstico;
- tipo e intención;
- protocolo/esquema;
- drogas con dosis, unidad explícita, vía y días de aplicación;
- número y periodicidad de ciclos;
- fecha del primer ciclo;
- consentimiento;
- peso y talla en centímetros;
- requisitos particulares del esquema.

El sistema compara de forma conservadora el grupo clínico reconocible del
diagnóstico con el del protocolo. Si la discordancia es evidente, muestra una
advertencia y exige confirmar la excepción con un motivo clínico de al menos
diez caracteres. No impide usos excepcionales u off-label: los vuelve
explícitos y agrega el motivo a la evolución inmutable.

El estado **Firmado · documento pendiente** permite registrar que el
consentimiento fue informado como firmado sin afirmar que ya existe un archivo.
El icono de descarga sólo se habilita cuando el documento está realmente
guardado.

El selector usa el catálogo clínico completo, sin recortar los primeros
resultados. La misma fuente alimenta **Configuración → Protocolos**, por lo que
los esquemas por sitio —incluidos Mama y las categorías alfabéticamente
posteriores— deben aparecer en ambas pantallas. Los protocolos personalizados
activos se integran al catálogo inmediatamente.

Al confirmar se crean, en una sola transacción:

- el tratamiento;
- su detalle;
- cada ciclo con fecha planificada;
- una aplicación por cada día del ciclo que realmente administra medicación en
  Hospital de Día;
- la fecha, las drogas y la duración de sillón propias de cada aplicación;
- la logística de farmacia/prescripción por aplicación;
- una evolución clínica inmutable.

Los componentes de vía oral o marcados como domiciliarios continúan dentro del
plan del ciclo, pero se muestran separados de las aplicaciones HDD: no generan
cola de Farmacia oncológica, turno de sillón ni QR. Todos los puntos de entrada
usan el mismo rango válido de día de aplicación, de **1 a 3650**.

## 3. Farmacia

Cada aplicación —por ejemplo, **Ciclo 1 · Día 8**— se audita de forma
independiente. La validación no admite drogas sin nombre, dosis, unidad
explícita o vía. La procedencia puede ser:

- stock del centro;
- debe traerla el paciente;
- la tiene el paciente;
- recibida en el centro;
- pendiente del proveedor.

La prescripción puede estar confirmada, requerida, solicitada o rechazada. Un
estado de medicación no se copia a los demás días: cada entrega se confirma por
separado. El QR identifica tratamiento, ciclo y día mediante firma
criptográfica; no confía en un identificador libre.

Para stock del centro se registra una reserva por componente. Si se usa la
modalidad manual, exige nota y representa sólo una constatación física: no
descuenta inventario ni garantiza exclusión atómica. En cualquier modalidad,
la lista debe coincidir exactamente con la prescripción: todos los componentes,
sin extras ni duplicados, con la misma clave, ID, dosis y unidad. La clave usa
`sourceItemRef` o `<drugId/nombre>-<ordinal>` para distinguir drogas repetidas.
El buscador textual de Farmacia amplía temporalmente el período consultado para
encontrar pacientes o aplicaciones fuera de la ventana visible.
Los filtros separan prescripción, procedencia, validación y reserva. Para stock
del centro, una reserva activa se muestra como **Stock reservado en el centro**
y cuenta como medicación asegurada; no se presenta como recepción del paciente
ni como preparación terminada.

## 4. Turnero por sillón

Las aplicaciones no turnadas aparecen ordenadas por fecha planificada. Un ciclo
con drogas los días 1, 8, 15 y 21 produce cuatro entradas y requiere cuatro
turnos. No existe un programador alternativo a nivel de ciclo: la antigua
acción **Programar ciclo** fue eliminada y Agenda asigna exclusivamente una
aplicación concreta. Al asignar:

- la duración se estima con las drogas activas de ese día;
- el analizador reconoce `h`, `hs`, `hr`, `hrs`, `hora` y `horas`, además de
  `min`/`minutos`, y suma expresiones combinadas como `1 h 30 min`;
- los tiempos se suman de forma secuencial; sólo se consideran simultáneos
  cuando el protocolo declara un grupo paralelo o una administración
  paralela/simultánea;
- la duración general del protocolo se conserva como referencia máxima y
  respaldo;
- el bloque ocupa todas las fracciones necesarias;
- PostgreSQL toma un bloqueo por sillón y fecha;
- una restricción rechaza cualquier superposición, incluso con dos usuarios al
  mismo tiempo.

Quitar el turno libera las celdas y devuelve sólo esa aplicación a pendientes.
La acción exige un motivo y queda auditada. Conserva los estados ya
documentados de Farmacia y Administración: una modificación de agenda no
reescribe la historia farmacéutica o clínica.

## 5. Triaje y preparación

La cola de Triaje usa la fecha operativa y ordena por hora y sillón. PASS exige
turno activo, validación farmacéutica y medicación asegurada. FAIL documenta el
motivo, libera turno y reserva y deja la aplicación reprogramable.

Preparación sólo recibe aplicaciones con PASS. Registra por componente lote,
vencimiento, cantidad, unidad, diluyente, volumen, concentración y TTL. Exige
una traza por cada componente, aun si la droga se repite. Una preparación
vencida no puede liberarse, administrarse ni reanudarse después de una
interrupción. Antes de administrar, una mezcla preparada o liberada también
puede descartarse por error, rotura o contaminación, con motivo y sin borrar la
traza anterior. Cada traza lleva el `componentKey` canónico persistido por
`V011`; el servidor valida uno a uno clave, droga, cantidad y unidad. Cuando la
orden ya informa cantidad y unidad, esos dos campos son de sólo lectura en la
interfaz y no pueden alterarse durante la preparación.

La cola muestra y filtra **Mezcla vencida** como un estado diferente de
**Preparación lista**. En ese caso ofrece **Descartar y repetir** sólo antes de
iniciar la administración.

## 6. Administración

El QR abre exactamente el paciente, tratamiento, ciclo, día de aplicación y
turno firmados. Sólo está disponible si existe logística real de Hospital de
Día para ese ciclo y día; una pauta exclusivamente oral o domiciliaria no
admite QR HDD. El escaneo se registra una sola vez por identificador de
operación y abre la ficha canónica de Administración. No existe un segundo
formulario de finalización detrás del QR.

**Sala de hoy** puede filtrar **Todo el día**, **En atención** y
**Finalizadas**. El cierre normal exige dosis real y observación y agrega una
evolución inmutable.

Si ocurre una reacción o debe detenerse, **Interrumpir / reacción** registra
dosis parcial, medidas, condición y destino. Después se decide explícitamente
si se reanuda o se cierra sin completar; ambas decisiones preservan la
trazabilidad. Si la aplicación finalmente se completa, el cierre conserva el
historial de interrupción, su resolución y la reacción; no los reemplaza con un
estado final vacío.

La selección del segundo profesional registra la declaración del usuario
activo sobre quién efectuó el control. Exige otra persona habilitada, pero no
vuelve a autenticarla y no equivale a una cofirma electrónica.

## 7. Suspensión y continuidad

- **Suspensión transitoria**: retira ciclos desde el ciclo efectivo y puede
  exigir una nueva prescripción antes de reanudar.
- **Suspensión definitiva**: cierra la continuidad y no se reanuda.
- **Solicitar prescripción/continuidad**: crea una tarea para un usuario con el
  permiso adecuado.
- **Reanudar**: define ciclo y nueva fecha, y documenta quién autorizó.

Toda decisión deja evento de flujo, auditoría y evolución.
