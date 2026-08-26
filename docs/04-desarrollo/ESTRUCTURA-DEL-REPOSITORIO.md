# Estructura del repositorio y ubicación de archivos

Este documento indica dónde se guarda cada parte del producto, qué contiene,
qué se versiona y qué se genera. Las rutas son relativas a la raíz del
repositorio `HCOP_JP`.

## Mapa general

Tres servicios Docker independientes, no un monolito (ver
[Arquitectura hexagonal](../02-arquitectura/HEXAGONAL.md)):

| Ruta | Contenido | Autoridad |
|---|---|---|
| `backend/src/main/java/ar/com/hexium/hcop/` | Dominio clínico completo: los ~14 módulos hexagonales (`domain`/`application`/`infrastructure`), más `auth`/`platform` (infraestructura transversal permanentemente exenta) | Código del servidor clínico |
| `backend/src/main/resources/db/migration/` | 14 migraciones Flyway, `V001` a `V014` | Esquema PostgreSQL |
| `backend/src/main/resources/bootstrap/` | Recursos sintéticos y repetibles de arranque | Datos demostrativos sin información real |
| `backend/src/main/resources/application.yml` | Valores de configuración Spring no secretos | Configuración base |
| `backend/runtime/catalogs/` | Catálogos clínicos distribuidos con la imagen | Datos de referencia |
| `bff/src/main/java/ar/com/hexium/hcop/bff/` | Token Handler de la sesión: JWT del backend ↔ cookie opaca del navegador, Redis | Código del BFF |
| `frontend/` | Aplicación Angular standalone, features, pruebas y construcción npm | Único frontend operativo |
| `frontend/nginx.conf` | Único punto público (5180); enruta `/api/`, `/actuator/health`, `/v3/api-docs`, `/swagger-ui*` hacia `bff` | Routing de producción |
| `docs/` | Manuales Markdown versionados | Documentación fuente |
| `scripts/` | Pruebas, contratos, documentación e instalación | Automatización |
| `.github/workflows/verify.yml` | Compilación (3 servicios), Docker (5 servicios), publicación GHCR (3 imágenes) | Integración continua |
| `backend/Dockerfile`, `bff/Dockerfile`, `frontend/Dockerfile` | Construcción multietapa de cada imagen | Empaquetado |
| `compose.yaml` | Los 5 servicios para desarrollo o construcción local | Orquestación local |
| `compose.github.yaml` | Los 5 servicios usando las imágenes publicadas en GHCR | Orquestación GHCR |
| `EJECUTAR-DOCKER-DESDE-GITHUB.ps1` | Lanzador de los canales estable y migración | Ejecución desde GHCR |
| `backend/target/`, `bff/target/`, `frontend/dist/`, `frontend/node_modules/` | Salida temporal de build; no se versionan | Artefactos generados |

## Entrada única de interfaz

nginx (servicio `frontend`) es el único punto público. Sirve el build Angular
estático y enruta la API hacia `bff`. No existe un segundo servidor web
sirviendo HTML, no hay iframe y Angular no ejecuta ningún JavaScript legacy
— el frontend vanilla anterior a la migración Angular (y su copia de
referencia en `legacy-reference/`) ya no forma parte del repositorio.

## Frontend Angular activo

`frontend/` contiene el proyecto Angular que su propio Docker compila:

```text
frontend/src/app
├── core
│   ├── auth
│   ├── clinical
│   ├── highlighting
│   ├── patients
│   └── visual
├── layout
└── features
    ├── agent
    ├── auth
    ├── clinical-entry
    ├── clinical-inbox
    ├── clinical-workspace
    ├── configuration
    ├── day-hospital
    ├── help
    ├── highlighting
    ├── oncology-history-entry
    ├── patients
    ├── prescription
    ├── protocols
    ├── qr
    ├── research
    ├── scheduler
    ├── studies
    ├── study-template-editor
    ├── timeline
    ├── tools
    ├── treatment-documents
    └── treatment-workflow-actions
```

Archivos centrales de este corte:

