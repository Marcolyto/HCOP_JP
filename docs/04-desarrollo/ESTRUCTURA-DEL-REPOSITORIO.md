# Estructura del repositorio y ubicación de archivos

Este documento indica dónde se guarda cada parte del producto, qué contiene,
qué se versiona y qué se genera. Las rutas son relativas a la raíz del
repositorio `HCOP_JP`.

## Mapa general

| Ruta | Contenido | Autoridad |
|---|---|---|
| `src/main/java/ar/com/hexium/hcop/` | Backend Java: dominio, casos de uso, controladores, seguridad y adaptadores | Código del servidor |
| `src/main/resources/static/` | Activos visuales, ayuda, documentación HTML y fuentes históricas no ejecutadas | Recursos empacados por Spring Boot |
| `frontend/` | Aplicación Angular standalone, features, pruebas y construcción npm | Único frontend operativo |
| `src/main/resources/db/migration/` | 12 migraciones Flyway ordenadas `V001` a `V012` | Esquema PostgreSQL |
| `src/main/resources/bootstrap/` | Recursos sintéticos y repetibles de arranque | Datos demostrativos sin información real |
| `src/main/resources/application.yml` | Valores de configuración Spring no secretos | Configuración base |
| `runtime/catalogs/` | Catálogos clínicos distribuidos con la imagen | Datos de referencia |
| `docs/` | Manuales Markdown versionados | Documentación fuente |
| `scripts/` | Pruebas, contratos, documentación e instalación | Automatización |
| `.github/workflows/verify.yml` | Compilación, pruebas, Docker y publicación GHCR | Integración continua |
| `Dockerfile` | Construcción multietapa del `.jar` y la imagen final | Empaquetado |
| `compose.yaml` | Aplicación y PostgreSQL para desarrollo o construcción local | Orquestación local |
| `EJECUTAR-DOCKER-DESDE-GITHUB.ps1` | Lanzador de los canales estable y migración | Ejecución desde GHCR |
| `target/` | Clases y `.jar` generados por Maven; no se versionan | Salida temporal |

## Entrada única de interfaz

Spring Boot sirve la API y un único frontend desde el mismo proceso. El build
Angular vive internamente en `/app/`; `/`, `/index.html` y los aliases de
Configuración, Protocolos y Herramientas redirigen hacia él. No existe un
segundo servidor web, no hay iframe y Angular no ejecuta `static/app.js`.

Angular puede reutilizar CSS, imágenes, videos o fuentes de `static/` durante
la consolidación visual. Eso no convierte al JavaScript anterior en una
dependencia de ejecución. El contrato exacto de rutas está en
[Corte final de entrada Angular](../09-migracion-angular-hexagonal/CORTE-FINAL-ENTRADA-ANGULAR.md).

### Fuentes históricas de la aplicación clínica

| Archivo | Contenido |
|---|---|
| `static/index.html` | Referencia de la estructura previa; `/index.html` redirige a Angular |
| `static/app.js` | Implementación histórica utilizada para comparar paridad; no se ejecuta |
| `static/styles.css` | Sistema visual general y composición de los paneles |
| `static/care-scheduler.css` | Grilla de sillones, celdas, turnos, lista de espera y estados |
| `static/care-scheduler-modal.css` | Tamaño, distribución y adaptación del modal del turnero |

`app.js` se conserva sólo como evidencia de comparación. No deben agregarse
reglas ni correcciones nuevas allí: las decisiones clínicas pertenecen a Java y
la interacción de usuario a `frontend/src/app/features`.

### Referencias históricas de Configuración

Todos estos archivos viven en `static/configuration/`:

| Archivo | Contenido |
|---|---|
| `index.html` | Pantallas de usuarios, protocolos, guías, calculadoras, formularios, plantillas y Hospital de Día |
| `configuration.js` | Navegación, formularios, llamadas REST y editores de configuración |
| `configuration.css` | Estilos base del centro |
| `configuration-overrides.css` | Ajustes visuales posteriores y correcciones de scroll/distribución |
| `calculator-builder.js` | Constructor no programático de scores y calculadoras |
| `calculator-engine.js` | Evaluación de una definición de calculadora |
| `expression-engine.js` | Expresiones permitidas, variables y operaciones seguras |
| `help-init.js` | Integración del módulo común de ayuda |

