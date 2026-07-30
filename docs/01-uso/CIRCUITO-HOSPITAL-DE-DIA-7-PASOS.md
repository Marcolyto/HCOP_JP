# Circuito de Hospital de día en 7 pasos

## Objetivo

Este circuito acompaña una aplicación oncológica desde la prescripción hasta su
cierre, con una pantalla sencilla para cada actor y controles que evitan avanzar
cuando falta una condición de seguridad.

Para verlo en la interfaz, use el
[video detallado paso a paso](../../src/main/resources/static/help/media/circuito-hospital-dia-paso-a-paso.mp4).
La [guía de capítulos del video](VIDEO-CIRCUITO-HOSPITAL-DIA-PASO-A-PASO.md)
explica sus recuadros, subtítulos azul intenso y todas las alternativas
mostradas. El
[video resumen de 70 segundos](../media/demo-flujo-7-pasos/flujo-oncologico-7-pasos.mp4)
se conserva como introducción.

La unidad de trabajo no es el tratamiento completo. Es cada aplicación real:

**Paciente + tratamiento + ciclo + día de aplicación**

Por ejemplo, un protocolo con medicación los días 1, 8, 15 y 21 crea cuatro
aplicaciones independientes. Cada una tendrá su propia medicación, turno,
triaje, preparación, administración y trazabilidad.

## Recorrido de la interfaz

Hospital de día concentra el trabajo en un único espacio:

1. **Nuevo tratamiento**
2. **Farmacia**
3. **Turnos y sala**
4. **Triaje**
5. **Preparación**
6. **Tratamientos**

Dentro de **Turnos y sala**, el operador cambia entre **Agenda** y **Sala**. Así
se conserva una sola ruta de trabajo sin repetir formularios.

```text
Prescripción
    ↓
Validación farmacéutica y disponibilidad de medicación
    ↓
Turno
    ↓
Triaje del día: PASS o FAIL
    ↓
Preparación, trazabilidad y liberación
    ↓
Doble control y administración
    ↓
Cierre de la aplicación
```

## Antes de comenzar

Para prescribir, el paciente debe estar abierto y tener un diagnóstico
registrado. El usuario debe comprobar:

- diagnóstico, protocolo e intención;
- peso y talla actuales;
- superficie corporal o criterio de cálculo indicado;
- cantidad, intervalo y días de los ciclos;
- consentimiento y requisitos previos;
- fecha de inicio prevista.

El sistema genera únicamente los días que realmente administran medicación y
mantiene visibles los ciclos futuros.

## Paso 1. Prescripción médica

**Actor principal:** médico oncólogo.

1. Abra **Hospital de día → Nuevo tratamiento**.
2. Seleccione el diagnóstico.
3. Elija el protocolo y revise drogas, días, vía, dosis, **unidad explícita** y
   duración estimada. Una dosis sin unidad no puede avanzar a validación
   farmacéutica.
4. Complete peso, talla, cantidad de ciclos, periodicidad y fecha inicial.
5. Confirme los requisitos del esquema y guarde.

### Resultado

- Se crea el tratamiento y su plan longitudinal.
- Se genera una aplicación por cada día con medicación.
- La orden queda disponible para Farmacia.
- La prescripción y sus variables quedan documentadas en la historia clínica.

El tratamiento completo se consulta en **Tratamientos**; el trabajo operativo
continúa en las colas de cada etapa.

## Paso 2. Validación y disponibilidad por Farmacia

**Actor principal:** Farmacia oncológica.

Abra **Farmacia**. La lista permite buscar por nombre, DNI, historia clínica,
diagnóstico o esquema, y filtrar por procedencia de la medicación.
Cuando hay texto en el buscador, la consulta atraviesa la ventana temporal
visible para poder localizar también un paciente o día planificado fuera de
ella. Al limpiar el texto vuelve a aplicarse el período seleccionado.

Los filtros distinguen prescripción, validación, procedencia y disponibilidad.
Una aplicación con **Stock reservado en el centro** se considera con medicación
asegurada aunque todavía no esté recibida como una entrega del paciente. La
reserva se muestra como un estado propio para no confundirla con una mezcla ya
preparada.