| Ruta | Contenido |
|---|---|
| `frontend/src/main.ts` | Arranque único de Angular mediante `bootstrapApplication` |
| `frontend/src/app/app.config.ts` | HTTP, router con hash y proveedores globales |
| `frontend/src/app/app.routes.ts` | Login, Configuración, Ayuda, shell clínico y guards |
| `frontend/src/app/layout/` | Cabecera, hoja, divisor y panel derecho que componen el sitio |
| `frontend/src/app/core/auth/` | Sesión, permisos y guard de autenticación |
| `frontend/src/app/core/patients/` | Workspace, borradores, conflictos y paciente activo |
| `frontend/src/app/core/highlighting/` | Persistencia y proyección de resaltados clínicos |
| `frontend/src/app/core/clinical/clinical-treatment-projection.ts` | Proyección, categorización y deduplicación común de tratamientos para hoja y línea temporal |
| `frontend/src/app/core/clinical/clinical-treatment-projection.tests.ts` | Casos de regresión de fuentes relacionales/documentales, identidades, categorías y tombstones |
| `frontend/src/app/core/clinical/clinical-print-projection.ts` | Selección pura de secciones e identidad para impresión clínica |
| `frontend/src/app/core/patients/patient-workspace.normalization.ts` | Normalización compatible de revisión y fecha del workspace |
| `frontend/src/app/core/patients/clinical-save-conflict.ts` | Clasificación de errores y captura profunda de borradores clínicos en conflicto |
| `frontend/src/app/core/patients/clinical-conflict-comparison.ts` | Comparación de sólo lectura y validación de la última revisión sin sustituir el workspace |
| `frontend/src/app/core/patients/clinical-conflict-comparison.tests.ts` | Regresiones de diferencias, aislamiento, identidad y respuestas tardías |
| `frontend/src/app/core/patients/pending-clinical-draft.guard.ts` | Impide abandonar por navegación SPA una ficha con borrador conflictivo pendiente |
| `frontend/scripts/run-clinical-tests.mjs` | Ejecutor multiplataforma de las suites clínicas invocado por `npm test` |
| `frontend/scripts/prepare-visual-contract.mjs` | Copia el contrato visual desde `frontend/src/legacy-visual-contract` a `frontend/src/generated/legacy-visual-contract` antes del build |
| `frontend/e2e/clinical-conflict.spec.ts` | Recorrido Chrome con dos sesiones, borrador `409`, respuesta tardía y verificación final de PostgreSQL |
| `frontend/e2e/playwright.config.ts` | Configuración aislada de Playwright; conserva captura y traza solamente ante fallo |
| `compose.e2e.yaml` | Los 5 servicios, redes y volúmenes descartables para concurrencia clínica |
| `scripts/test-clinical-conflict-e2e.ps1` | Orquesta secretos efímeros, salud, E2E y limpieza incondicional del entorno |
| `frontend/src/app/features/clinical-workspace/` | Hoja clínica Angular y selección de paciente |
| `frontend/src/app/features/clinical-entry/` | Evoluciones y diagnóstico estructurado |
| `frontend/src/app/features/oncology-history-entry/` | Tratamientos históricos, radioterapia y cirugías |
| `frontend/src/app/features/configuration/` | Hub nativo de protocolos, guías, plantillas, calculadoras, LLM y acceso |
| `frontend/src/app/features/day-hospital/` | Tratamientos y circuito operativo por aplicación |
| `frontend/src/app/features/scheduler/` | Turnero por sillón y lista de espera |
| `frontend/src/app/features/treatment-documents/` | Consentimiento, prescripción, hoja, QR y etiqueta trazable |
| `frontend/src/app/features/treatment-workflow-actions/` | Suspensión, continuidad y solicitudes auditables |
| `frontend/src/app/features/studies/` | Estudios, archivos, anotaciones y plantillas anatómicas |
| `frontend/src/app/features/research/` | Formularios configurables de investigación |
| `frontend/src/app/features/clinical-inbox/` | Bandeja de solicitudes clínicas por usuario |
| `frontend/src/app/features/timeline/` | Línea temporal y filtros clínicos |
| `frontend/src/app/features/tools/calculators/` | Catálogo, motor y renderizador de las 57 calculadoras |
| `frontend/src/app/features/help/` | Ayuda contextual nativa |
| `frontend/public/help/media/` | Videos operativos servidos por nginx |
| `frontend/public/docs/` | Catálogo HTML de endpoints, servido bajo `/docs/` |
| `frontend/public/assets/` | Plantillas anatómicas, formularios sistémicos y fuentes |
| `frontend/e2e/` | Recorridos Playwright contra el producto real |