### Referencia histórica del administrador de protocolos

Todos estos archivos viven en `static/protocol-admin/`:

| Archivo | Contenido |
|---|---|
| `index.html` | Editor completo de protocolo, ciclos, aplicaciones y componentes |
| `protocol-admin.js` | Catálogo, alta, edición, archivo, drogas, preparación y tiempos |
| `protocol-admin.css` | Estilos del editor |
| `scroll-fix.css` | Regla específica de desplazamiento vertical del formulario |
| `help-init.js` | Integración de ayuda |

### Referencias históricas de Herramientas

La aplicación de herramientas vive en `static/herramientas/`:

| Ruta o archivo | Contenido |
|---|---|
| `index.html` | Índice y contenedor de calculadoras y estadificación |
| `css/styles.css` | Estilos propios |
| `js/app.js` | Navegación y ejecución general |
| `js/clinical-rules.js` | Reglas clínicas compartidas |
| `js/oncology-rules-general.js` | Reglas oncológicas generales |
| `js/oncology-rules-gi-thorax.js` | Reglas gastrointestinales y de tórax |
| `js/oncology-rules-gyne.js` | Reglas ginecológicas |
| `js/oncology-tools-general.js` | Herramientas generales |
| `js/oncology-tools-gi-thorax.js` | Herramientas gastrointestinales y de tórax |
| `js/oncology-tools-gyne.js` | Herramientas ginecológicas |
| `js/radiotherapy-rules.js` | Reglas de radioterapia |
| `js/radiotherapy-tools.js` | Herramientas de radioterapia |
| `js/help-init.js` | Integración de ayuda |

Las 20 páginas autocontenidas están en `static/herramientas/pages/`:

| Archivos | Área |
|---|---|
| `01-ecog-karnofsky.html` a `03-g8-carg.html` | Performance, comorbilidad y valoración geriátrica |
| `04-ipss-epic-shim.html` a `12-chaarted-latitude.html` | Próstata |
| `13-eau-nmibc-eortc-cueto.html` a `16-utuc-eau-riesgo.html` | Vejiga y urotelio |
| `17-renal-padua.html` a `19-imdc-heng-mskcc-motzer.html` | Riñón |
| `20-igcccg.html` | Tumores germinales |

### Ayuda y documentación navegable

| Ruta | Contenido |
|---|---|
| `static/help/help.js` | Referencia histórica; Maven la excluye del producto |
| `static/help/help-content.js` | Referencia histórica; la ayuda activa vive en Angular |
| `static/help/help.css` | Apariencia del centro de ayuda |
| `static/help/media/*.mp4` | Videos operativos incluidos en el producto |
| `static/docs/index.html` | Índice navegable |
| `static/docs/manual-usuario.html` | Manual clínico en HTML |
| `static/docs/referencia-tecnica.html` | Referencia para desarrollo y datos |
| `static/docs/api-endpoints.html` | Catálogo HTML generado desde OpenAPI |
| `static/docs/consolidacion-lira-hdd.html` | Antecedente de consolidación |
| `static/docs/documentacion.css` | Estilos de documentación |
| `static/docs/documentacion.js` | Navegación de documentación |

`static/docs/api-endpoints.html` y
`docs/02-arquitectura/ENDPOINTS.md` forman un par generado por
`scripts/generate-api-docs.ps1`; no deben editarse por separado.

### Recursos visuales y dependencias

