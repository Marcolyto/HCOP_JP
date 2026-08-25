# Progreso migración BFF — feature/migracion-bff-arquitectura

Plan completo: `~/.claude/plans/fuzzy-waddling-galaxy.md` (leer ahí el detalle de cada tarea).
Este archivo es el tracker de estado — actualizar después de cada tarea completada, ANTES de
seguir con la próxima. Si el contexto se compacta, releer este archivo primero.

## Convención
- `[ ]` pendiente · `[~]` en curso · `[x]` hecho y verificado · `[!]` bloqueado (anotar por qué)

## F0 — Separar en 3 servicios Docker (sin BFF, sin tocar auth)

- [x] F0.1 — git mv (backend/, frontend/public, legacy-reference/) — commit 7b42e91
- [x] F0.2 — Backend deja de servir el frontend (WebConfiguration, pom.xml, Dockerfile, StudyTemplateController) — 308 tests verdes
- [x] F0.3 — frontend/Dockerfile + frontend/nginx.conf + scripts de contrato visual — npm run build verde (incl. --dist)
- [x] F0.4 — compose.yaml/.github/.e2e/.validation/.dev + scripts (backup/restore/instalar/EJECUTAR-DOCKER) + CI + nginx-routing-test.ps1
- [x] F0.5 — Verificación F0 en runtime real (Docker), con el usuario corriendo los comandos:
  - `docker compose up --build --wait`: 3 servicios healthy
  - health, los 12 redirects (Location relativo, sin puerto), `/app/` con `<app-root>`,
    login/logout real, `/api/clinical/status`, `/api/study-templates`, thumbnail real
    servido por nginx (`image/webp`), video con `Range` (206), 6 paths legacy en 404 — todo OK
  - 2 bugs encontrados y corregidos en el camino (ver commits): Host header inválido hacia
    Tomcat en /actuator/health, /v3/api-docs, /swagger-ui (nginx mandaba el nombre del
    upstream "api_upstream" como Host, con guion bajo → Tomcat 400); y redirects
    absolutizados con el puerto interno de nginx (8080) en vez de relativos
  - pwsh instalado (`brew install powershell`) y scripts corridos contra el stack real:
    `nginx-routing-test.ps1` OK · `smoke-test.ps1` OK · `configuration/guide/protocol-contract-test.ps1`
    OK (3/3) · `test-core-browser-e2e.ps1` 3/3 passed.
  - `test-clinical-conflict-e2e.ps1`: **7/7 failed**, misma causa raíz en los 7 — al abrir el
    paciente recién creado (`page.goto('./')`), el nombre completo existe en el DOM pero queda
    `hidden` (timeout esperando `toBeVisible`). El layout carga con el panel Historia colapsado
    ("Mostrar solo Estudios y colapsar Historia" queda `[pressed]` por default), y el nombre del
    paciente vive en ese panel. No es un problema de red/proxy/nginx (banner, tabs, login, todo
    lo demás renderiza bien) — es un bug de la app (default del divisor de Historia/Estudios),
    preexistente y sin relación con la migración BFF. `clinical-conflict.spec.ts` no se tocó
    desde `ee62be8` y nunca se había corrido en este entorno (pwsh no estaba disponible antes).
    **Pendiente, no bloqueante para F1**: investigar `clinical-shell` (posición default del
    divisor Historia/Estudios) y arreglar antes de mergear a main.

## F1 — BFF + Redis (sesión opaca actual, sin JWT)

- [x] F1.1 — Scaffolding bff/ (pom.xml, Dockerfile, application.yml, HcopBffApplication) —
  `mvn test` + `package` verdes; jar arranca standalone (`java -jar`), `/actuator/health` responde
  503 DOWN sin Redis levantado (esperado, redis entra en F1.5)
- [x] F1.2 — BffAuthController + BffSession + BffSessionService (Redis) + BackendAuthClient.
  Decisión (preguntada al usuario, ver también más abajo): el backend hoy solo lee sesión de la
  cookie `HCOP_SESSION` (`AuthInterceptor.java`), no de `Authorization` — el plan F1 pedía mandar
  `Authorization: Bearer` pero eso recién lo soportaba el `JwtAuthenticationFilter` de F2. Se
  optó por tocar `AuthInterceptor` ahora: acepta `Authorization: Bearer` además de la cookie
  (misma validación SHA-256 contra `local_sessions`, sin JWT), cambio chico y aislado — 3 tests
  nuevos, 311 tests del backend verdes. Verificado end-to-end real: `bff/` standalone + Redis
  efímero + backend reconstruido con el fix, contra el stack F0 real (nginx→backend en :5180):
  login guarda `{backendToken, expiresAt}` en Redis y setea `BFF_SESSION`, `/me` con sesión
  reenvía el Bearer y devuelve el usuario completo, `/me` sin sesión y `/logout` responden el
  contrato exacto de hoy. `smoke-test.ps1` sigue verde con el `AuthInterceptor` tocado (cookie
  directa intacta). 21 tests nuevos en `bff/` (SetCookieParser, BffSessionService,
  BffAuthController), todos verdes.