`frontend/Dockerfile` ejecuta `npm ci`, `npm test` y `npm run build`, y sirve
`frontend/dist/` con nginx — la imagen resultante es standalone, no depende
de Java para nada más que hablar HTTP con `bff` en runtime.

## Documentación Markdown

`docs/README.md` es el índice maestro. Cada carpeta tiene una responsabilidad
estable:

| Ruta | Contenido |
|---|---|
| `docs/00-inicio/` | instalación, primer ingreso y prueba del canal publicado |
| `docs/01-uso/` | manual clínico, flujo de tratamiento, roles y videos |
| `docs/02-arquitectura/` | Arquitectura hexagonal, OpenAPI, interoperabilidad y contratos técnicos |
| `docs/03-base-de-datos/` | modelo, diccionario y relación campo → persistencia |
| `docs/04-desarrollo/` | entorno local, estructura, pruebas y reconstrucción |
| `docs/05-operacion/` | Docker, variables, red, actualización, backup y seguridad |
| `docs/06-migracion/` | consolidación histórica desde HCOP/Lira |
| `docs/07-referencia/` | mapa pantalla → API → Java → PostgreSQL y glosario |
| `docs/08-auditoria/` | matrices, resultados reproducibles y evidencias QA |
| `docs/08-recrear-desde-cero/` | guía ordenada para reconstruir el producto (backend/bff/frontend + hexagonal) |
| `docs/09-migracion-bff/` | tracker, decisiones y estado de la migración a BFF + hexagonal (backend) |
| `docs/09-migracion-angular-hexagonal/` | decisiones, cortes y evidencia histórica de la migración a Angular |
| `docs/media/` | videos y otros artefactos documentales versionados |

Los archivos generados `docs/02-arquitectura/ENDPOINTS.md` y
`frontend/public/docs/api-endpoints.html` se reconstruyen juntos con
`scripts/generate-api-docs.ps1`. `docs/02-arquitectura/openapi-snapshot.json`
es el guardián real del contrato (dump normalizado del OpenAPI completo,
incluye schemas) — `ENDPOINTS.md` es una proyección legible pero *lossy*,
sin schemas de request/response; un cambio de contrato puede pasar el check
de `ENDPOINTS.md` y romper igual el snapshot. Los documentos manuales se
editan en Markdown; la versión HTML navegable se publica bajo `/docs/`.

## Backend Java hexagonal

El paquete raíz es `backend/src/main/java/ar/com/hexium/hcop/`.

| Estructura | Uso |
|---|---|
| `<módulo>/domain/` | Entidades, objetos de valor y reglas Java puras — sin Spring, JDBC ni Jackson |
| `<módulo>/application/port/in/` | Casos de uso que invocan los adaptadores de entrada |
| `<módulo>/application/port/out/` | Contratos requeridos a persistencia, archivos o catálogos, y puertos cruzados hacia otros módulos |
| `<módulo>/application/service/` | Coordinación de reglas y puertos (`*ApplicationService`, `*Failure`) |
| `<módulo>/infrastructure/web/` | HTTP, JSON, permisos y códigos de respuesta (`*Controller`, `*JsonMapper`, `*FailureAdvice`) |
| `<módulo>/infrastructure/persistence/` | PostgreSQL o almacenamiento de archivos (`Postgres*Store`) |
| `<módulo>/infrastructure/configuration/` | Composición de beans y transacciones |
| `<módulo>/infrastructure/<otro-módulo>/` | Adapter que implementa un puerto cruzado hacia otro módulo |
| `platform/` | Fusión de lo que era `common`+`config`: infraestructura transversal (bootstrap, manejo de excepciones HTTP) — permanentemente exenta de la regla hexagonal, junto a `auth` |