Para cada aplicación:

1. Revise la prescripción, dosis, vía, intervalo, drogas y premedicación.
2. Indique la procedencia:
   - **Stock del centro**.
   - **Debe traerla el paciente**.
   - **La tiene el paciente**.
   - **Recibida en el centro**.
   - **Pendiente de proveedor**.
3. Apruebe o rechace la validación farmacéutica.
4. Si es stock del centro, registre la reserva.

### Reserva blanda y asignación de lote

La reserva de este paso es una **reserva blanda por aplicación**: asegura que la
cantidad necesaria no se ofrezca a otro paciente, pero todavía no afirma que la
mezcla esté preparada.

El lote físico y la trazabilidad final se registran en **Preparación**, después
del PASS clínico. Esto evita consumir o inmovilizar definitivamente un vial
antes de saber si el paciente puede tratarse.

Si existe inventario cuantificado, la reserva debe referenciar sus lotes y
cantidades. Si todavía no existe ese inventario, el sistema sólo admite una
**reserva manual documentada**: exige una nota verificable y nombre, cantidad y
unidad de cada componente, además del usuario responsable.

La reserva debe reproducir **exactamente** la composición prescripta para ese
ciclo y día: no admite componentes faltantes, extras o duplicados, ni cambios
de droga, ID, cantidad o unidad. Cada componente conserva una clave canónica:
se usa `sourceItemRef` cuando el protocolo la informa y, en caso contrario,
`<drugId>-<ordinal>` o `<nombre-normalizado>-<ordinal>`. El ordinal permite
distinguir dos apariciones de la misma droga sin fusionarlas.

La reserva manual documentada es evidencia de una constatación física. **No
descuenta ni bloquea existencias de un inventario electrónico y no ofrece
protección atómica frente a dos operadores.** Cuando el centro disponga de
inventario integrado, debe usarse la reserva respaldada por lote.

> El sistema no inventa existencias, cantidades ni lotes. Una medicación sólo
> queda disponible cuando un profesional registra su procedencia y la
> verificación correspondiente.

### Cuándo la medicación está asegurada

| Procedencia | Condición para continuar |
|---|---|
| Stock del centro | Validación aprobada y reserva activa |
| Debe traerla el paciente | Puede otorgarse el turno, pero no puede emitirse PASS hasta confirmar que la tiene o que fue recibida |
| La tiene el paciente | Disponible |
| Recibida en el centro | Disponible |
| Pendiente de proveedor | Todavía no está asegurada |

## Paso 3. Agendamiento

**Actor principal:** admisión, recepción o secretaría.

1. Abra **Turnos y sala → Agenda**.
2. Busque la aplicación pendiente.
3. Arrástrela a un sillón y horario disponible.
4. Compruebe fecha, franja horaria, duración y sillón.
5. Confirme el turno e informe los estudios o análisis previos requeridos.

Cada aplicación ocupa tantos casilleros como exija su duración. El sistema
rechaza superposiciones, incluso si dos usuarios intentan asignar el mismo
espacio al mismo tiempo.

**Quitar turno** libera solamente esa aplicación y la devuelve a pendientes.
Solicita un motivo y deja auditoría de quién lo hizo y cuándo. No borra el
tratamiento, no altera los demás días del ciclo y **conserva los estados de
Farmacia y Administración** de la aplicación. Quitar un lugar de la agenda no
debe falsear una validación, reserva, preparación o administración histórica.

## Paso 4. Triaje clínico y analítico

**Actores principales:** médico y/o enfermería, según permisos.

Abra **Triaje**. La cola del día aparece ordenada por hora del turno y sillón.
Puede buscar por paciente, DNI, diagnóstico o esquema.

Para cada aplicación:

1. Abra la ficha.
2. Registre laboratorio relevante, incluidos hemograma y funciones renal o
   hepática cuando correspondan.
3. Registre signos vitales y peso del día.
4. Evalúe toxicidad, síntomas y estado funcional.
5. Decida:
   - **PASS:** habilita la preparación.
   - **FAIL:** posterga la aplicación.