| Ruta | Contenido |
|---|---|
| `static/assets/study-templates/` | 333 imágenes y metadatos de plantillas anatómicas |
| `static/assets/systemic-forms/` | 9 fondos rasterizados de formularios sistémicos |
| `static/assets/systemic-fonts/` | Fuentes incorporadas para completar formularios |
| `static/formulariosos/` | 5 PDF originales de formularios de referencia |
| `static/vendor/lucide.min.js` | Iconos Lucide incluidos localmente |
| `static/vendor/jsQR.js` | Lectura de QR en el navegador |
| `static/__clone/vendor/jsQR.js` | Copia histórica excluida del artefacto ejecutable |

No se deben guardar pacientes, estudios cargados ni documentos generados
dentro de `static/`. Esos archivos pertenecen al volumen persistente
`/opt/hcop/runtime/storage`.

## Frontend Angular activo

`frontend/` contiene el proyecto Angular que Docker compila antes del JAR:

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
| `frontend/e2e/clinical-conflict.spec.ts` | Recorrido Chrome con dos sesiones, borrador `409`, respuesta tardía y verificación final de PostgreSQL |
| `frontend/e2e/playwright.config.ts` | Configuración aislada de Playwright; conserva captura y traza solamente ante fallo |
| `compose.e2e.yaml` | Aplicación, PostgreSQL, redes y volúmenes descartables para concurrencia clínica |
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
| `frontend/scripts/run-clinical-tests.mjs` | Ejecutor de pruebas puras del frontend |
| `frontend/e2e/` | Recorridos Playwright contra el producto real |

El Dockerfile ejecuta `npm test` y `npm run build`, y copia
`frontend/dist/hcop-jp-angular/browser` a `static/app` dentro del JAR. Angular
se sirve internamente bajo `/app/`; `WebConfiguration` convierte la raíz y los
aliases históricos en entradas al mismo frontend.

## Documentación Markdown

`docs/README.md` es el índice maestro. Cada carpeta tiene una responsabilidad
estable:

| Ruta | Contenido |
|---|---|
| `docs/00-inicio/` | instalación, primer ingreso y prueba del canal publicado |
| `docs/01-uso/` | manual clínico, flujo de tratamiento, roles y videos |
| `docs/02-arquitectura/` | MVC, OpenAPI, interoperabilidad y contratos técnicos |
| `docs/03-base-de-datos/` | modelo, diccionario y relación campo → persistencia |
| `docs/04-desarrollo/` | entorno local, estructura, pruebas y reconstrucción |
| `docs/05-operacion/` | Docker, variables, red, actualización, backup y seguridad |
| `docs/06-migracion/` | consolidación histórica desde HCOP/Lira |
| `docs/07-referencia/` | mapa pantalla → API → Java → PostgreSQL y glosario |
| `docs/08-auditoria/` | matrices, resultados reproducibles y evidencias QA |
| `docs/08-recrear-desde-cero/` | guía ordenada para reconstruir el producto |
| `docs/09-migracion-angular-hexagonal/` | decisiones, cortes y evidencia histórica de la migración |
| `docs/media/` | videos y otros artefactos documentales versionados |

Los archivos generados `docs/02-arquitectura/ENDPOINTS.md` y
`src/main/resources/static/docs/api-endpoints.html` se reconstruyen juntos con
`scripts/generate-api-docs.ps1`. Los documentos manuales se editan en Markdown;
la versión HTML navegable se publica bajo `/docs/`.

## Backend Java

El paquete raíz es `src/main/java/ar/com/hexium/hcop/`.

| Estructura | Uso |
|---|---|
| `<módulo>/domain/` | Entidades, objetos de valor y reglas Java puras |
| `<módulo>/application/port/in/` | Casos de uso que invocan los adaptadores de entrada |
| `<módulo>/application/port/out/` | Contratos requeridos a persistencia, archivos o catálogos |
| `<módulo>/application/service/` | Coordinación de reglas y puertos |
| `<módulo>/infrastructure/web/` | HTTP, JSON, permisos y códigos de respuesta |
| `<módulo>/infrastructure/persistence/` | PostgreSQL o almacenamiento de archivos |
| `<módulo>/infrastructure/configuration/` | Composición de beans y transacciones |
| `sharedkernel/domain/` | Identificadores compartidos mínimos |
| Paquetes sin estas capas completas | Módulos Spring MVC que conservan el mismo contrato mientras se robustecen |

