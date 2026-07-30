# Manual de uso

## Cabecera

- **Nuevo paciente**: abre un formulario limpio y crea una historia vacía.
- **Abrir paciente**: muestra los pacientes recientes y permite filtrar en
  PostgreSQL por nombre, DNI, HC o ID desde el primer carácter.
- **Hospital de día**: abre el espacio operativo global.
- **Imprimir**: imprime la hoja clínica visible.
- **Configuración**: administra protocolos, guías, calculadoras, investigación,
  agenda, diagnósticos, LLM, usuarios y permisos.
- **Ayuda**: abre la documentación funcional.
- **Campana**: muestra solicitudes clínicas asignadas al usuario.
- **Usuario**: informa quién firma las acciones y permite cerrar sesión.

Al abrir un paciente, la ficha queda activa en esa sesión aunque se recargue la
página o se navegue por Configuración. Para dejar la hoja en blanco, use
**Cerrar paciente** (icono de persona con una `X`) a la derecha de la goma de
resaltado. Esta acción no elimina la historia: solamente cierra el contexto
activo y permite volver a abrirlo después.

## Ventanas de carga y confirmación

Los formularios que se abren como ventana, incluido **Nuevo paciente**, se
mantienen abiertos mientras el usuario trabaja. No se cierran por tocar el
fondo, por presionar `Esc` ni por transcurrir tiempo. Para descartarlos use la
`X` visible en la cabecera; **Guardar**, **Confirmar** o **Cancelar** siguen
siendo acciones explícitas y nunca se ejecutan automáticamente.

## Hoja clínica izquierda

Es la historia longitudinal. Contiene identidad, diagnóstico, motivo de
consulta, enfermedad actual, antecedentes, estudios complementarios, examen
físico, tratamientos sistémicos, cirugías, resumen y evoluciones.

**Agregar diagnóstico** exige la clasificación configurada. El estadio AJCC se
calcula desde TNM cuando existe regla; si la combinación no tiene resultado, el
campo queda editable. Los diagnósticos no se pisan: se agregan para conservar la
historia de tumores múltiples.

**Agregar evolución** permite documentar texto clínico. Tratamientos, QR,
suspensiones, continuidad y finalizaciones también agregan evoluciones
automáticas e inmutables.

## Panel derecho

- **Estudios**: carga múltiple, pegar imagen, plantillas y anotaciones.
- **H. de día**: tratamiento y aplicaciones del paciente activo.
- **Prescripción**: medicamentos, certificados y formularios sistémicos.
- **Agente**: asistente LLM opcional; no funciona hasta configurarlo.
- **Investigación**: formularios personalizados.
- **Línea del tiempo**: cronología clínica y resumen asistido.
- **Protocolos**: consulta de esquemas, drogas y duración.
- **Herramientas**: calculadoras y estadificación.

## Hospital de Día global

El botón **Ayuda** de las pantallas de tratamiento y Hospital de día enlaza al
[video detallado del circuito](../../src/main/resources/static/help/media/circuito-hospital-dia-paso-a-paso.mp4).
Sus subtítulos azul intenso, el puntero y los recuadros explican cada control,
incluido cómo encontrar una aplicación en Farmacia, arrastrarla a un sillón,
confirmar, mover o quitar el turno y resolver PASS, postergación, mezcla
vencida, interrupción, reanudación o cierre incompleto. La
[guía del video](VIDEO-CIRCUITO-HOSPITAL-DIA-PASO-A-PASO.md) contiene el
diagrama y el índice completo de alternativas. El
[video breve](../media/demo-flujo-7-pasos/flujo-oncologico-7-pasos.mp4) se
conserva como introducción de 70 segundos.

- **Nuevo tratamiento**: prescribe para el paciente activo. Cada droga debe
  conservar nombre, dosis, unidad explícita, vía y día de aplicación; Farmacia
  no puede aprobar una orden con unidad ausente. Las dosis orales o
  domiciliarias permanecen en el plan terapéutico, pero no generan una
  aplicación, turno ni QR de Hospital de Día.
- **Farmacia**: registra prescripción y disponibilidad de medicación para cada
  ciclo y día de aplicación, e imprime su QR específico. Para stock del centro,
  la reserva exige una correspondencia exacta con todos los componentes
  prescriptos: clave, ID de droga, nombre, dosis y unidad, sin faltantes, extras
  ni duplicados. `sourceItemRef` identifica el componente; si falta se usa una
  clave `<drugId/nombre>-<ordinal>` que también distingue drogas repetidas. La
   modalidad manual documentada registra una constatación física, pero no
   descuenta inventario ni constituye una reserva atómica. Al escribir en el
   buscador de Farmacia, la búsqueda incluye coincidencias fuera de la ventana
   temporal visible. Los filtros separan prescripción, validación, procedencia
   y reserva; **Stock reservado en el centro** cuenta como medicación asegurada
   sin confundirse con “recibida” o “preparada”.