### Gate clínico

Un PASS sólo es posible si:

- existe un turno activo;
- Farmacia aprobó la orden;
- la medicación está asegurada.

Después del PASS, la validación, la procedencia y la reserva farmacéuticas
quedan bloqueadas para esa aplicación. Si hace falta cambiarlas, primero se
debe postergar la aplicación y repetir el triaje sobre la orden actualizada.

Un FAIL exige un motivo. Puede indicar una nueva fecha; el turno deja de estar
activo, la aplicación queda disponible para reprogramar y cualquier reserva
blanda de stock del centro se libera.

El FAIL no elimina la aplicación ni falsea un tratamiento como realizado.

## Paso 5. Preparación estéril

**Actor principal:** Farmacia, área de mezclas.

Abra **Preparación**. Sólo se listan aplicaciones que superaron el gate clínico
y están en condiciones de preparar.

1. Marque **Iniciar preparación**.
2. Para cada componente prescripto registre una traza:
   - droga y cantidad;
   - lote y vencimiento;
   - diluyente;
   - volumen final;
   - concentración;
   - tiempo de vida útil o **TTL**.
3. Finalice la preparación.
4. Revise la etiqueta y libere la mezcla a sala.

Para stock del centro, cada componente preparado debe corresponder a una
reserva activa. El vencimiento operativo de la preparación se calcula con el
TTL más corto registrado. Debe existir exactamente una traza por componente,
incluidas todas las apariciones de una droga repetida. Una preparación vencida
no puede liberarse.

La cola diferencia **Mezcla vencida** de **Preparación lista** y permite
filtrarla. Antes de iniciar la administración, la acción indicada es
**Descartar y repetir**: exige un motivo, conserva la preparación anterior como
descartada y vuelve a crear la trazabilidad. Si la administración ya comenzó,
esa acción queda bloqueada y se debe resolver el evento desde la ficha de
Administración.

## Paso 6. Administración en sala

**Actor principal:** enfermería.

Abra **Turnos y sala → Sala de hoy** o escanee el QR de la aplicación. El
selector de la cola permite alternar entre **Todo el día**, **En atención** y
**Finalizadas**. Las aplicaciones completadas permanecen visibles en
**Finalizadas** para el control de cierre de la jornada.

Antes de iniciar:

1. Confirme activamente la identidad del paciente.
2. Compruebe que la etiqueta o QR coincide con paciente, tratamiento, ciclo y
   día.
3. Seleccione al segundo profesional que realizó el doble control.
4. Administre la premedicación indicada.
5. Inicie la administración y registre la hora real.

El segundo profesional debe ser distinto del usuario que inicia y debe tener
permiso para administración.

En la versión actual, el usuario activo declara quién realizó el segundo
control. El sistema audita esa declaración, pero no constituye una firma
electrónica ni solicita nuevamente la credencial del segundo profesional.

El QR no es un atajo que omite controles ni tiene un cierre paralelo. Identifica
la aplicación exacta, deja registrado el escaneo y abre la **ficha canónica de
Administración**. El inicio, la interrupción, la reanudación y el cierre se
documentan únicamente en esa ficha. El inicio continúa bloqueado si falta PASS,
liberación de Farmacia, turno activo o doble chequeo.

## Paso 7. Cierre

**Actor principal:** enfermería.

Al finalizar:

1. Registre la hora real de fin.
2. Registre la dosis efectivamente administrada.
3. Indique si ocurrió una reacción y descríbala cuando corresponda.
4. Documente la condición del paciente y las observaciones de cierre.
5. Confirme **Completar aplicación**.

La aplicación completada queda inmutable. El árbol
**ciclo → día → aplicación** muestra qué se realizó y qué continúa pendiente.
Las próximas aplicaciones conservan su propio recorrido independiente.

Si la administración debe detenerse antes del cierre normal, use
**Interrumpir / reacción**. Registre hora, motivo, dosis administrada hasta ese
momento, medidas adoptadas, condición del paciente y destino clínico. La
aplicación queda pausada y no puede completarse como si no hubiera ocurrido el
evento.

