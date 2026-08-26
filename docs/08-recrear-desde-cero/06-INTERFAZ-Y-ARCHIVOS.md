# 06 · Integrar interfaz y archivos

## Un servicio propio, un mismo origen público

Angular vive en `frontend/`, es un servicio Docker propio (`frontend/
Dockerfile` multi-stage: build Angular → runtime nginx), no un recurso
empaquetado dentro del `.jar` de Java:

```text
frontend/
  src/app/
    features/<dominio>/   ← un directorio por área clínica
  public/
    assets/
    help/
  nginx.conf               ← único lugar que sabe que el backend real se
                              llama "bff" en la red Docker
```

nginx expone un único puerto público (5180) y enruta `/api/`,
`/actuator/health`, `/v3/api-docs`, `/swagger-ui*` y `/webjars/` hacia el
BFF. Esto conserva "mismo origen" desde el navegador (sin CORS, sesión
simple) sin que Angular y Java compartan proceso — evita que HCOP y
Hospital de Día parezcan sistemas pegados, y de paso permite escalar,
buildear y desplegar el frontend sin recompilar Java.

## Estado de la interfaz

La UI puede mantener estado transitorio para editar, filtrar o arrastrar, pero
la fuente clínica sigue en el servidor.

No usar `localStorage` para:

- pacientes;
- evoluciones;
- tratamientos;
- turnos;
- roles;
- confirmaciones clínicas.

Puede usarse para preferencias no clínicas y reversibles, como tamaño visual de
paneles, si no afecta seguridad ni integridad.

## Carga y persistencia

Para cada pantalla:

1. definir estado cargando/vacío/error/listo;
2. leer la API;
3. normalizar la respuesta;
4. renderizar;
5. enviar cambios con revisión;
6. manejar `401`, `403`, `404` y `409`;
7. releer después de una transición crítica.

No “asuma” éxito y pinte la UI antes de que el servidor confirme, especialmente
en turnos y administración de medicación.

## Accesibilidad

- HTML semántico;
- `label` asociado a cada input;
- teclado en tabs, modales y tablas;
- foco dentro del modal y devolución al cerrar;
- cierre de modales solamente por controles explícitos: nunca por clic en el
  fondo, tecla `Esc` o temporizador;
- `aria-live` para estados;
- contraste suficiente;
- iconos con nombre accesible;
- no comunicar un estado sólo con color.

## Diseño responsivo

Defina desde el comienzo:

- anchos mínimos de tablas;
- colapso/scroll en un único contenedor;
- modales grandes sin doble scroll;
- zoom/control de columnas para el turnero;
- tamaños homogéneos;
- estados vacíos accionables;
- no solapar controles con divisores.

Pruebe al menos escritorio habitual, ventana angosta y escalado del sistema al
125/150 %.

## Estudios y archivos

Flujo recomendado:

1. seleccionar, arrastrar o pegar;
2. validar en navegador para feedback rápido;
3. enviar multipart;
4. validar nuevamente en servidor;
5. persistir binario y metadata/hash;
6. agregar referencia a la hoja clínica;
7. mostrar visor y acciones autorizadas.

Si falla el guardado de la hoja después de subir un archivo, defina una
compensación: eliminar el huérfano o mantenerlo pendiente con limpieza segura.

## Editor de imágenes

Ediciones como texto, trazo, flecha, círculo, rectángulo y goma se componen en
canvas y se guardan como una nueva imagen rasterizada. Conserve:

- original;
- derivado;
- autor/fecha;
- vínculo entre ambos;
- formato y dimensiones;
- hash.

No sobrescriba silenciosamente el original clínico.

## Turnero visual

La grilla es una representación de intervalos, no la regla de integridad.

- cada bloque conoce inicio y duración;
- el backend calcula fin;
- la UI consulta/deriva espacios posibles;
- al soltar envía sillón, inicio, duración y versión;
- ante `409` relee toda la fecha;
- mover usa la misma validación que crear;
- cancelar libera visualmente después de confirmación.

## Separación de responsabilidades

Use la estructura de features de Angular: un módulo/feature por dominio
clínico, servicios inyectables para el cliente HTTP y el estado, componentes
enfocados en presentación. Evite un único componente creciente sin límites
— la migración real de este proyecto pasó por exactamente ese problema
(una SPA sin framework, un `app.js` de miles de líneas) antes de moverse a
Angular nativo por feature.

## Hito de aceptación

Cada sección funciona al recargar el navegador, en una segunda sesión y después
de un conflicto. La consola no presenta errores y no existe scroll duplicado en
la vista principal, modales ni visor.
