# Documentación completa de HCOP JP

Este es el índice maestro del producto. La documentación se mantiene junto al
código y describe la aplicación, la API, PostgreSQL, Docker, seguridad,
operación y mantenimiento. No es necesario conocer Docker ni Java para usar el
sistema.

## Elegir un recorrido

| Necesidad | Empezar por |
|---|---|
| Usar HCOP JP en atención clínica | [Manual de uso](01-uso/MANUAL-DE-USO.md) |
| Instalarlo en una PC | [Instalación desde GitHub](00-inicio/INSTALACION-DESDE-GITHUB.md) |
| Entender el circuito de tratamiento | [Flujo de tratamiento](01-uso/FLUJO-TRATAMIENTO.md) |
| Operar Hospital de día paso a paso | [Video detallado y guía de capítulos](01-uso/VIDEO-CIRCUITO-HOSPITAL-DIA-PASO-A-PASO.md), [circuito de 7 pasos](01-uso/CIRCUITO-HOSPITAL-DE-DIA-7-PASOS.md) y [guía por roles](01-uso/GUIA-POR-ROLES-HOSPITAL-DE-DIA.md) |
| Revisar la auditoría de Hospital de día | [Reporte del 30/07/2026](08-auditoria/REPORTE-AUDITORIA-HOSPITAL-DIA-2026-07-30.md) y [matriz de 100 casos](08-auditoria/HOSPITAL-DIA-100-CASOS.md) |
| Integrarse con la API | [Swagger / OpenAPI](02-arquitectura/SWAGGER-OPENAPI.md) y [endpoints](02-arquitectura/ENDPOINTS.md) |
| Mantener el código | [Arquitectura MVC](02-arquitectura/MVC.md) y [mapa funcional](07-referencia/MAPA-FUNCIONAL.md) |
| Entender o respaldar PostgreSQL | [Modelo](03-base-de-datos/MODELO-DE-DATOS.md) y [diccionario](03-base-de-datos/DICCIONARIO-DE-DATOS.md) |
| Configurar un servidor | [Variables de entorno](05-operacion/VARIABLES-DE-ENTORNO.md) y [seguridad](05-operacion/SEGURIDAD.md) |

Con el sistema iniciado también existe una versión navegable en
`http://localhost:5180/docs/`.

## 00 · Empezar

- [Instalación desde GitHub](00-inicio/INSTALACION-DESDE-GITHUB.md)
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

## 03 · Base de datos

- [Modelo de datos](03-base-de-datos/MODELO-DE-DATOS.md)
- [Diccionario de las 34 tablas](03-base-de-datos/DICCIONARIO-DE-DATOS.md)
- [Origen y recuperación de campos](03-base-de-datos/CAMPOS-Y-RELACIONES.md)

## 04 · Desarrollo

- [Crear desde cero](04-desarrollo/CREAR-DESDE-CERO.md)
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

## Fuentes de verdad

Cuando dos documentos difieran, prevalecen en este orden:

1. migraciones Flyway para estructura y restricciones de PostgreSQL;
2. OpenAPI generado por el servidor para el contrato HTTP;
3. permisos verificados por los controladores y servicios;
4. estos documentos explicativos.

`scripts/generate-api-docs.ps1` reconstruye el catálogo de endpoints desde el
OpenAPI real y el CI impide publicar ese catálogo desactualizado.
