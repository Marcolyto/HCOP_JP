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

- **Nuevo tratamiento**: prescribe para el paciente activo.
- **Farmacia**: registra prescripción y disponibilidad de medicación e imprime
  el QR.
- **Sillones**: agenda ciclos pendientes por duración y evita superposiciones.
  La lupa `+` muestra menos sillones con más ancho y la lupa `-` vuelve a
  mostrar más. Las flechas recorren los sillones que quedan fuera de la vista.
- **Tratamientos**: abre detalle longitudinal, ciclos, aplicaciones y
  documentos. Al seleccionar un ciclo, **Drogas** muestra la composición del
  protocolo prescripto: nombre, dosis, método de cálculo, días, vía y tiempo de
  administración. Los ciclos usan el lenguaje visual de Lira: verde realizado,
  celeste actual, gris pendiente, ámbar parcial y rojo suspendido. La pestaña
  **Aplicaciones** conserva todos los días previstos por el protocolo en un
  árbol vertical alternado y superpone los datos reales sin ocultar las ramas
  que todavía están pendientes.
- **Escanear QR**: identifica el ciclo a administrar y deja trazabilidad.

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