El arranque demostrativo se coordina en
`patient/DefaultDemoPatientBootstrap.java`. Busca la clave de seed, crea como
máximo una identidad sintética y su documento, y no modifica sesiones ni
pacientes activos. `meta.demoContentVersion` identifica la versión del recurso;
`meta.demoManagedRevision` identifica la revisión que todavía puede administrar
el bootstrap. Una actualización sólo se aplica si el recurso es más nuevo y la
revisión persistida coincide con esa marca.

El contenido actual es la versión **3** de un caso compuesto de colon y melanoma
creado desde cero. Es el único recurso de paciente demostrativo que se versiona.
El bootstrap es best-effort: las condiciones operativas que impiden sembrarlo
generan una advertencia y no detienen la aplicación.

Configuración, Protocolos y Guías ya usan la estructura hexagonal. ArchUnit
comprueba la dirección de sus dependencias en cada `mvn verify`.

## Base, catálogos y archivos clínicos

| Ruta o recurso | Contenido |
|---|---|
| `src/main/resources/db/migration/V*.sql` | 12 versiones (`V001` a `V012`) con tablas, índices, restricciones, seeds y evolución de esquema |
| `src/main/resources/db/migration/V012__patient_seed_identity.sql` | Índice único parcial que impide repetir una `identity_json.seedKey` no vacía |
| `src/main/resources/bootstrap/patients/test-savatierra-v3.json` | Único recurso de paciente demostrativo versionado: historia ficticia de colon y melanoma, creada desde cero y sin datos reales (`demoContentVersion=3`) |
| `runtime/catalogs/esquemas-coir-419.json` | Catálogo COIR importado |
| `runtime/catalogs/scheme-duration-seed.json` | Duraciones y aplicaciones de esquemas |
| `runtime/catalogs/diagnosis-equivalences.json` | Equivalencias diagnósticas iniciales |
| `runtime/catalogs/hc-oncologica-vacia.json` | Documento clínico inicial |
| `runtime/catalogs/medicamentos-ar-demo.json` | Catálogo demostrativo de medicamentos |
| `runtime/catalogs/seer-rx-regimens.csv` | Regímenes SEER de referencia |
| `runtime/catalogs/vademecum-css-2026-07-11.csv` | Vademécum |
| `runtime/catalogs/systemic-forms*.json` | Definiciones y fondos de formularios sistémicos |
| volumen PostgreSQL | Pacientes, tratamientos, turnos, configuraciones, usuarios y auditoría |
| volumen de almacenamiento | Estudios, imágenes editadas, guías y documentos clínicos |

Los catálogos versionados son semillas o referencias. La información operativa
creada por usuarios nunca se sube a GitHub. El paciente incluido es una
excepción exclusivamente sintética, marcada como demostración y separada de
los datos operativos; nunca se copia aquí una ficha real. Una versión nueva no
sobrescribe una modificación humana: sólo renueva la hoja que conserva la
revisión administrada por el seed.

## Scripts

Todos viven en `scripts/`:

| Archivo | Función |
|---|---|
| `smoke-test.ps1` | Salud, login, estado clínico, Configuración y OpenAPI |
| `integration-test.ps1` | Circuito clínico completo multidroga |
| `configuration-contract-test.ps1` | Contrato real del módulo Configuración |
| `protocol-contract-test.ps1` | Contrato real del módulo Protocolos |
| `guide-contract-test.ps1` | Contrato real del módulo Guías y descarga binaria |
| `generate-api-docs.ps1` | Genera o compara Markdown y HTML de endpoints |
| `verify-documentation.ps1` | Enlaces, páginas públicas y calidad OpenAPI |
| `test-github-launcher.ps1` | Compatibilidad PowerShell, aislamiento de canales y accesos administrados de backup/restauración |
| `instalar-desde-github.ps1` | Instalación administrada, preflight y recuperación |
| `hcop-data-common.ps1` | Resolución segura del despliegue y operaciones Docker compartidas |
| `backup-hcop.ps1` | Copia consistente de PostgreSQL, storage y manifiesto SHA-256 |
| `restore-hcop.ps1` | Restauración confirmada con backup previo y comprobación de salud |
| `test-backup-restore.ps1` | Ensayo destructivo aislado de copia y recuperación completa |

