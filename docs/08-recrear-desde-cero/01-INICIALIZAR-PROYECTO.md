# 01 · Inicializar el proyecto

## Herramientas

- Git;
- JDK 21;
- Maven 3.9 o superior;
- Node.js 24 y npm (frontend Angular);
- Docker Desktop con Compose v2;
- editor con soporte Java, TypeScript, SQL y HTML/CSS;
- PowerShell 5.1+ en Windows o PowerShell 7 para scripts multiplataforma.

Compruebe:

```powershell
java -version
mvn -version
node -version
npm -version
docker version
docker compose version
git --version
```

## Crear el repositorio

```powershell
New-Item -ItemType Directory HCOP_JP
Set-Location HCOP_JP
git init
```

Use `main` protegida y ramas cortas para cambios. No versionar:

- `.env`;
- `target/`, `.angular/`, `dist/`, `node_modules/`;
- `runtime/storage`;
- dumps PostgreSQL;
- claves y certificados privados;
- archivos clínicos;
- configuración personal del IDE.

## Tres servicios, no uno

El sistema son **tres despliegues independientes** detrás de un único punto de
entrada (nginx del frontend, puerto 5180), no un monolito que sirve HTML y API
desde el mismo proceso:

```text
backend/    Java 21 · Spring MVC · PostgreSQL — dominio clínico completo
bff/        Java 21 · Spring MVC · Redis — Token Handler de la sesión
frontend/   Angular · nginx — SPA + proxy hacia el BFF
```

- **`backend`** nunca lo toca el navegador directamente. Habla JWT: recibe
  `Authorization: Bearer <access-token>`, no cookies.
- **`bff`** es el único que ve el navegador para autenticación. Guarda el
  access/refresh token del backend en Redis, expone una cookie de sesión
  opaca (`BFF_SESSION`) al navegador y nunca reenvía los tokens JWT hacia
  afuera. Proxea el resto de la API hacia `backend` agregando el Bearer.
- **`frontend`** es Angular compilado, servido por nginx. nginx redirige
  `/api/`, `/actuator/health`, `/v3/api-docs`, `/swagger-ui*` y `/webjars/`
  hacia `bff`, no hacia `backend`.

Motivo: el backend no debe aprender a hablar con el navegador (cookies,
CORS, XSS) ni el navegador debe ver un JWT firmado que viva más que una
pestaña. El patrón se llama **Token Handler** — ver
[04 · Seguridad y auditoría](04-SEGURIDAD-Y-AUDITORIA.md).

## Generar los tres proyectos

### `backend/` — Spring Boot

Coordenadas:

```text
group: ar.com.hexium
artifact: hcop-backend
java: 21
packaging: jar
```

Dependencias mínimas:

- Spring Web MVC;
- Spring JDBC;
- Validation;
- Actuator;
- Flyway y soporte PostgreSQL;
- driver PostgreSQL;
- Spring Security (filtro JWT, `SecurityFilterChain` en `permitAll` — el
  gate real lo hace el filtro, no Spring Security, ver 04);
- jjwt (firma/verificación de JWT);
- Springdoc OpenAPI MVC UI;
- Spring Boot Test;
- Testcontainers PostgreSQL (referencia; el Dockerfile corre `mvn test` sin
  socket de Docker disponible, así que los repositorios JDBC reales se
  verifican en Docker end-to-end, no con Testcontainers en build).

### `bff/` — Spring Boot, sin PostgreSQL

Coordenadas: mismo `group`, `artifact: hcop-bff`, Java 21.

Dependencias mínimas:

- Spring Web MVC;
- Spring Data Redis (o el cliente Redis que prefiera — sesión efímera, sin
  persistencia propia);
- `RestClient`/`JdkClientHttpRequestFactory` con streaming real (sin
  `byte[]` intermedio) para proxear hacia el backend;
- Actuator (su `/actuator/health` depende del health real del backend).

El BFF no tiene su propia base de datos. Redis es un caché de sesión, no una
fuente de verdad: perderlo sólo obliga a un re-login.

### `frontend/` — Angular

```powershell
npm install -g @angular/cli
ng new frontend --routing --style=scss
```

Servido en producción por nginx (`frontend/Dockerfile` multi-stage: build
Angular → runtime nginx). El `nginx.conf` es el único lugar que sabe que el
BFF se llama `bff` en la red Docker.

El [pom.xml de `backend/`](../../backend/pom.xml) y el
[pom.xml de `bff/`](../../bff/pom.xml) fijan las versiones probadas. Cuando
se actualice una dependencia:

1. leer notas de migración;
2. cambiar una familia por vez;
3. ejecutar compilación, pruebas y Docker de los tres servicios;
4. revisar OpenAPI;
5. registrar el cambio.

## Estructura interna de `backend/`

Cada capacidad clínica es un módulo con arquitectura hexagonal —
`domain`/`application`/`infrastructure` — no capas técnicas globales
(`controllers/`, `services/`, `repositories/`). Ver
[02 · Construir la arquitectura hexagonal](02-ARQUITECTURA-HEXAGONAL.md).

```text
backend/
  src/
    main/
      java/ar/com/hexium/hcop/
        auth/            ← infraestructura transversal, exenta de hexagonal
        platform/         (fusión de lo que antes era common+config)
        admin/
        patient/
        diagnosis/
        treatment/
        infusion/
        workflow/
        qr/
        configuration/
        catalog/
        media/
        integration/
        system/
        tools/
        guide/
        protocol/
        <cada módulo>/
          domain/
          application/
            port/in/
            port/out/
            service/
          infrastructure/
            web/
            persistence/
            configuration/
      resources/
        application.yml
        db/migration/
    test/
      java/ar/com/hexium/hcop/
docs/
scripts/
.github/workflows/
```

`auth` y `platform` son los únicos módulos permanentemente exentos de la
regla hexagonal (infraestructura de plataforma real: filtros JWT,
`AuthContext`, bootstrap, manejo de excepciones HTTP). ArchUnit
(`HexagonalArchitectureTest`) hace cumplir esto — ver
[07 · Aplicar pruebas y calidad](07-PRUEBAS-Y-CALIDAD.md).

## Configuración externa

`application.yml` del backend contiene valores seguros de desarrollo y
referencias a variables; no secretos reales. Defina desde el comienzo:

- puerto y dirección;
- JDBC y pool;
- rutas de runtime y catálogos;
- secreto y TTL del JWT (`HCOP_JWT_SECRET`, `HCOP_JWT_ACCESS_MINUTES`);
- límites de carga;
- secretos QR y cifrado;
- Actuator y Springdoc.

`application.yml` del BFF necesita, además: URL del backend
(`BACKEND_URL`), host/puerto de Redis.

Use la lista vigente de
[variables de entorno](../05-operacion/VARIABLES-DE-ENTORNO.md).

## Primer hito

Antes de crear dominios clínicos debe pasar, para cada servicio:

```powershell
mvn --batch-mode -f backend/pom.xml verify
mvn --batch-mode -f bff/pom.xml verify
```

Y con los tres contenedores arriba (`docker compose up --build --wait`):

```text
GET http://localhost:5180/actuator/health → UP (vía frontend→bff→backend)
GET http://localhost:5180/swagger-ui.html → interfaz Swagger
```

No agregue funciones clínicas hasta que el esqueleto de los tres servicios
pueda construirse tanto localmente como en Docker.