Los ~14 módulos clínicos (`patient`, `treatment`, `infusion`, `admin`,
`catalog`, `integration`, `media`, `diagnosis`, `workflow`, `qr`, `system`,
`tools`, `guide`, `protocol`, `configuration`) son hexagonales completos.
`auth` y `platform` son los únicos permanentemente exentos.

El arranque demostrativo se coordina en
`patient/infrastructure/bootstrap/DefaultDemoPatientBootstrap.java`. Busca
la clave de seed, crea como máximo una identidad sintética y su documento, y
no modifica sesiones ni pacientes activos. `meta.demoContentVersion`
identifica la versión del recurso; `meta.demoManagedRevision` identifica la
revisión que todavía puede administrar el bootstrap. Una actualización sólo
se aplica si el recurso es más nuevo y la revisión persistida coincide con
esa marca.

El contenido actual es la versión **3** de un caso compuesto de colon y
melanoma creado desde cero. Es el único recurso de paciente demostrativo que
se versiona. El bootstrap es best-effort: las condiciones operativas que
impiden sembrarlo generan una advertencia y no detienen la aplicación.

ArchUnit (`HexagonalArchitectureTest`) comprueba la dirección de las
dependencias en cada `mvn verify` — de forma incondicional para
`domain`/`application` (Spring, JDBC, Jackson) y de forma acotada a los
módulos clínicos para libertad de ciclos entre sí.

## BFF (Token Handler)

`bff/src/main/java/ar/com/hexium/hcop/bff/` — Java 21, sin PostgreSQL:

| Paquete | Uso |
|---|---|
| `auth/` | `BffAuthController`, `BffSession`, `BffSessionService` (Redis), `BackendAuthClient` (login/refresh/logout/me contra el backend) |
| `proxy/` | `ApiProxyController` (streaming genérico), `DocsProxyController` (Swagger/OpenAPI sin sesión), `BackendApiClient` |
| `security/` | `BffSessionFilter` (resuelve sesión una vez por request, refresh transparente), `SessionRequiredFilter` (401 uniforme) |
| `logging/` | `CorrelationIdFilter`, `RequestResponseLoggingFilter` |
| `cache/` | `CacheControlFilter` (`no-store` por default) |
| `health/` | `BackendHealthIndicator` — `/actuator/health` del BFF depende del health real del backend |

## Base, catálogos y archivos clínicos

| Ruta o recurso | Contenido |
|---|---|
| `backend/src/main/resources/db/migration/V*.sql` | 14 versiones (`V001` a `V014`) con tablas, índices, restricciones, seeds y evolución de esquema |
| `backend/src/main/resources/db/migration/V013__jwt_auth.sql` | Sesión JWT: `local_session_state`, `local_refresh_tokens` |
| `backend/src/main/resources/db/migration/V014__drop_local_sessions.sql` | Elimina `local_sessions` (modo cookie retirado) |
| `backend/src/main/resources/bootstrap/patients/test-savatierra-v3.json` | Único recurso de paciente demostrativo versionado: historia ficticia de colon y melanoma, creada desde cero y sin datos reales (`demoContentVersion=3`) |
| `backend/runtime/catalogs/esquemas-coir-419.json` | Catálogo COIR importado |
| `backend/runtime/catalogs/scheme-duration-seed.json` | Duraciones y aplicaciones de esquemas |
| `backend/runtime/catalogs/diagnosis-equivalences.json` | Equivalencias diagnósticas iniciales |
| `backend/runtime/catalogs/hc-oncologica-vacia.json` | Documento clínico inicial |
| `backend/runtime/catalogs/medicamentos-ar-demo.json` | Catálogo demostrativo de medicamentos |
| `backend/runtime/catalogs/seer-rx-regimens.csv` | Regímenes SEER de referencia |
| `backend/runtime/catalogs/vademecum-css-2026-07-11.csv` | Vademécum |
| `backend/runtime/catalogs/systemic-forms*.json` | Definiciones y fondos de formularios sistémicos |
| `backend/runtime/catalogs/guides/*.pdf` | Guías NCCN/blocks por tipo de tumor (el mayor volumen de datos versionados del repo) |
| volumen PostgreSQL | Pacientes, tratamientos, turnos, configuraciones, usuarios y auditoría |
| volumen de almacenamiento (`backend`) | Estudios, imágenes editadas, guías y documentos clínicos |
| Redis (`bff`, efímero) | Sesión opaca del navegador — perderlo obliga a re-login, no pierde datos clínicos |