## Índice de todos los Markdown

### Raíz e inicio

| Archivo | Qué contiene |
|---|---|
| `README.md` | Presentación, ejecución Docker, arquitectura, documentación y evidencia |
| `docs/README.md` | Índice maestro por necesidad |
| `docs/00-inicio/INSTALACION-DESDE-GITHUB.md` | Instalación directa y administrada |
| `docs/00-inicio/PRIMER-INGRESO.md` | Acceso y primeros pasos |
| `docs/00-inicio/PRUEBA-RAMA-ANGULAR-HEXAGONAL.md` | Canal migratorio aislado |

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
| `docs/02-arquitectura/MVC.md` | Arquitectura vigente anterior y responsabilidades |
| `docs/02-arquitectura/SWAGGER-OPENAPI.md` | Uso, convenciones y errores de Swagger |
| `docs/02-arquitectura/ENDPOINTS.md` | Catálogo generado de los 111 endpoints |
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
| `01-INICIALIZAR-PROYECTO.md` | Creación del proyecto |
| `02-ARQUITECTURA-MVC.md` | Construcción de la base MVC original |
| `03-POSTGRESQL-Y-FLYWAY.md` | Base y migraciones |
| `04-SEGURIDAD-Y-AUDITORIA.md` | Autenticación, permisos y trazabilidad |
| `05-API-Y-SWAGGER.md` | Diseño de la API |
| `06-INTERFAZ-Y-ARCHIVOS.md` | Integración del frontend y almacenamiento |
| `07-PRUEBAS-Y-CALIDAD.md` | Estrategia de calidad |
| `08-DOCKER-CI-Y-DESPLIEGUE.md` | Empaquetado y publicación |
| `09-MIGRACION-Y-PUESTA-EN-MARCHA.md` | Migración operativa |
| `10-CHECKLIST-PRODUCTO-FINAL.md` | Condiciones de aceptación |
| `11-ORDEN-DE-IMPLEMENTACION-FUNCIONAL.md` | Secuencia funcional |
| `PLANTILLA-ADR.md` | Plantilla de decisiones |

Los nombres abreviados de esta tabla corresponden siempre a
`docs/08-recrear-desde-cero/`.

### Migración Angular y hexagonal

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
| `adr/ADR-0001-MONOLITO-MODULAR-HEXAGONAL.md` | Decisión de arquitectura |
| `adr/ADR-0002-ANGULAR-Y-CONVIVENCIA.md` | Estrategia de reemplazo gradual |
| `adr/ADR-0003-CONTRATOS-DATOS-Y-ROLLBACK.md` | Compatibilidad y reversión |

Los nombres abreviados de esta tabla corresponden siempre a
`docs/09-migracion-angular-hexagonal/`.

### Material audiovisual

| Archivo | Qué contiene |
|---|---|
| `docs/media/demo-flujo-7-pasos/README.md` | Inventario de videos y subtítulos |
| `docs/media/demo-flujo-7-pasos/capturas/README.md` | Estado de capturas del recorrido |

## Qué se versiona y qué no

Se versionan código, catálogos de referencia, migraciones, recursos estáticos,
documentación y pruebas. No se versionan:

- `.env` ni claves;
- `target/`, dependencias descargadas o builds intermedios;
- volúmenes PostgreSQL;
- pacientes o tratamientos reales;
- estudios, guías y documentos subidos durante el uso;
- registros locales del lanzador.

`scripts/verify-documentation.ps1` recorre todos los Markdown y falla si un
enlace local no existe. Este archivo debe actualizarse cuando se crea, mueve o
retira un módulo relevante.
