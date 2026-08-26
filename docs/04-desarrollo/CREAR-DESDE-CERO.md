# Crear HCOP JP desde cero

Esta es la versión resumida. Para una reconstrucción completa, con decisiones,
seguridad, migración, pruebas, Docker y checklist de aceptación, use el
[manual de reconstrucción con buenas prácticas](../08-recrear-desde-cero/README.md).

## 1. Herramientas

- Java 21;
- Maven 3.9 o superior;
- Node.js 24 y npm;
- Docker Desktop;
- Git.

## 2. Tres proyectos, no uno

```text
backend/    Java 21 · Spring MVC · PostgreSQL — dominio clínico, hexagonal
bff/        Java 21 · Redis — Token Handler de la sesión
frontend/   Angular · nginx — SPA + único punto público
```

El navegador sólo habla con `frontend`/`bff`; el `backend` recibe únicamente
`Authorization: Bearer` del `bff`, nunca cookies del navegador. Ver
[01](../08-recrear-desde-cero/01-INICIALIZAR-PROYECTO.md) para el detalle.

## 3. Crear el backend

Genere Spring Boot (`backend/`) con:

- Spring Web MVC;
- JDBC;
- Validation;
- Actuator;
- Flyway;
- PostgreSQL;
- Spring Security (filtro JWT propio, `SecurityFilterChain` en `permitAll`);
- jjwt;
- springdoc OpenAPI MVC.

Cada capacidad clínica es un módulo hexagonal —
`domain`/`application`/`infrastructure`, no carpetas globales con todos los
controladores o repositorios. Ver
[02](../08-recrear-desde-cero/02-ARQUITECTURA-HEXAGONAL.md).

## 4. Crear el BFF

Genere Spring Boot (`bff/`) con Spring Web MVC + Spring Data Redis. Sin
PostgreSQL propio. Guarda `{accessToken, refreshToken}` del backend en Redis
y expone una cookie de sesión opaca al navegador — nunca reenvía el JWT.

## 5. Crear la base

No cree tablas desde Java. Agregue migraciones inmutables dentro de
`backend/`:

```text
backend/src/main/resources/db/migration/V001__core_schema.sql
V002__rbac_seed.sql
...
```

Nunca modifique una migración aplicada. Cree la siguiente.

## 6. Incorporar la interfaz

Angular vive en `frontend/`, servicio propio con su Dockerfile (build Node →
runtime nginx) — no dentro del `.jar` de Java. Use rutas `/api/...` del
mismo origen público (nginx las resuelve hacia `bff`). No guarde pacientes
en JavaScript, archivos versionados ni `localStorage` como fuente clínica.

## 7. Implementar cada caso de uso hexagonal

1. Defina el comando/resultado del `port/in`.
2. `infrastructure/web`: resuelve `Authorization: Bearer` (vía `AuthContext`)
   y autoriza.
3. `application/service`: valida, abre transacción, coordina `port/out`.
4. `infrastructure/persistence`: ejecuta SQL parametrizado.
5. Agregue auditoría/evolución cuando sea acto clínico.
6. Documente la operación en Swagger.
7. Agregue prueba (éxito, entrada inválida, permiso, conflicto) y confirme
   que ArchUnit sigue en verde.

## 8. Empaquetar

```powershell
mvn -f backend/pom.xml verify
mvn -f bff/pom.xml verify
docker compose up --build --wait
```

Cada `Dockerfile` (backend/bff/frontend) compila en una etapa y ejecuta como
usuario no root en una imagen runtime separada (JRE para backend/bff, nginx
para frontend).

## 9. Publicar

Un push a `main` (`.github/workflows/verify.yml`):

- verifica `backend`, `bff` y `frontend` por separado;
- levanta los 5 servicios en Docker y corre el smoke/E2E completo;
- publica las tres imágenes (`ghcr.io/marcolyto/hcop_jp-backend`,
  `-bff`, `-frontend`) sólo si todo lo anterior pasó.
