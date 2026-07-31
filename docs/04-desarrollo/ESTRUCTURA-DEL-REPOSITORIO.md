# Estructura del repositorio y ubicación de archivos

Este documento indica dónde se guarda cada parte del producto, qué contiene,
qué se versiona y qué se genera. Las rutas son relativas a la raíz del
repositorio `HCOP_JP`.

## Mapa general

| Ruta | Contenido | Autoridad |
|---|---|---|
| `src/main/java/ar/com/hexium/hcop/` | Backend Java: dominio, casos de uso, controladores, seguridad y adaptadores | Código del servidor |
| `src/main/resources/static/` | Frontend vigente que Spring Boot sirve al navegador | Interfaz actual |
| `frontend/` | Proyecto Angular inicial, publicado bajo `/app/` | Interfaz en convivencia |
| `src/main/resources/db/migration/` | 11 migraciones Flyway ordenadas `V001` a `V011` | Esquema PostgreSQL |
| `src/main/resources/application.yml` | Valores de configuración Spring no secretos | Configuración base |
| `runtime/catalogs/` | Catálogos clínicos distribuidos con la imagen | Datos de referencia |
| `docs/` | Manuales Markdown versionados | Documentación fuente |
| `scripts/` | Pruebas, contratos, documentación e instalación | Automatización |
| `.github/workflows/verify.yml` | Compilación, pruebas, Docker y publicación GHCR | Integración continua |
| `Dockerfile` | Construcción multietapa del `.jar` y la imagen final | Empaquetado |
| `compose.yaml` | Aplicación y PostgreSQL para desarrollo o construcción local | Orquestación local |
| `EJECUTAR-DOCKER-DESDE-GITHUB.ps1` | Lanzador de los canales estable y migración | Ejecución desde GHCR |
| `target/` | Clases y `.jar` generados por Maven; no se versionan | Salida temporal |

## Frontend vigente

La interfaz visible actual está íntegramente en
`src/main/resources/static/`. Spring Boot incorpora esa carpeta al `.jar` y la
sirve desde la misma dirección que la API. No existe otro servidor frontend.

### Aplicación clínica principal

| Archivo | Contenido |
|---|---|
| `static/index.html` | Estructura principal: cabecera, hoja clínica, solapas, modales y Hospital de Día |
| `static/app.js` | Comportamiento de la aplicación clínica, API, estado de paciente, formularios, estudios y flujos |
| `static/styles.css` | Sistema visual general y composición de los paneles |
| `static/care-scheduler.css` | Grilla de sillones, celdas, turnos, lista de espera y estados |
| `static/care-scheduler-modal.css` | Tamaño, distribución y adaptación del modal del turnero |

`app.js` sigue siendo grande porque pertenece al frontend heredado. Durante la
migración no se agregan reglas clínicas nuevas allí: las decisiones permanecen
en Java y cada recorrido se trasladará a `frontend/src/app/features`.

### Centro de Configuración

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

### Administración de protocolos

Todos estos archivos viven en `static/protocol-admin/`:

| Archivo | Contenido |
|---|---|
| `index.html` | Editor completo de protocolo, ciclos, aplicaciones y componentes |
| `protocol-admin.js` | Catálogo, alta, edición, archivo, drogas, preparación y tiempos |
| `protocol-admin.css` | Estilos del editor |
| `scroll-fix.css` | Regla específica de desplazamiento vertical del formulario |
| `help-init.js` | Integración de ayuda |

### Herramientas oncológicas

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
| `static/help/help.js` | Apertura, navegación y acciones de ayuda |
| `static/help/help-content.js` | Textos contextuales mostrados en la interfaz |
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
| `static/__clone/vendor/jsQR.js` | Copia heredada pendiente de retirar al completar la migración |

No se deben guardar pacientes, estudios cargados ni documentos generados
dentro de `static/`. Esos archivos pertenecen al volumen persistente
`/opt/hcop/runtime/storage`.

## Frontend Angular actual y siguiente alcance

La decisión vigente reserva `frontend/` como proyecto Angular independiente:

```text
frontend/src/app
├── core
│   ├── api
│   ├── auth
│   ├── errors
│   └── routing
├── layout
├── shared
│   ├── accessibility
│   ├── forms
│   ├── ui
│   └── utilities
└── features
    ├── patients
    ├── clinical-history
    ├── diagnosis
    ├── studies
    ├── treatments
    ├── pharmacy
    ├── day-hospital
    ├── scheduler
    └── configuration
```

El proyecto ya existe y hoy entrega el primer recorrido completo: inicio de
sesión, consulta y activación de paciente, y cierre del contexto clínico. La
interfaz servida históricamente continúa en `static/` bajo `/`; Angular se
entrega bajo `/app/`.

| Ruta | Contenido actual |
|---|---|
| `frontend/package.json` y `frontend/package-lock.json` | Dependencias reproducibles y órdenes de compilación/prueba de Angular |
| `frontend/angular.json` | Configuración de compilación; publica el frontend bajo `/app/` |
| `frontend/src/main.ts` | Arranque de Angular |
| `frontend/src/index.html` | Documento base y `base href` de la aplicación Angular |
| `frontend/src/styles.scss` | Variables y estilos globales del lenguaje clínico |
| `frontend/src/app/app.ts` y `app.html` | Raíz de la aplicación y carga de la sesión vigente |
| `frontend/src/app/app.config.ts` | Router, HTTP y proveedores globales |
| `frontend/src/app/app.routes.ts` | Rutas protegidas: login y espacio de paciente |
| `frontend/src/app/core/api/` | Cliente HTTP común y normalización del error API |
| `frontend/src/app/core/auth/` | Sesión, inicio/cierre de sesión, paciente activo y guardia de rutas |
| `frontend/src/app/core/patients/` | Tipos de datos que consume el espacio de paciente |
| `frontend/src/app/layout/` | Cabecera y marco clínico reutilizable |
| `frontend/src/app/features/auth/` | Pantalla de acceso |
| `frontend/src/app/features/patients/` | Buscador, apertura y cierre de paciente activo |
| `frontend/src/app/features/clinical-history/` | Lectura Angular de situación oncológica, hoja y evoluciones |
| `frontend/src/app/features/clinical-history-editor/` | Formulario versionado de hoja clínica con control de conflicto |
| `frontend/src/app/features/diagnosis/` | Alta de diagnóstico AJCC 8, TNM, SNOMED CT y CIE-10 |
| `frontend/src/app/features/studies/` | Biblioteca de estudios, filtro, carga múltiple y visor básico |

Durante la convivencia, la interfaz anterior se conservará como `/legacy` antes
de que Angular pase definitivamente a `/`. El estado, autenticación, permisos y
datos continúan siendo responsabilidad del backend Java; Angular no duplica
reglas clínicas ni almacenamiento.

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
| Paquetes anteriores sin estas capas | MVC heredado todavía en convivencia |

Configuración, Protocolos y Guías ya usan la estructura hexagonal. ArchUnit
comprueba la dirección de sus dependencias en cada `mvn verify`.

## Base, catálogos y archivos clínicos

| Ruta o recurso | Contenido |
|---|---|
| `src/main/resources/db/migration/V*.sql` | Tablas, índices, restricciones, seeds y evolución de esquema |
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
creada por usuarios nunca se sube a GitHub.

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
| `test-github-launcher.ps1` | Compatibilidad PowerShell y aislamiento de canales |
| `instalar-desde-github.ps1` | Instalación administrada, preflight y recuperación |

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