Los catálogos versionados son semillas o referencias. La información
operativa creada por usuarios nunca se sube a GitHub. El paciente incluido
es una excepción exclusivamente sintética, marcada como demostración y
separada de los datos operativos; nunca se copia aquí una ficha real. Una
versión nueva no sobrescribe una modificación humana: sólo renueva la hoja
que conserva la revisión administrada por el seed.

## Scripts

Todos viven en `scripts/`:

| Archivo | Función |
|---|---|
| `smoke-test.ps1` | Salud, login, estado clínico, Configuración y OpenAPI |
| `integration-test.ps1` | Circuito clínico completo multidroga |
| `nginx-routing-test.ps1` | Redirects, estáticos y rutas del frontend nginx |
| `configuration-contract-test.ps1` | Contrato real del módulo Configuración |
| `protocol-contract-test.ps1` | Contrato real del módulo Protocolos |
| `guide-contract-test.ps1` | Contrato real del módulo Guías y descarga binaria |
| `generate-api-docs.ps1` | Genera o compara Markdown y HTML de endpoints |
| `generate-openapi-snapshot.ps1` | Genera o compara el snapshot completo del OpenAPI (guardián real del contrato) |
| `verify-documentation.ps1` | Enlaces, páginas públicas y calidad OpenAPI |
| `test-github-launcher.ps1` | Compatibilidad PowerShell, aislamiento de canales y accesos administrados de backup/restauración |
| `test-core-browser-e2e.ps1` | Circuitos esenciales de interfaz vía Playwright, contra el stack real (BFF incluido) |
| `test-clinical-conflict-e2e.ps1` | Conflicto de guardado con dos sesiones, entorno efímero |
| `test-backup-restore.ps1` | Ensayo destructivo aislado de copia y recuperación completa |
| `instalar-desde-github.ps1` | Instalación administrada, preflight y recuperación |
| `hcop-data-common.ps1` | Resolución segura del despliegue y operaciones Docker compartidas |
| `backup-hcop.ps1` | Copia consistente de PostgreSQL, storage y manifiesto SHA-256 |
| `restore-hcop.ps1` | Restauración confirmada con backup previo y comprobación de salud |

## Índice de todos los Markdown

### Raíz e inicio

| Archivo | Qué contiene |
|---|---|
| `README.md` | Presentación, ejecución Docker, arquitectura, documentación y evidencia |
| `docs/README.md` | Índice maestro por necesidad |
| `docs/00-inicio/INSTALACION-DESDE-GITHUB.md` | Instalación directa y administrada |
| `docs/00-inicio/PRIMER-INGRESO.md` | Acceso y primeros pasos |
| `docs/00-inicio/PRUEBA-RAMA-ANGULAR-HEXAGONAL.md` | Canal migratorio aislado (histórico) |

### Uso clínico

| Archivo | Qué contiene |
|---|---|
| `docs/01-uso/MANUAL-DE-USO.md` | Uso de cada sección y botón |
| `docs/01-uso/FLUJO-TRATAMIENTO.md` | Relación prescripción, ciclos y aplicaciones |
| `docs/01-uso/CIRCUITO-HOSPITAL-DE-DIA-7-PASOS.md` | Circuito operativo completo |
| `docs/01-uso/GUIA-POR-ROLES-HOSPITAL-DE-DIA.md` | Acciones de oncología, farmacia, admisión y enfermería |
| `docs/01-uso/VIDEO-CIRCUITO-HOSPITAL-DIA-PASO-A-PASO.md` | Capítulos, alternativas y subtítulos del video |