Después de la evaluación, resuelva la interrupción con una de estas decisiones:

- **Reanudar administración:** documente la condición y las condiciones de
  reanudación; el sistema vuelve a dejarla en curso sólo si el TTL de la
  preparación continúa vigente.
- **Cerrar sin completar:** documente el motivo, la condición final y la dosis
  total administrada; la aplicación se cierra sin falsearla como completada.

Tanto la interrupción como su resolución generan evoluciones clínicas
inmutables. Si luego se completa la aplicación, el cierre **no reemplaza ni
borra** el evento: conserva el motivo, la dosis parcial, las medidas, la
condición, la resolución y la reacción documentada dentro del historial de esa
misma aplicación.

## Estados visibles

| Etapa | Estados principales | Significado |
|---|---|---|
| Prescripción | confirmada, requerida, solicitada, rechazada | Situación de la orden médica |
| Validación farmacéutica | pendiente, aprobada, rechazada | Auditoría de la orden |
| Reserva | sin reserva, reservada, liberada, consumida | Disponibilidad del stock del centro |
| Triaje | pendiente, PASS, FAIL | Aptitud clínica para esa aplicación |
| Preparación | no iniciada, en preparación, preparada, vencida, liberada, descartada | Estado de la mezcla |
| Administración | no iniciada, en curso, interrumpida, completada, cerrada sin completar | Ejecución real en sala |

La interfaz muestra una acción principal coherente con el estado actual. No se
debe “saltar” de etapa utilizando un formulario alternativo.

## Qué hacer ante excepciones

### Farmacia rechaza la orden

Registre el motivo. La aplicación no avanza hasta que exista una prescripción
corregida y una nueva validación.

### El paciente debía traer la medicación y no la trajo

No marque “la tiene el paciente”. Mantenga la procedencia pendiente y reprograme
si corresponde. La ausencia queda explícita; no se simula disponibilidad.

### Triaje FAIL

Registre motivo y nueva fecha cuando se conozca. El sistema libera la reserva
blanda, desactiva el turno y devuelve la aplicación al circuito de
reprogramación.

### Preparación vencida, dañada o incorrecta

No puede liberarse ni administrarse. Debe descartarse y reiniciarse desde
**Preparación**, dejando el motivo y una nueva trazabilidad completa.
El mismo comando puede usarse aunque el TTL siga vigente cuando hubo error de
preparación, rotura o contaminación. Sólo está disponible antes de comenzar la
administración y siempre exige documentar el motivo; los lotes anteriores
quedan conservados como descartados.

### Reacción durante la administración

Atienda el evento según el protocolo clínico y use **Interrumpir / reacción**
en cuanto sea seguro registrar. No complete la aplicación para retirar la fila
de la cola. Documente dosis parcial, medidas y condición del paciente; luego
elija explícitamente **Reanudar administración** o **Cerrar sin completar**.

## Trazabilidad y seguridad

Cada comando registra:

- usuario y fecha;
- aplicación afectada;
- acción;
- versión anterior y posterior;
- datos clínicos u operativos informados;
- clave de idempotencia.

La versión evita que una pantalla desactualizada pise cambios de otro usuario.
La clave de idempotencia evita duplicar una acción si se repite el envío por un
problema de red.

Las decisiones clínicas y farmacéuticas siguen siendo humanas. El sistema
ordena el recorrido, hace visibles los faltantes y conserva evidencia; no
reemplaza la validación profesional ni el procedimiento institucional.

## Referencias de seguridad

El diseño toma como guía los estándares ASCO/ONS para prescripción,
preparación, doble verificación y administración de antineoplásicos, y el
alcance de USP <800> para el manejo de medicamentos peligrosos:

- https://www.ons.org/ascoons-chemotherapy-administration-safety-standards
- https://www.dev.usp.org/compounding/general-chapter-hazardous-drugs-handling-healthcare

Estas referencias orientan los controles del producto, pero no sustituyen los
procedimientos, habilitaciones ni requisitos regulatorios de cada institución.