- [x] F1.3 — ApiProxyController streaming + DocsProxyController + BackendApiClient +
  ProxyException/ProxyExceptionHandler. `backendStreamClient` con `JdkClientHttpRequestFactory`
  (streaming real de request y response, sin `byte[]`, redirects `NEVER`). URI armada a mano con
  `UriComponentsBuilder.build(true)` (hallazgo 3, verificado con test contra servidor HTTP real
  con `tx%2017%2F165` byte a byte). Lista negra de headers de entrada/salida (`Cookie`,
  `Authorization` entrante y `Set-Cookie` del backend nunca cruzan). Pass-through literal de
  status+body en cualquier respuesta del backend; `ProxyException`→502/504 solo si el backend ni
  siquiera respondió. `DocsProxyController` cubre los mismos paths que nginx proxeaba directo en
  F0 (`/v3/api-docs`, `/swagger-ui.html`, `/swagger-ui/**`, `/webjars/**`), sin sesión. Se agregó
  `auth/BffSessionResolver` compartido (refactor sin cambio de contrato de `BffAuthController`) —
  interino: resuelve contra Redis en cada request, F1.4 lo va a reemplazar por un atributo que
  puebla `BffSessionFilter` una sola vez. 30 tests verdes en `bff/` (6 nuevos de
  `BackendApiClient` contra un `com.sun.net.httpserver.HttpServer` real). Verificado además
  end-to-end real: jar standalone + Redis efímero + backend F0 real — login, proxy genérico
  público y autenticado (Bearer reenviado), 401 pass-through sin sesión, docs proxy
  (`/v3/api-docs` 200, `/swagger-ui.html` 302→`/swagger-ui/index.html`), `Set-Cookie` del backend
  confirmado sin fuga al navegador.
- [x] F1.4 — Security/logging/cache filters + BackendHealthIndicator.
  `security/`: `BffSessionFilter` (@Order 30, resuelve sesión una vez por request + refresh
  transparente — TTL<1 día ⇒ extiende Redis Y reemite cookie, con lock SETNX 5s así de N
  requests concurrentes solo uno refresca), `SessionRequiredFilter` (@Order 40, 401 byte a byte
  igual a `AuthenticationRequiredResponse` del backend, mismos paths públicos que
  `AuthInterceptor.isPublic()`), `CurrentSessionArgumentResolver` (inyecta
  `Optional<BffSession>`/`BffSession` en controllers desde el atributo del filtro — se usó para
  sacarle a `ApiProxyController` el lookup a Redis que tenía interino desde F1.3).
  `logging/`: `CorrelationIdFilter` (@Order 10, respeta `X-Correlation-Id` entrante o genera uno,
  MDC), `RequestResponseLoggingFilter` (@Order 20, una línea por request, excluye
  `/actuator/health` vía `LoggingPolicy`), `LogEvent`.
  `cache/CacheControlFilter` (@Order 50, el más interno — `HttpServletResponseWrapper` que
  decide `no-store` recién en `getOutputStream()`/`getWriter()`, porque `BackendApiClient` ya
  comprometió la respuesta con `flushBuffer()` para cuando un filtro normal post-`chain.doFilter`
  podría tocarla).
  `health/BackendHealthIndicator`: `/actuator/health` del BFF ahora depende del health real del
  backend. 55 tests verdes en `bff/` (25 nuevos). Verificado end-to-end real (Redis efímero +
  backend F0 real): endpoint protegido sin sesión corta en el BFF con el 401 nuevo (nunca llega
  al backend), `X-Correlation-Id` presente en toda respuesta, login + endpoint protegido con
  sesión 200, `Cache-Control: no-store` por default confirmado, `/actuator/health` sigue UP.