### Arquitectura, datos y desarrollo

| Archivo | Qué contiene |
|---|---|
| `docs/02-arquitectura/HEXAGONAL.md` | Arquitectura vigente y responsabilidades por módulo |
| `docs/02-arquitectura/SWAGGER-OPENAPI.md` | Uso, convenciones y errores de Swagger |
| `docs/02-arquitectura/ENDPOINTS.md` | Catálogo generado de las 114 operaciones |
| `docs/02-arquitectura/openapi-snapshot.json` | Dump completo y normalizado del OpenAPI real — guardián del contrato en CI |
| `docs/02-arquitectura/INTEROPERABILIDAD.md` | Integraciones y contratos externos |
| `docs/03-base-de-datos/MODELO-DE-DATOS.md` | Entidades y relaciones principales |
| `docs/03-base-de-datos/DICCIONARIO-DE-DATOS.md` | Tablas y columnas |
| `docs/03-base-de-datos/CAMPOS-Y-RELACIONES.md` | Origen y recuperación de campos |
| `docs/04-desarrollo/CREAR-DESDE-CERO.md` | Construcción resumida del proyecto |
| `docs/04-desarrollo/ENTORNO-LOCAL.md` | Herramientas y ejecución de desarrollo |
| `docs/04-desarrollo/PRUEBAS.md` | Estrategia y comandos de prueba |
| `docs/04-desarrollo/CONTRATOS-DE-API.md` | Fechas, estados, sesión, errores y concurrencia |
| `docs/04-desarrollo/ESTRUCTURA-DEL-REPOSITORIO.md` | Este mapa de archivos y contenidos |

### Operación, migración y referencia

| Archivo | Qué contiene |
|---|---|
| `docs/05-operacion/DOCKER.md` | Imágenes, contenedores, Compose y canales |
| `docs/05-operacion/ACTUALIZACION.md` | Actualización conservando datos |
| `docs/05-operacion/BACKUP-Y-RESTAURACION.md` | Copias y recuperación |
| `docs/05-operacion/ACCESO-POR-RED.md` | Acceso LAN y puertos |
| `docs/05-operacion/SEGURIDAD.md` | Secretos, sesiones y recomendaciones |
| `docs/05-operacion/VARIABLES-DE-ENTORNO.md` | Variables aceptadas |
| `docs/06-migracion/DESDE-HCOP-LIRA.md` | Antecedentes y migración desde el sistema anterior |
| `docs/07-referencia/MAPA-FUNCIONAL.md` | Pantalla → API → Java → PostgreSQL |
| `docs/07-referencia/GLOSARIO.md` | Términos clínicos y técnicos |

### Auditoría

| Archivo | Qué contiene |
|---|---|
| `docs/08-auditoria/README.md` | Cómo repetir la auditoría |
| `docs/08-auditoria/HOSPITAL-DIA-100-CASOS.md` | Definición de los 100 escenarios |
| `docs/08-auditoria/REPORTE-AUDITORIA-HOSPITAL-DIA-2026-07-30.md` | Hallazgos y correcciones |
| `docs/08-auditoria/resultados/hospital-dia-100-casos-20260730-100711.md` | Resultado reproducible |

### Reconstrucción desde cero

