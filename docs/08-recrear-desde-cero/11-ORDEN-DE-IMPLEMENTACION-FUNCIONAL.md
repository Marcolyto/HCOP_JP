# 11 · Orden de implementación funcional

Este orden reduce retrabajo y permite tener un producto ejecutable al final de
cada fase. Cada bloque es un corte vertical completo: base, Java, API, interfaz,
Swagger y pruebas.

## Fase 0 · Plataforma

- scaffolding de `backend`, `bff` y `frontend` como tres proyectos
  independientes (ver [01](01-INICIALIZAR-PROYECTO.md));
- Spring Boot, configuración y manejo de errores en `backend`/`bff`;
- PostgreSQL/Flyway en `backend`;
- Actuator en `backend`/`bff`;
- Swagger en `backend`;
- Docker (5 servicios: database/redis/backend/bff/frontend);
- CI mínimo.

Salida: los tres servicios compilan, `backend` migra y responde health a
través de `frontend`→`bff`→`backend`.

## Fase 1 · Identidad y acceso

- usuarios;
- BCrypt;
- sesiones JWT (`backend`) + Token Handler (`bff` + Redis, ver
  [04](04-SEGURIDAD-Y-AUDITORIA.md));
- roles/permisos;
- login/logout/cambio de contraseña/refresh;
- usuario inicial.

Salida: ninguna API clínica funciona sin sesión y permiso.

## Fase 2 · Paciente e historia

- identidad/cobertura;
- búsqueda;
- nuevo paciente;
- paciente activo por sesión;
- documento en blanco;
- guardado con revisión;
- evoluciones append-only.

Salida: dos sesiones pueden abrir pacientes distintos y un conflicto no pisa la
historia.

## Fase 3 · Catálogos y diagnóstico

- SNOMED;
- CIE-10;
- AJCC/TNM;
- cálculo/edición de estadio;
- mapeo entre catálogos;
- diagnósticos múltiples;
- evolución narrativa.

Salida: un diagnóstico completo queda disponible para prescripción sin borrar
anteriores.

## Fase 4 · Configuración clínica

- configuración versionada;
- protocolos;
- componentes/drogas;
- duración y periodicidad;
- guías;
- calculadoras/scores;
- formularios de investigación;
- plantillas anatómicas.

Salida: un administrador crea/edita y un usuario clínico consume una versión
estable.

## Fase 5 · Tratamiento y ciclos

- selección de diagnóstico;
- requisitos;
- antropometría;
- prescripción;
- snapshot de protocolo;
- generación de ciclos;
- logística por ciclo;
- documentos;
- evolución transaccional.

Salida: una prescripción produce tratamiento, detalle, ciclos y evolución o no
produce nada.

## Fase 6 · Farmacia y lista de espera

- prescripción requerida/confirmada;
- medicación pendiente/recibida/en poder del paciente;
- filtros;
- orden por fecha;
- ciclos que reaparecen;
- indicadores faltantes.

Salida: la lista de espera explica por qué un ciclo está o no apto para turnar.

## Fase 7 · Turnero por sillón

- configuración de jornada, sillones y fracción;
- bloques por duración;
- drag/drop;
- mover/cancelar;
- confirmación;
- prevención de superposición en base;
- búsqueda y detalle.

Salida: dos usuarios no pueden reservar el mismo intervalo aunque lo intenten
simultáneamente.

## Fase 8 · Administración y QR

- generación firmada;
- impresión;
- escaneo;
- apertura de turno;
- estados de preparación/administración;
- finalización idempotente;
- evolución/auditoría.

Salida: escanear y finalizar dos veces no duplica el acto clínico.

## Fase 9 · Continuidad y solicitudes

- suspensión transitoria/definitiva;
- motivo y fecha;
- reanudación;
- nueva prescripción si corresponde;
- solicitudes a profesionales;
- bandeja, lectura y resolución;
- eventos inmutables.

Salida: ningún ciclo suspendido aparece como disponible y toda decisión es
trazable.

## Fase 10 · Estudios e imágenes

- carga múltiple;
- pegar portapapeles;
- visor;
- anotación;
- plantillas;
- orden;
- eliminación temporal segura;
- vínculo a evolución.

Salida: archivos privados sobreviven una actualización y no son accesibles sin
permiso.

## Fase 11 · Herramientas e integraciones

- calculadoras configurables;
- investigación;
- línea de tiempo;
- agente LLM opcional;
- endpoint/modelo;
- secreto cifrado;
- degradación segura si LLM no responde.

Salida: el sistema clínico principal funciona con LLM apagado.

## Fase 12 · Endurecimiento

- rendimiento;
- índices;
- accesibilidad;
- pruebas concurrentes;
- backup/restauración;
- HTTPS/VPN;
- observabilidad;
- migración;
- capacitación;
- checklist final.

## Regla entre fases

No avance dejando:

- migraciones rotas;
- endpoints sin permiso;
- estado sólo en navegador;
- documentación atrasada;
- tests deshabilitados;
- datos demo persistentes;
- secretos predeterminados en un entorno real.

El orden puede adaptarse, pero no se debe implementar Turnero antes de tener
tratamiento/ciclos, ni tratamiento antes de identidad, diagnóstico y protocolos.