- [x] F1.5 — Compose con redis+bff, verificación F1.
  `compose.yaml`: `redis` (efímero a propósito — sin `--appendonly`, sin volumen; perder sesiones
  BFF en un restart de Redis solo obliga a re-login, no pierde datos clínicos) + `bff` (build
  `./bff`, `BACKEND_URL=http://backend:5180`, `REDIS_HOST=redis`, sin `ports`) + `frontend` pasa a
  depender de `bff` en vez de `backend`. `compose.dev.yaml`: puerto debug del bff en 5184.
  `frontend/nginx.conf`: upstream `api_upstream` de `backend:5180` → `bff:8080` (un solo cambio
  cubre `/api/`, `/actuator/health`, `/v3/api-docs`, `/swagger-ui*`, `/webjars/` — mismos
  `location` de F0.4).
  **Hallazgo real F1.5** (no en el plan): `compose.e2e.yaml` no tenía `bff`/`redis` — como el
  upstream de nginx queda compilado en la imagen del frontend (no es configurable por entorno),
  el stack E2E rompía en el arranque (`nginx: emerg host not found in upstream "bff:8080"`). Se
  agregaron ambos servicios ahí también, mismo patrón que `compose.yaml`. `.github/workflows/
  verify.yml`: job `bff` nuevo (`mvn verify` sobre `bff/pom.xml`, nunca corría en CI) + agregado a
  los `needs` del job `publish`. `.env.example`: `HCOP_REDIS_IMAGE` (mismo patrón que
  `HCOP_POSTGRES_IMAGE`).
  **Deliberadamente fuera de alcance de F1.5** (no bloqueante, antes de mergear a main): la
  matriz de publish de `verify.yml` y `compose.github.yaml` siguen sin imagen de `bff` — una
  instalación desde GHCR (`instalar-desde-github.ps1`, `EJECUTAR-DOCKER-DESDE-GITHUB.ps1`) hoy no
  tendría bff. Es trabajo de infra puro (agregar el 4to servicio a la distribución GHCR), no
  cambia nada del BFF en sí — se puede hacer en un commit aparte antes de mergear.
  **Verificación end-to-end real** (5 servicios healthy: database, redis, backend, bff,
  frontend): `nginx-routing-test.ps1` OK · `smoke-test.ps1` OK · los 3 contract-tests OK (con
  streaming/multipart pasando por el BFF) · `test-core-browser-e2e.ps1` 3/3 passed (ahora vía
  BFF real) · `test-clinical-conflict-e2e.ps1` mismos 7/7 failed que en F0.5 (bug de app
  preexistente confirmado, sin relación a la migración, sin regresión nueva) · recrear el
  contenedor de Redis desloguea limpio (`authenticated:false`) sin romper la app, un simple
  `restart` (sin recrear) preserva la sesión porque Redis persiste su RDB por default dentro del
  mismo contenedor — comportamiento esperado y documentado.

## F1 — CERRADA. BFF como Token Handler de la sesión opaca actual, proxy completo (auth +
  genérico + docs), filtros de sesión/seguridad/logging/cache, health real, y los 5 servicios
  corriendo juntos y verificados. Commits: `f89aea6` (F1.1) · `084bb63`+`df1ae68` (F1.2) ·
  `ec4f1f0` (F1.3) · `b716cf3` (F1.4) · el de F1.5 (este). Siguiente: F2 — Token Handler JWT real.

## F2 — Token Handler JWT real

- [x] F2.1 — TokenIssuerTest (validar jjwt/Jackson 3 ANTES de seguir). jjwt 0.13.0 no existe
  (Central tiene hasta 0.12.6); jjwt-gson en vez de jjwt-jackson (evita Jackson 2 transitivo
  conviviendo con Jackson 3/tools.jackson). 5/5 verde — commit `d310518`.
- [x] F2.2 — JwtProperties (fail-fast <32 bytes) + SecurityConfiguration en permitAll (sin esto
  Boot auto-configura form login por defecto) + `spring-boot-starter-security` +
  `UserDetailsServiceAutoConfiguration` excluida (paquete Boot 4.1:
  `org.springframework.boot.security.autoconfigure`, no `...autoconfigure.security.servlet`).
  `HCOP_JWT_SECRET` propagado a todo compose/CI/scripts/.env.example. Verificado en Docker real
  — commit `94a9ee7`.