| Archivo | Qué contiene |
|---|---|
| `docs/08-recrear-desde-cero/README.md` | Índice de reconstrucción |
| `00-PRINCIPIOS-Y-ALCANCE.md` | Límites y decisiones iniciales |
| `01-INICIALIZAR-PROYECTO.md` | Creación de los tres proyectos (`backend`/`bff`/`frontend`) |
| `02-ARQUITECTURA-HEXAGONAL.md` | Construcción de la arquitectura hexagonal por módulo |
| `03-POSTGRESQL-Y-FLYWAY.md` | Base y migraciones |
| `04-SEGURIDAD-Y-AUDITORIA.md` | Autenticación JWT vía BFF, permisos y trazabilidad |
| `05-API-Y-SWAGGER.md` | Diseño de la API |
| `06-INTERFAZ-Y-ARCHIVOS.md` | Frontend Angular como servicio propio y almacenamiento |
| `07-PRUEBAS-Y-CALIDAD.md` | Estrategia de calidad, incl. ArchUnit |
| `08-DOCKER-CI-Y-DESPLIEGUE.md` | Empaquetado de los 3 servicios y publicación |
| `09-MIGRACION-Y-PUESTA-EN-MARCHA.md` | Migración operativa |
| `10-CHECKLIST-PRODUCTO-FINAL.md` | Condiciones de aceptación |
| `11-ORDEN-DE-IMPLEMENTACION-FUNCIONAL.md` | Secuencia funcional |
| `PLANTILLA-ADR.md` | Plantilla de decisiones |

Los nombres abreviados de esta tabla corresponden siempre a
`docs/08-recrear-desde-cero/`.

### Migración BFF y hexagonal (backend)

| Archivo | Qué contiene |
|---|---|
| `docs/09-migracion-bff/PROGRESO.md` | Tracker de estado por fase (F0 a F3) |
| `DECISIONES-F2.md` | Decisiones del Token Handler JWT |
| `DECISIONES-F3.md` | Decisiones de la migración hexagonal del backend |

Los nombres abreviados de esta tabla corresponden siempre a
`docs/09-migracion-bff/`.

### Migración Angular y hexagonal (histórico)

| Archivo | Qué contiene |
|---|---|
| `docs/09-migracion-angular-hexagonal/README.md` | Gobierno y ciclo de migración |
| `BASELINE-2026-07-30.md` | Línea base comprobada |
| `MATRIZ-DE-PARIDAD.md` | Estado por capacidad |
| `ARQUITECTURA-OBJETIVO.md` | Módulos Java y estructura Angular objetivo |
| `CONTRATOS-REST.md` | Contratos estables para Angular |
| `REGLAS-ARQUITECTURA.md` | Reglas ArchUnit y límites |
| `MIGRACION-CONFIGURACION.md` | Corte vertical Configuración |
| `MIGRACION-PROTOCOLOS.md` | Corte vertical Protocolos |
| `MIGRACION-GUIAS.md` | Corte vertical Guías |
| `adr/ADR-0001-MONOLITO-MODULAR-HEXAGONAL.md` | Decisión de arquitectura (histórica — el "monolito" describe el backend Java de esa etapa, previo al split en 3 servicios) |
| `adr/ADR-0002-ANGULAR-Y-CONVIVENCIA.md` | Estrategia de reemplazo gradual |
| `adr/ADR-0003-CONTRATOS-DATOS-Y-ROLLBACK.md` | Compatibilidad y reversión |

Los nombres abreviados de esta tabla corresponden siempre a
`docs/09-migracion-angular-hexagonal/`. Es un registro histórico de una
migración ya cerrada — no describe la arquitectura actual (ver
[Arquitectura hexagonal](../02-arquitectura/HEXAGONAL.md) y
`docs/09-migracion-bff/` para eso).

### Material audiovisual

| Archivo | Qué contiene |
|---|---|
| `docs/media/demo-flujo-7-pasos/README.md` | Inventario de videos y subtítulos |
| `docs/media/demo-flujo-7-pasos/capturas/README.md` | Estado de capturas del recorrido |

## Qué se versiona y qué no

Se versionan código, catálogos de referencia, migraciones, recursos
estáticos del frontend, documentación y pruebas. No se versionan:

- `.env` ni claves;
- `backend/target/`, `bff/target/`, `frontend/dist/`, `frontend/node_modules/`,
  `frontend/.angular/` ni dependencias descargadas;
- volúmenes PostgreSQL/Redis;
- pacientes o tratamientos reales;
- estudios, guías y documentos subidos durante el uso;
- registros locales del lanzador.

`scripts/verify-documentation.ps1` recorre todos los Markdown y falla si un
enlace local no existe. Este archivo debe actualizarse cuando se crea, mueve
o retira un módulo relevante.