- **Turnos y sala → Agenda**: agenda cada día con medicación como una aplicación
  independiente, usando la duración de las drogas activas y evitando
  superposiciones. Es la única forma de asignar un turno: ya no existe la acción
  separada **Programar ciclo**. La duración reconoce horas escritas como `h`,
  `hs`, `hr`, `hrs`, `hora` o `horas` y minutos como `min`/`minutos`; suma los
   pasos secuenciales y sólo superpone componentes cuando el protocolo declara
   paralelismo o simultaneidad.
   La lupa `+` muestra menos sillones con más ancho y la lupa `-` vuelve a
   mostrar más. Las flechas recorren los sillones que quedan fuera de la vista.
   **Quitar turno** exige un motivo, libera la franja y conserva los estados de
   Farmacia y Administración; toda la acción queda auditada.
- **Triaje**: muestra los turnos de la fecha ordenados por hora y sillón. PASS
  habilita Preparación; FAIL documenta la causa y libera turno y reserva.
- **Preparación**: registra lote, vencimiento, cantidad, unidad, diluyente,
  volumen, concentración y TTL en exactamente una traza por cada componente,
  incluso cuando la misma droga aparece más de una vez. Una mezcla preparada o
   liberada puede descartarse antes de administrar por vencimiento, error,
   rotura o contaminación, siempre con motivo y conservando la traza anterior.
   La traza conserva el `componentKey`; si cantidad y unidad ya vienen de la
   orden, se muestran bloqueadas y el servidor no permite cambiarlas. Las
   mezclas vencidas tienen filtro y estado propios y no aparecen como listas.
- **Turnos y sala → Sala de hoy**: reúne las aplicaciones del día. El filtro
   **Todo el día / En atención / Finalizadas** permite reducir la cola sin
   ocultar los cierres ya realizados. Una administración en curso puede usar
   **Interrumpir / reacción** y luego **Reanudar administración** o **Cerrar sin
   completar**, siempre con dosis parcial, medidas, condición y evolución
   clínica. Reanudar queda bloqueado si venció el TTL de la preparación. Si
   finalmente se completa, el cierre conserva la interrupción, su resolución y
   la reacción.
- **Tratamientos**: abre detalle longitudinal, ciclos, aplicaciones y
  documentos. Al seleccionar un ciclo, **Drogas** muestra la composición del
  protocolo prescripto: nombre, dosis, método de cálculo, días, vía y tiempo de
  administración. Los ciclos usan el lenguaje visual de Lira: verde realizado,
  celeste actual, gris pendiente, ámbar parcial y rojo suspendido. La pestaña
  **Aplicaciones** conserva todos los días previstos por el protocolo en un
  árbol vertical alternado y superpone los datos reales sin ocultar las ramas
  que todavía están pendientes. El turno y el estado de Farmacia aparecen como
  indicadores de sólo lectura: para cambiar esos datos se usan exclusivamente
  las pestañas **Farmacia** y **Sillones**.
- **Escanear QR**: identifica el ciclo y día HDD exactos, deja trazabilidad y
  abre la misma ficha canónica de Administración usada por Sala. No contiene un
  segundo formulario de cierre y no se genera para una pauta exclusivamente
  oral o domiciliaria.

El recorrido operativo es único: **Nuevo tratamiento → Farmacia → Agenda →
Triaje → Preparación → Sala de hoy → Tratamientos**. El detalle final permite
comprobar el plan y lo administrado, pero no abre un segundo programador ni una
segunda vista de Farmacia.

Cuando Preparación o Sala solicita elegir un segundo profesional, la selección
es una declaración auditada del usuario activo. No pide la credencial de esa
segunda persona y, por lo tanto, no constituye una firma o cofirma electrónica.

## Configuración clínica

- **Protocolos** combina los protocolos propios con el catálogo COIR. Los
  registros importados muestran su duración, drogas y preparación disponibles;
  al convertir uno en protocolo propio esos datos se precargan para revisión y
  pueden completarse antes de guardarlo.
- **Plantillas anatómicas** muestra la biblioteca incluida con sus miniaturas,
  licencia y atribución en modo de consulta. Las plantillas propias pueden
  agregarse, editarse o desactivarse.
- **Hospital de día** define sillones, jornada e intervalo de 5, 10, 15, 20 o
  30 minutos. Al guardar, la agenda abierta se recalcula sin perder los turnos
  ya registrados; al volver a entrar conserva el valor en PostgreSQL.

## Estudios

Se admiten imágenes, PDF, Word, PowerPoint y video. Las cargas se almacenan en
el volumen clínico y la historia guarda su referencia. Durante 24 horas y en la
misma sesión, el archivo puede eliminarse con su token temporal.

Un registro histórico rotulado como imagen pero sin archivo se conserva como
metadato y se identifica como **sin archivo**. Las nuevas altas de imagen exigen
un archivo real y nunca simulan una imagen disponible.

Las anotaciones se rasterizan como una imagen nueva; la fuente original no se
modifica silenciosamente.