- [x] F2.3 — V013__jwt_auth.sql (aditiva): `local_session_state` (sid PK, revoked) +
  `local_refresh_tokens` (jti PK → sid, rotación por fila) + `clinical_files.upload_session_id`
  nullable. `SessionStateRepository`/`RefreshTokenRepository` (JDBC directo, sin unit test —
  mismo régimen que `AuthRepository`: el Dockerfile corre `mvn test` sin socket de Docker
  disponible, Testcontainers rompe el build de imagen, se descartó). Verificado: build de imagen
  limpio, V013 aplica en Postgres real — commit `19dd606`.
- [x] F2.4 — `AuthContext.token`→`AuthContext.sessionId` (9 call-sites: AuthController×3,
  PatientController×2, PatientWorkspaceController, ClinicalFileController×2,
  StudyTemplateController). Cambio mecánico: mismo valor hoy (`sha256(token)` ==
  `local_sessions.token_hash`), calculado una vez en `AuthInterceptor` en vez de en cada
  consumidor — prepara el seam para que F2.6 solo tenga que tocar `AuthInterceptor`/el filtro
  JWT. `AuthService.logout/changePassword/setActivePatient` reciben el hash ya calculado (sin
  doble sha256). Verificado en Docker real: login→active-patient→me→logout→me(401) idéntico.
  316 tests verdes.
- [x] F2.5 — login/refresh/logout modo dual (`hcop.auth.mode: cookie|jwt`, default cookie, env
  `HCOP_AUTH_MODE`). Modo JWT: login crea `local_session_state` (sid) sin tocar `local_sessions`,
  emite access token (HS512, TTL 15 min, `HCOP_JWT_ACCESS_MINUTES`) + refresh token (también JWT
  firmado — evita guardar un secreto crudo comparable, la fila de `local_refresh_tokens` es el
  ledger de revocación) con TTL leído de `local_security_settings.session_duration_minutes`
  (re-cableado real: antes esa columna era editable desde Configuración pero no la consultaba
  nadie). `POST /api/auth/refresh` (nuevo, público, no expuesto al navegador) rota el `jti`
  preservando el `sid`, releyendo roles/permisos frescos de la DB. Login/refresh devuelven
  `{ok,accessToken,refreshToken,expiresIn,refreshExpiresIn,session:{...idéntico a hoy...}}`, sin
  `Set-Cookie`. **Bug encontrado y corregido en la verificación real** (no en unit tests):
  `/api/auth/logout` no estaba en `isPublic()` de `AuthInterceptor` — en modo JWT el cliente no
  tiene cookie/Bearer opaco que el interceptor entienda todavía (el filtro JWT es F2.6), así que
  logout devolvía 401 antes de llegar al controller y nunca revocaba nada; el refresh posterior
  seguía funcionando (bug silencioso). Fix: `isPublic("/api/auth/logout")` solo cuando
  `hcop.auth.mode=jwt` — modo cookie sin cambio (logout sigue exigiendo cookie/Bearer válido,
  comportamiento preexistente). Verificado en Docker real (contenedor aparte con
  `HCOP_AUTH_MODE=jwt`, red `hcop_jp_egress`+`hcop_jp_internal` porque `hcop_jp_internal` es
  `internal: true` y no publica puertos sola): login→refresh→reuso del refresh viejo (401,
  rotación real)→logout→refresh tras logout (401). Modo cookie re-verificado sin regresión
  (login/logout con y sin cookie, smoke-test.ps1 verde). 320 tests verdes.
