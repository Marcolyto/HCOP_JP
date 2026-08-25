# Documentación completa de HCOP JP

Este es el índice maestro del producto. La documentación se mantiene junto al
código y describe la aplicación, la API, PostgreSQL, Docker, seguridad,
operación y mantenimiento. Está organizada por recorridos clínicos, operativos
y técnicos para localizar rápidamente la referencia correspondiente.

La entrada operativa es `http://localhost:<puerto>/`: sirve el frontend Angular
nativo sin iframe ni runtime JavaScript legacy. El mismo proceso Java 21/Spring
MVC publica la API y Swagger; PostgreSQL conserva los datos y Flyway versiona
el esquema.

## Elegir un recorrido

| Necesidad | Empezar por |
|---|---|
| Usar HCOP JP en atención clínica | [Manual de uso](01-uso/MANUAL-DE-USO.md) |
| Instalarlo en una PC | [Instalación desde GitHub](00-inicio/INSTALACION-DESDE-GITHUB.md) |
| Probar la rama Angular/hexagonal sin tocar la estable | [Canal Docker de migración](00-inicio/PRUEBA-RAMA-ANGULAR-HEXAGONAL.md) |
| Entender el circuito de tratamiento | [Flujo de tratamiento](01-uso/FLUJO-TRATAMIENTO.md) |
| Operar Hospital de día paso a paso | [Video detallado y guía de capítulos](01-uso/VIDEO-CIRCUITO-HOSPITAL-DIA-PASO-A-PASO.md), [circuito de 7 pasos](01-uso/CIRCUITO-HOSPITAL-DE-DIA-7-PASOS.md) y [guía por roles](01-uso/GUIA-POR-ROLES-HOSPITAL-DE-DIA.md) |
| Revisar la auditoría de Hospital de día | [Reporte del 30/07/2026](08-auditoria/REPORTE-AUDITORIA-HOSPITAL-DIA-2026-07-30.md) y [matriz de 100 casos](08-auditoria/HOSPITAL-DIA-100-CASOS.md) |
| Integrarse con la API | [Swagger / OpenAPI](02-arquitectura/SWAGGER-OPENAPI.md) y [endpoints](02-arquitectura/ENDPOINTS.md) |
| Mantener el código | [Arquitectura MVC](02-arquitectura/MVC.md) y [mapa funcional](07-referencia/MAPA-FUNCIONAL.md) |
| Encontrar un archivo o carpeta | [Estructura del repositorio](04-desarrollo/ESTRUCTURA-DEL-REPOSITORIO.md) |
| Entender o respaldar PostgreSQL | [Modelo](03-base-de-datos/MODELO-DE-DATOS.md) y [diccionario](03-base-de-datos/DICCIONARIO-DE-DATOS.md) |
| Configurar un servidor | [Variables de entorno](05-operacion/VARIABLES-DE-ENTORNO.md) y [seguridad](05-operacion/SEGURIDAD.md) |

Con el sistema iniciado también existe una versión navegable en
`http://localhost:5180/docs/`.

## 00 · Empezar

- [Instalación desde GitHub](00-inicio/INSTALACION-DESDE-GITHUB.md)
- [Prueba aislada de la rama Angular/hexagonal](00-inicio/PRUEBA-RAMA-ANGULAR-HEXAGONAL.md)
- [Primer ingreso](00-inicio/PRIMER-INGRESO.md)

## 01 · Uso clínico

- [Manual de uso](01-uso/MANUAL-DE-USO.md)
- [Flujo de tratamiento y Hospital de Día](01-uso/FLUJO-TRATAMIENTO.md)
- [Circuito de Hospital de día en 7 pasos](01-uso/CIRCUITO-HOSPITAL-DE-DIA-7-PASOS.md)
- [Guía operativa por roles](01-uso/GUIA-POR-ROLES-HOSPITAL-DE-DIA.md)
- [Video detallado, capítulos, alternativas y diagrama](01-uso/VIDEO-CIRCUITO-HOSPITAL-DIA-PASO-A-PASO.md)
- [MP4 detallado con subtítulos azul intenso](../src/main/resources/static/help/media/circuito-hospital-dia-paso-a-paso.mp4)
- [Video resumen de 70 segundos](media/demo-flujo-7-pasos/flujo-oncologico-7-pasos.mp4)

## 02 · Arquitectura

- [MVC y módulos](02-arquitectura/MVC.md)
- [Swagger / OpenAPI](02-arquitectura/SWAGGER-OPENAPI.md)
- [Catálogo completo de endpoints](02-arquitectura/ENDPOINTS.md)
- [Contratos y convenciones de API](04-desarrollo/CONTRATOS-DE-API.md)
- [Interoperabilidad](02-arquitectura/INTEROPERABILIDAD.md)
- [Entrada única Angular y aliases](09-migracion-angular-hexagonal/CORTE-FINAL-ENTRADA-ANGULAR.md)

## 03 · Base de datos

- [Modelo de datos](03-base-de-datos/MODELO-DE-DATOS.md)
- [Diccionario de las 34 tablas](03-base-de-datos/DICCIONARIO-DE-DATOS.md)
- [Origen y recuperación de campos](03-base-de-datos/CAMPOS-Y-RELACIONES.md)

## 04 · Desarrollo

- [Crear desde cero](04-desarrollo/CREAR-DESDE-CERO.md)
- [Estructura del repositorio y ubicación de cada archivo](04-desarrollo/ESTRUCTURA-DEL-REPOSITORIO.md)
- [Entorno local](04-desarrollo/ENTORNO-LOCAL.md)
- [Pruebas](04-desarrollo/PRUEBAS.md)
- [Contratos y convenciones de API](04-desarrollo/CONTRATOS-DE-API.md)

