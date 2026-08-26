# Recrear HCOP JP desde cero

Esta carpeta es un manual de reconstrucción, no una explicación superficial del
repositorio. Permite que otro equipo vuelva a crear el producto con la misma
arquitectura, contratos y garantías, aunque parta de un directorio vacío.

## Resultado esperado

Al terminar debe existir un sistema de tres servicios (`backend`/`bff`/
`frontend`, ver [01](01-INICIALIZAR-PROYECTO.md)) con:

- Java 21 y Spring Boot 4.1 en `backend` y `bff`;
- arquitectura hexagonal modular en `backend` (`domain`/`application`/
  `infrastructure`, ArchUnit en verde);
- PostgreSQL 18 con migraciones Flyway;
- Angular servido por nginx en `frontend`, sin acceso directo del navegador
  al `backend`;
- `bff` como Token Handler: sesión opaca hacia el navegador, JWT hacia el
  backend, Redis como caché de sesión;
- login obligatorio, roles y permisos;
- historia clínica versionada;
- tratamiento, ciclos, farmacia, sillones, QR y workflows;
- archivos clínicos privados;
- Swagger/OpenAPI;
- Docker, pruebas automáticas y documentación mantenible.

No se considera terminado si sólo “se ve igual”. También deben mantenerse
integridad de datos, permisos, concurrencia, auditoría, backups y capacidad de
actualización.

## Orden recomendado

1. [Principios, alcance y decisiones](00-PRINCIPIOS-Y-ALCANCE.md)
2. [Inicializar el proyecto](01-INICIALIZAR-PROYECTO.md)
3. [Construir la arquitectura hexagonal](02-ARQUITECTURA-HEXAGONAL.md)
4. [Diseñar PostgreSQL y Flyway](03-POSTGRESQL-Y-FLYWAY.md)
5. [Implementar seguridad y auditoría](04-SEGURIDAD-Y-AUDITORIA.md)
6. [Diseñar API y Swagger](05-API-Y-SWAGGER.md)
7. [Integrar interfaz y archivos](06-INTERFAZ-Y-ARCHIVOS.md)
8. [Aplicar pruebas y calidad](07-PRUEBAS-Y-CALIDAD.md)
9. [Preparar Docker, CI y despliegue](08-DOCKER-CI-Y-DESPLIEGUE.md)
10. [Migrar y poner en marcha](09-MIGRACION-Y-PUESTA-EN-MARCHA.md)
11. [Seguir el orden de implementación funcional](11-ORDEN-DE-IMPLEMENTACION-FUNCIONAL.md)
12. [Completar el checklist final](10-CHECKLIST-PRODUCTO-FINAL.md)

Para registrar decisiones nuevas use la
[plantilla ADR](PLANTILLA-ADR.md).

## Fuentes de verdad del producto actual

| Tema | Fuente |
|---|---|
| Dependencias y versiones | [backend/pom.xml](../../backend/pom.xml) y [bff/pom.xml](../../bff/pom.xml) |
| Esquema PostgreSQL | `backend/src/main/resources/db/migration` |
| Contrato HTTP | `/v3/api-docs` (vía `bff`, puerto 5180) |
| Endpoints legibles | [ENDPOINTS.md](../02-arquitectura/ENDPOINTS.md) |
| Datos y relaciones | [DICCIONARIO-DE-DATOS.md](../03-base-de-datos/DICCIONARIO-DE-DATOS.md) |
| Empaquetado | `backend/Dockerfile`, `bff/Dockerfile`, `frontend/Dockerfile` y [compose.yaml](../../compose.yaml) |
| Aceptación integral | `scripts/integration-test.ps1` |

## Regla de avance

Cada etapa termina con una prueba verificable. No se continúa acumulando capas
si la anterior no compila, no migra o no documenta su contrato. Una secuencia
segura es:

```text
estructura → migración → repositorio → servicio → controlador → Swagger
→ interfaz → pruebas → operación
```

## Qué no hacer

- copiar una base de pacientes dentro del repositorio;
- mantener dos servidores “pegados” como producto definitivo;
- acceder a PostgreSQL desde el navegador;
- guardar datos clínicos canónicos en `localStorage`;
- editar una migración Flyway ya aplicada;
- confiar sólo en botones ocultos para autorizar;
- usar texto libre para estados operativos que necesitan restricciones;
- publicar secretos en `.env.example`, logs, imágenes o documentación;
- declarar terminado un flujo sin probar error, concurrencia y recuperación.