- [x] F2.6 — JwtAuthenticationFilter + SecurityFilterChain (commit aislado, riesgoso). El access
  token ahora lleva el `SessionPrincipal` completo salvo `activePatientId` (roles con id/key/name,
  no solo key — hacía falta para reconstruir sin releer la DB); `JwtAuthenticationFilter` (sin
  `@Component`, registrado a mano vía `FilterRegistrationBean` en `SecurityConfiguration`, trap
  del plan evitada) valida firma+expiración, resuelve `activePatientId` con una sola lectura por
  PK a `local_session_state` y puebla los mismos atributos de request que `AuthInterceptor` usa
  en modo cookie — `AuthContext.PRINCIPAL_ATTRIBUTE`/`SESSION_ID_ATTRIBUTE`. `AuthInterceptor` se
  volvió idempotente: si el filtro ya resolvió un principal, lo reusa en vez de re-resolver (no
  hay doble autenticación, ni en cookie ni en JWT). Con esto los 93 `requirePermission` y los 4
  `hasPermission` de filtrado de datos funcionan igual en ambos modos, sin tocarlos (hallazgo 6).
  **Desvío consciente de la redacción literal del plan** ("esto se endurece a
  anyRequest().authenticated()"): `SecurityConfiguration` se queda en `permitAll()` — los filtros
  de Spring Security corren *antes* que cualquier `HandlerInterceptor`, así que endurecer el gate
  ahí rompería el modo cookie (para cuando `AuthInterceptor` resuelve la sesión por cookie, Security
  ya habría rechazado la request). No hay forma de cumplir la redacción literal sin romper cookie
  o reescribir los 93 call-sites — ambos fuera de alcance de F2.6 y contra el hallazgo 6.
  Documentado en el javadoc de `SecurityConfiguration`. 6 tests nuevos (`JwtAuthenticationFilterTest`,
  incluye guardia por reflexión de que la clase no tiene `@Component`). Verificado en Docker real
  (contenedor `HCOP_AUTH_MODE=jwt`): endpoint protegido sin token → 401; con el access token JWT
  del login → 200 (`/api/study-templates`); `PUT /api/auth/active-patient` con JWT funciona
  (mismo `AuthContext.sessionId` que en cookie); token tamperado → 401. Modo cookie (compose.yaml,
  default) re-verificado sin regresión, smoke-test.ps1 verde. 326 tests verdes.
- [x] F2.7 — Revocación inmediata + revocar en AdminService/changePassword. `JwtAuthenticationFilter`
  ahora rechaza (no puebla nada) si `local_session_state.revoked` — detrás de
  `hcop.jwt.session-revocation-check` (default `true`). `AuthService.changePassword` revoca
  toda otra sesión JWT del usuario (preserva la actual, mismo criterio que `deleteOtherSessions`
  en modo cookie) — detecta modo JWT parseando `sessionId` como UUID (falla silencioso en modo
  cookie, es un hash ahí). `AdminService.updateUser` revoca TODAS las sesiones JWT del usuario
  (deshabilitar, cambiar contraseña, o reasignar roles — se compara el set de roles antes/después
  con `AdminRepository.roleIdsForUser`, nuevo). `AdminService.updateRole` revoca a todos los
  usuarios del rol (`AdminRepository.userIdsForRole`, nuevo) cuando se editan sus permisos.
  `SessionStateRepository`/`RefreshTokenRepository`: `revokeAllForUserExcept`/`revokeAllForUsers`
  nuevos (batch). 13 tests nuevos (`JwtAuthenticationFilterTest` +2, `AdminServiceRevocationTest`
  nuevo — sin cobertura previa de `AdminService`, mismo patrón mock-based que el resto).
  Verificado en Docker real (`HCOP_AUTH_MODE=jwt`): login de `marcolyto2`, deshabilitado por
  `marcolyto` vía `PUT /api/admin/users/2`, el access token YA EMITIDO de `marcolyto2` (todavía
  dentro de su TTL de 15 min) devuelve 401 en la request inmediatamente siguiente — sin esperar
  el vencimiento. Modo cookie re-verificado sin regresión, smoke-test.ps1 verde. 333 tests verdes.
- [ ] F2.8 — Eliminar modo cookie + V014 DROP local_sessions + regenerar ENDPOINTS.md
- [ ] F2.9 — security-review sobre el diff completo

## F3 — Backend hexagonal (~14 módulos)

- [ ] F3.0.1 — ArchUnit: 10 reglas nuevas en modo permisivo (allow-list = tracker)
- [ ] F3.0.2 — Cerrar deuda configuration/ConfigurationService.java
- [ ] F3.0.3 — Test guardián OpenAPI (claves Controller.metodo resuelven)
- [ ] F3.0.4 — Snapshot openapi-snapshot.json versionado + CI diff
- [ ] F3.1 — tools, system, admin (calibrar patrón; escribir tests que faltan)
- [ ] F3.2 — catalog, integration, media
- [ ] F3.3.0 — Puertos cruzados (patient/treatment/infusion) — commit propio
- [ ] F3.3 — patient, diagnosis, workflow, treatment, infusion (3 PRs), qr
- [ ] F3.4 — common→sharedkernel/platform, config→platform (NO hexagonal), eliminar ApiException

## Decisiones ya tomadas (no volver a preguntar)
- Layout: backend/ bff/ frontend/ en raíz · Auth: JWT completo · Alcance: hexagonal completo
- Orden: Infra→BFF→JWT→hexagonal · Revocación: inmediata (columna revoked + lookup por sid)
- Puerto publicado: se mantiene 5180

## Notas de ejecución (agregar hallazgos nuevos acá, no releer agentes viejos)