## 05 · Operación

- [Docker](05-operacion/DOCKER.md)
- [Actualización](05-operacion/ACTUALIZACION.md)
- [Backup y restauración](05-operacion/BACKUP-Y-RESTAURACION.md)
- [Acceso por red](05-operacion/ACCESO-POR-RED.md)
- [Seguridad](05-operacion/SEGURIDAD.md)
- [Variables de entorno](05-operacion/VARIABLES-DE-ENTORNO.md)

## 06 · Migración

- [Decisiones y migración del sistema anterior](06-migracion/DESDE-HCOP-LIRA.md)

## 07 · Referencia cruzada

- [Mapa funcional: pantalla → API → Java → PostgreSQL](07-referencia/MAPA-FUNCIONAL.md)
- [Glosario técnico y clínico-operativo](07-referencia/GLOSARIO.md)

## 08 · Recrear desde cero

- [Manual completo de reconstrucción con buenas prácticas](08-recrear-desde-cero/README.md)
- [Checklist de producto final](08-recrear-desde-cero/10-CHECKLIST-PRODUCTO-FINAL.md)
- [Plantilla para decisiones de arquitectura](08-recrear-desde-cero/PLANTILLA-ADR.md)

## Auditoría de Hospital de día

- [Reporte de auditoría y remediación del 30/07/2026](08-auditoria/REPORTE-AUDITORIA-HOSPITAL-DIA-2026-07-30.md)
- [Matriz reproducible de 100 casos](08-auditoria/HOSPITAL-DIA-100-CASOS.md)
- [Resultado final: 100 PASS, 0 FAIL, 0 NO_DATA y 0 MANUAL](08-auditoria/resultados/hospital-dia-100-casos-20260730-100711.md)
- [Cómo ejecutar el arnés QA](08-auditoria/README.md)

## 09 · Historial de migración Angular y arquitectura hexagonal

- [Programa de migración](09-migracion-angular-hexagonal/README.md)
- [Línea base verificada](09-migracion-angular-hexagonal/BASELINE-2026-07-30.md)
- [Matriz de paridad funcional](09-migracion-angular-hexagonal/MATRIZ-DE-PARIDAD.md)
- [Arquitectura objetivo](09-migracion-angular-hexagonal/ARQUITECTURA-OBJETIVO.md)
- [Contratos REST](09-migracion-angular-hexagonal/CONTRATOS-REST.md)
- [Corte final de entrada Angular](09-migracion-angular-hexagonal/CORTE-FINAL-ENTRADA-ANGULAR.md)
- [Configuración](09-migracion-angular-hexagonal/MIGRACION-CONFIGURACION.md),
  [Protocolos](09-migracion-angular-hexagonal/MIGRACION-PROTOCOLOS.md) y
  [Guías](09-migracion-angular-hexagonal/MIGRACION-GUIAS.md)

- [Corte Angular 002: Estudios, línea de tiempo y alta](09-migracion-angular-hexagonal/CORTE-ANGULAR-002-ESTUDIOS-TIMELINE-ALTA.md)
- [Corte Angular 003: Hospital de Día](09-migracion-angular-hexagonal/CORTE-ANGULAR-003-HOSPITAL-DE-DIA-LECTURA.md)
- [Corte Angular 031: conflictos de guardado](09-migracion-angular-hexagonal/CORTE-ANGULAR-031-CONFLICTOS-DE-GUARDADO.md)
- [Corte Angular 032: comparación segura](09-migracion-angular-hexagonal/CORTE-ANGULAR-032-COMPARACION-DE-CONFLICTOS.md)
- [Corte Angular 033: E2E concurrente](09-migracion-angular-hexagonal/CORTE-ANGULAR-033-E2E-CONFLICTO-CONCURRENTE.md)
- [Corte Angular 034: editor Conclusión / resumen](09-migracion-angular-hexagonal/CORTE-ANGULAR-034-EDITOR-CONCLUSION-RESUMEN.md)
- [Corte Angular 035: editor Motivo de consulta](09-migracion-angular-hexagonal/CORTE-ANGULAR-035-EDITOR-MOTIVO-CONSULTA.md)
- [Corte Angular 036: editor Antecedentes de enfermedad actual](09-migracion-angular-hexagonal/CORTE-ANGULAR-036-EDITOR-ENFERMEDAD-ACTUAL.md)
- [Corte Angular 037: editor Antecedentes personales](09-migracion-angular-hexagonal/CORTE-ANGULAR-037-EDITOR-ANTECEDENTES-PERSONALES.md)
- [Corte Angular 038: editor Examen físico](09-migracion-angular-hexagonal/CORTE-ANGULAR-038-EDITOR-EXAMEN-FISICO.md)
- [Corte Angular 039: Estudios complementarios coordinados](09-migracion-angular-hexagonal/CORTE-ANGULAR-039-ESTUDIOS-COORDINADOS.md)
- [Corte Angular 040: paciente de ejemplo sintético](09-migracion-angular-hexagonal/CORTE-ANGULAR-040-PACIENTE-EJEMPLO-SINTETICO.md)

## Fuentes de verdad

Cuando dos documentos difieran, prevalecen en este orden:

1. migraciones Flyway para estructura y restricciones de PostgreSQL;
2. OpenAPI generado por el servidor para el contrato HTTP;
3. permisos verificados por los controladores y servicios;
4. estos documentos explicativos.

`scripts/generate-api-docs.ps1` reconstruye el catálogo de endpoints desde el
OpenAPI real y el CI impide publicar ese catálogo desactualizado.
