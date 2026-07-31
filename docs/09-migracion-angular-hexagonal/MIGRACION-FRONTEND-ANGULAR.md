# Corte vertical: frontend Angular inicial

Este corte crea el proyecto Angular real en `frontend/`. No sustituye la
interfaz clínica vigente: se publica bajo `/app/` y conserva `/` como la
interfaz anterior durante la convivencia.

## Alcance entregado

El primer recorrido Angular tiene cuatro piezas:

1. `#/login`: formulario reactivo contra `POST /api/auth/login`.
2. guardia de rutas: recupera `GET /api/auth/me`, conserva la cookie HttpOnly y
   redirige a login cuando la sesión no es válida.
3. `#/patients`: búsqueda con `GET /api/clinical/patients` y apertura con
   `POST /api/clinical/patients/{id}/activate`.
4. `#/patients/{id}`: resumen del paciente activo, tratamientos y aplicaciones
   obtenidos de la respuesta de espacio de trabajo existente.
5. `#/patients/{id}/history`: lectura de situación oncológica, hoja clínica y
   evoluciones desde el mismo documento JSON versionado del paciente.
6. `#/patients/{id}/history/edit`: edición reactiva de la hoja clínica. Lee
   el documento activo, conserva sus secciones no editadas y lo guarda por
   `PUT /api/hc`; ante un `409` permite recargar la versión vigente.
7. `#/patients/{id}/diagnosis/new`: agrega, sin reemplazar los anteriores, un
   diagnóstico con sitio AJCC 8, TNM, estadio editable, SNOMED CT y CIE-10.
   El estadio se solicita al servidor mediante `POST /api/ajcc8/stage` y el
   registro queda en el documento clínico versionado.

Cerrar un paciente utiliza `PUT /api/auth/active-patient` con `null`. Por lo
tanto, el contexto pertenece al servidor y la interfaz anterior lo reconoce
sin copias en el navegador.

## Estructura creada

```text
frontend/src/app
├── core
│   ├── api             cliente HTTP y traducción de errores
│   ├── auth            sesión, guardia y modelos de usuario
│   └── patients        contratos TypeScript del recorrido inicial
├── features
│   ├── auth            pantalla de ingreso
│   └── patients        búsqueda y espacio del paciente
└── layout              cabecera y navegación compartida
```

La aplicación usa componentes standalone, TypeScript estricto, formularios
reactivos, Signals para estado local, `HttpClient` con cookies y ruteo por hash.
El hash evita requerir un fallback especial del servidor mientras coexisten
`/` y `/app/`.

## Construcción y publicación

`Dockerfile` agrega una etapa `node:24-alpine` que ejecuta `npm ci` y
`npm run build`. El resultado `dist/frontend/browser` se incorpora a
`src/main/resources/static/app` antes de empaquetar el `.jar`. El adaptador
`AngularApplicationController` reenvía `/app` y `/app/` a ese `index.html`.

La imagen final sigue siendo una sola aplicación Java/PostgreSQL. No se agrega
un servidor Node en producción ni una segunda dirección para la API.

Para desarrollo local con Node compatible:

```powershell
Set-Location frontend
npm ci
npm start
```

Angular 22.1.2 exige Node 22.22.3, 24.15.0 o superior. El Dockerfile usa Node
24 para que la construcción sea reproducible incluso si Windows tiene otra
versión.

## Estado de paridad

Login, sesión, paciente activo, lectura y edición base de hoja clínica y alta
de diagnóstico quedan `En convivencia` en Angular. Esto
significa que el recorrido base funciona con la autoridad actual del servidor,
pero todavía falta demostrar la paridad completa de permisos, recuperación,
errores y apariencia en todas las resoluciones.

No se migraron todavía los estudios, prescripción,
tratamientos, Farmacia, sillones, triaje, preparación ni administración. Esas
capacidades continúan en la interfaz vigente y se abrirán desde el enlace
explícito **Interfaz actual**.

## Próximo corte

El siguiente recorrido debe migrar **estudios y prescripción**. La hoja y el
diagnóstico Angular necesitan aún comparación visual, pruebas E2E repetibles,
permisos por rol y todos los formularios especializados antes de retirar sus
entradas de la pantalla anterior.
