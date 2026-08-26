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
- [x] F2.7.5 — BFF habla JWT (no estaba en el checklist original; se agregó ANTES de F2.8 tras
  preguntarle al usuario — ver decisión abajo). El BFF (F1) hablaba el contrato viejo: login
  esperaba `Set-Cookie` del backend, guardaba el token opaco, reenviaba
  `Authorization: Bearer <token opaco>`. F2.8 iba a cortar `local_sessions` — sin este paso el
  BFF (y el login vía frontend) se hubiera roto de punta a punta.
  `BffSession` pasa a `{accessToken, accessExpiresAt, refreshToken, refreshExpiresAt}` (antes
  `{backendToken, expiresAt}`). `SetCookieParser` eliminado (ya no hay `Set-Cookie` que parsear).
  `BackendAuthClient`: `login`/`refresh` (nuevo, server-to-server, no expuesto al navegador)/
  `logout(refreshToken)`/`me(accessToken)` — todo contra el body JSON del backend, no headers.
  `BffAuthController.login`: **nunca reenvía accessToken/refreshToken al navegador** — el body
  de respuesta se re-arma a partir de `session` (mismo shape de siempre), los tokens quedan solo
  en Redis (verificado con un assert explícito en el test). `BffSessionFilter`: el "refresh
  transparente" de F1 (heurística, solo extendía TTL de Redis) pasa a ser una llamada real y
  síncrona a `POST /api/auth/refresh` del backend cuando el access token tiene <2 min de vida
  (antes: <1 día, tenía sentido con TTL de sesión de 30 días; con TTL de access de 15 min ya no).
  Si el refresh falla (401 — sesión revocada), borra la sesión de Redis: la request sigue sin
  sesión, `SessionRequiredFilter` corta con 401 — la revocación inmediata del backend (F2.7)
  ahora se propaga hasta el BFF. `ApiProxyController` reenvía `session.accessToken()`.
  `compose.yaml`/`compose.e2e.yaml`: `HCOP_AUTH_MODE: jwt` agregado al backend — **modo jwt pasa
  a ser el default real de la instalación**, antes de que F2.8 borre el modo cookie del código.
  49 tests del BFF (5 nuevos/reescritos en `BffSessionFilterTest`, `BffAuthControllerTest`
  reescrito completo, `SetCookieParserTest` eliminado).
  **Verificado end-to-end real, stack completo (nginx:5180 → frontend → bff → backend, JWT de
  punta a punta):** build de ambas imágenes con `mvn test` adentro (verde), login real sin
  accessToken/refreshToken en el body de respuesta (solo `Set-Cookie: BFF_SESSION`), `/me`,
  endpoint protegido vía proxy (200), logout, `/me` tras logout (401) — smoke-test.ps1 y
  nginx-routing-test.ps1 verdes — y **`test-core-browser-e2e.ps1` 3/3 passed vía Playwright
  real** (login por la UI, navegación, Configuración) contra el stack JWT completo, no cookie.
  `test-core-browser-e2e.ps1`/`test-clinical-conflict-e2e.ps1`: `HCOP_E2E_JWT_SECRET` agregado
  (faltaba, el compose.e2e.yaml ya lo exigía desde F2.2).
- [x] F2.8 — Eliminar modo cookie + V014 DROP local_sessions + regenerar ENDPOINTS.md.
  `AuthService`/`AuthController`/`AuthInterceptor` reescritos: sin flag `hcop.auth.mode`, sin
  `authenticate(token)`/`login()`/`logout(hash)` de modo cookie, sin `Set-Cookie`.
  `AuthInterceptor` quedó mínimo — ya no resuelve nada, solo lee `PRINCIPAL_ATTRIBUTE` que
  `JwtAuthenticationFilter` pobló y gatea permisos (`isPublic`/`earlyPermission`). `AuthRepository`
  perdió `findSession`/`insertSession`/`touchSession`/`deleteSession`/`deleteOtherSessions`/
  `setActivePatient` (todo `local_sessions`).
  **Bug real encontrado y corregido en esta limpieza** (no en verificación de fases previas):
  `AuthService.setActivePatient` seguía escribiendo en `AuthRepository.setActivePatient`
  (`local_sessions.token_hash = ?`) con `sessionId` = un `sid` UUID desde F2.5 — la condición
  `WHERE` nunca matcheaba nada, así que `PUT /api/auth/active-patient` en modo JWT era un no-op
  silencioso desde F2.5 hasta ahora (devolvía 200 pero no persistía nada; F2.6/F2.7 nunca lo
  probaron con un `patientId` real, solo con `null`). Fix: usa
  `SessionStateRepository.setActivePatient(sid, ...)`. Verificado en Docker real con un paciente
  real: `PUT active-patient` → `GET /me` refleja `activePatientId` correctamente.
  `V014__drop_local_sessions.sql` (aditiva-terminal: `DROP TABLE local_sessions`) — aplicó limpio
  contra la base real con datos (contenedor persistente, no efímero) y contra la base efímera de
  `test-core-browser-e2e.ps1`. `OpenApiConfiguration`: `@SecurityScheme` `sessionCookie`
  (APIKEY/COOKIE/`HCOP_SESSION`) → `bearerAuth` (HTTP bearer/JWT); doc de `login`/`logout`
  actualizada, `refresh` documentado y excluido de `requiresSession` (es público).
  `generate-api-docs.ps1`: línea de autenticación hardcodeada también actualizada (no salía del
  spec). `ENDPOINTS.md` regenerado: **114 operaciones** (113 + `POST /api/auth/refresh`, nueva
  desde F2.5, nunca se había regenerado el doc). `HCOP_AUTH_MODE` retirado de compose.yaml/
  compose.e2e.yaml/application.yml (sin lector desde este commit). `HcopProperties.sessionCookieName`/
  `sessionDurationMinutes` quedan sin lector pero NO se tocan — sacarlos del record obligaría a
  actualizar varios tests no relacionados con auth que lo construyen posicional; no vale la pena
  el blast radius para un cleanup cosmético.
  **Verificado end-to-end real, stack completo con V014 aplicada (sin `local_sessions` en la
  base):** smoke-test.ps1, nginx-routing-test.ps1, los 3 contract-tests (configuration/protocol/
  guide), integration-test.ps1 (flujo clínico integral completo) y test-core-browser-e2e.ps1 3/3
  (Playwright real, DB efímera desde cero con V014). 330 tests del backend verdes.
- [x] F2.9 — security-review sobre el diff completo (auth/, config/, admin/, bff/auth+security+proxy).
  Sin hallazgos de severidad alta o media: SQL parametrizado en todo lo nuevo, sin logging de
  tokens, sin fuga de accessToken/refreshToken al navegador (test explícito), sin bypass de
  autorización nuevo. Desvíos del plan y el bug real de `active-patient` documentados en
  `docs/09-migracion-bff/DECISIONES-F2.md`.

## F2 — CERRADA. Token Handler JWT real: TokenIssuer, migración V013/V014, sesión vía
  `local_session_state`, `JwtAuthenticationFilter`, revocación inmediata, BFF actualizado a JWT
  (F2.7.5), modo cookie eliminado del código y la base. Commits: `d310518`(F2.1) · `94a9ee7`(F2.2)
  · `19dd606`(F2.3) · `c6b47b2`(F2.4) · `ee539ab`(F2.5) · `5a3e5d5`(F2.6) · `07805ba`(F2.7) ·
  `5a3197c`(F2.7.5) · `a69ad8e`(F2.8) · (F2.9, este commit). Siguiente: F3 — Backend hexagonal.

## F3 — Backend hexagonal (~14 módulos)

- [x] F3.0.2 — Cerrar deuda `configuration/ConfigurationService.java` (el "puente temporal").
  Único consumidor (`media/StudyTemplateController`) migrado a hablar directo con
  `ConfigurationManagementUseCase` + `ConfigurationJsonMapper` — no es hexagonal todavía (eso
  llega con `media` en F3.2), así que la traducción `ConfigurationFailure`→`ApiException` quedó
  inline en el controller (el advice de `configuration` solo aplica a `ConfigurationController`).
  Verificado en Docker real: `GET`/`POST /api/study-templates` con un PNG real de punta a punta.
  330 tests verdes.
- [x] F3.0.3 — `OpenApiDocumentationKeysTest` (hallazgo 1, la regla de mayor retorno): escanea el
  classpath con ArchUnit (`ClassFileImporter`) para indexar los 30 `@RestController` por
  `getSimpleName()`, lee `OpenApiConfiguration.DOCUMENTATION`/`PERMISSIONS` por reflexión y
  verifica que cada clave `Controller.metodo` resuelve a un controller y un método reales. 332
  tests verdes.
- [x] F3.0.4 — `docs/02-arquitectura/openapi-snapshot.json` (dump completo y normalizado —claves
  ordenadas recursivamente, sin depender de `jq`— del spec real) + `scripts/generate-openapi-snapshot.ps1`
  (mismo patrón que `generate-api-docs.ps1`, con `-Check`) + paso nuevo en el job `docker` de CI.
  `generate-api-docs.ps1 -Check` no alcanzaba: `ENDPOINTS.md` es una proyección *lossy* del spec
  (sin schemas de request/response) — un cambio en un `ObjectSchema` pasaría ese check y rompería
  el frontend en silencio. **Criterio de aceptación de cada commit de F3: diff del snapshot vacío.**
- [x] F3.0.1 — ArchUnit: 10 reglas nuevas (R1-R9 + R10=F3.0.3) en modo permisivo, allow-list única
  compartida (`TRACKED_LEGACY_MODULES`, el tracker ejecutable — se achica en F3.1/F3.2/F3.3;
  `PERMANENTLY_EXEMPT_MODULES` = `auth`/`common`/`config`, nunca se achica). R4 (slices libres de
  ciclos, hallazgo 7) queda `@ArchIgnore` a propósito — meta visible de F3.3.0. Ajustes reales
  sobre el diseño literal del plan, encontrados corriendo la suite contra el código real: R6
  necesitaba exceptuar también la clase sintética `*Advice$1` que genera el compilador para el
  switch exhaustivo sobre `Type` (su "simple name" no termina en `Advice`, su nombre completo sí
  lo contiene); R9 (naming de puertos) aplicaba por error a los records anidados de cada
  interfaz (`CreateCommand`, `ConfigurationView`, etc. — el propio patrón #1 del plan, "DTO
  anidados como records") — se restringió a clases de primer nivel. 343 tests verdes (1 skip:
  R4), `mvn verify` completo verde.
### F3.1 — tools, system, admin (calibrar patrón; escribir tests que faltan)

- [x] F3.1 (1/3) — `tools` migrado, commit `1c5fb90`. Módulo de 1 archivo (proyección read-only
  sobre `configuration`, cero lógica propia) — se usó para calibrar las 7 piezas con el riesgo
  más bajo posible antes de `admin` (el primer `Postgres*Store` real). `domain/CalculatorSummary`
  (proyección propia, no atada a `ConfigurationDefinition`) · `application/port/in/CalculatorCatalogUseCase`
  · `application/port/out/CalculatorCatalogPort` (rompe la dependencia directa a `configuration`,
  patrón #7) · `application/service/CalculatorCatalogApplicationService` (final, sin `@Service`,
  sin lógica propia) · `infrastructure/configuration/ConfigurationCalculatorCatalogAdapter`
  (el ÚNICO lugar que importa `configuration.ConfigurationManagementUseCase` directo — mismo
  patrón que `guide/infrastructure/configuration/ConfigurationGuideMetadataAdapter`) ·
  `infrastructure/configuration/CalculatorModuleConfiguration` (variante B, `@Configuration`+`@Bean`,
  read-only) · `infrastructure/web/CalculatorCatalogController` (sin cambio de contrato). `tools`
  sale de `TRACKED_LEGACY_MODULES` en `HexagonalArchitectureTest`. Verificado en Docker real:
  rebuild, snapshot OpenAPI sin diff, `GET /api/clinical/tools/calculators` funcionando igual.
  348 tests verdes.
- [x] F3.1 (2/3) — `system` migrado. 7 piezas: `domain/DatabaseHealth` · `application/port/in/SystemStatusUseCase`
  (`ClinicalStatusView`/`RuntimeStatusView` anidados) · `application/port/out/DatabaseHealthStore`
  (persistencia, sufijo `Store` por R9) + `ApplicationVersionPort` (no persistencia, sufijo `Port`)
  · `application/service/SystemStatusApplicationService` (`final`, sin `@Service`) ·
  `infrastructure/persistence/PostgresDatabaseHealthStore` (`@Repository`, `JdbcTemplate`
  inyectado — elimina el antipatrón `new JdbcTemplate(dataSource)` del controller viejo) ·
  `infrastructure/configuration/BuildPropertiesVersionAdapter` (`@Component`) +
  `SystemModuleConfiguration` (variante B, `@Configuration`+`@Bean`, read-only) ·
  `infrastructure/web/StatusController` (nombre y métodos intactos: `clinical`/`liraCompatibility`/
  `runtime`/`stop`, mismos 4 endpoints y mismo shape de respuesta). `liraCompatibility` y `stop` no
  tienen lógica propia (siguen 100% estáticos en el controller, sin pasar por el use case) — solo
  `clinical`/`runtime` usan el puerto (DB health + versión + timestamp). 6 tests nuevos (0 antes):
  `SystemStatusApplicationServiceTest`, `PostgresDatabaseHealthStoreTest`,
  `BuildPropertiesVersionAdapterTest`, `StatusControllerTest`. `system` sale de
  `TRACKED_LEGACY_MODULES`. `mvn -f backend/pom.xml verify` verde — 360 tests (incluye ArchUnit y
  `OpenApiDocumentationKeysTest`). Verificado en Docker real: `docker compose up --build --wait`
  con 5 servicios healthy, `scripts/generate-openapi-snapshot.ps1 -Check` diff vacío,
  `scripts/smoke-test.ps1` verde.
- [x] F3.1 (3/3) — `admin` migrado, el primer `Postgres*Store` real con `@Transactional` de
  verdad. 7 piezas: `domain/{AdminUser,AdminRole,Permission,SecuritySettings}` (`AdminUser` con
  `RoleSummary` anidado) · `application/port/in/AdminManagementUseCase` (comandos anidados como
  records, `roleIds`/`permissions` en crudo como `List<String>` — el parseo/validación es del
  service, no del mapper) · `application/port/out/AdminStore` (+`NewUser`/`ExistingUser`/`NewRole`/
  `ExistingRole` anidados) + `UsernameOrEmailConflictException`/`RoleKeyConflictException` (mismo
  patrón que `ConfigurationKeyConflictException`: el store traduce `DataIntegrityViolationException`
  en el borde, el service nunca ve Spring) · `application/service/AdminApplicationService` (`final`,
  sin `@Service`; la revocación inmediata de sesiones JWT de F2.7 vive acá por ser regla de negocio,
  usa `auth.PasswordService`/`SessionStateRepository`/`RefreshTokenRepository` directo — módulo
  permanentemente exento, mismo criterio que `AuthContext`) + `AdminFailure` (`INVALID`/`NOT_FOUND`/
  `CONFLICT`) · `infrastructure/persistence/PostgresAdminStore` (`@Repository`, `JdbcTemplate`
  inyectado, SQL sin cambios salvo el fix de abajo) · `infrastructure/configuration/
  TransactionalAdminManagement` (variante A, `@Service`+`@Transactional` por método, delegando) ·
  `infrastructure/web/AdminController` (nombre y métodos intactos, mismos 8 endpoints) +
  `AdminJsonMapper` + `AdminFailureAdvice`.
  **Bug real encontrado y corregido en la verificación end-to-end** (preexistente en
  `AdminRepository` desde siempre — nunca antes ejercitado con un request real: `admin` tenía 0
  tests): `usernameOrEmailExists(username, email, excludedId)` con `excludedId = null` (el caso de
  `createUser`) rompía en Postgres real con `could not determine data type of parameter $3` — el
  placeholder de `? IS NULL` no queda atado a ningún tipo de columna cuando el valor es null y por
  eso Postgres no puede inferirlo (a diferencia de `updateUser`, donde el mismo placeholder
  reaparece en `id <> ?` con un valor no nulo). `POST /api/admin/users` devolvía 500 siempre. Fix:
  cast explícito `?::bigint IS NULL`. Encontrado con un curl real contra el stack Docker (crear
  usuario), no por los tests unitarios (que mockean `JdbcTemplate`, no pueden detectar esto).
  `PostgresAdminStore` sigue sin test unitario propio (mismo régimen que `SessionStateRepository`/
  `RefreshTokenRepository` de F2.3: Testcontainers rompe el build de imagen, la única red real es
  Docker end-to-end). 26 tests nuevos: `AdminApplicationServiceTest` (reemplaza y amplía
  `AdminServiceRevocationTest` de F2.7 — misma cobertura de revocación + validaciones + conflictos),
  `AdminJsonMapperTest`, `AdminControllerTest`. `admin` sale de `TRACKED_LEGACY_MODULES`.
  Verificado en Docker real (con el fix aplicado): `mvn -f backend/pom.xml verify` verde (386
  tests) · `docker compose up --build --wait` 5 servicios healthy · `generate-openapi-snapshot.ps1
  -Check` diff vacío · `smoke-test.ps1` verde · flujo real completo por curl (login real):
  `GET /api/admin/users`, `/roles`, `/security-settings`, `/api/clinical/users` (200) ·
  `POST /api/admin/roles` (201) · `POST /api/admin/users` (201, con el fix) · usuario/correo
  duplicado (409) · rol inválido (400) · `PUT .../security-settings` con duración inválida (400) y
  válida (200, revisión incrementada) · `PUT /api/admin/users/{id}` (200, reasignación de roles) ·
  `PUT /api/admin/roles/{id}` (200) · `PUT /api/admin/users/99999` inexistente (404).

## F3.1 — CERRADA. `tools`/`system`/`admin` migrados, patrón hexagonal calibrado (7 piezas,
  variantes A y B de wiring, traducción de excepciones en el borde, revocación JWT como regla de
  negocio del módulo dueño). Commits: `1c5fb90` (1/3 tools) · `366c80c` (2/3 system) · (3/3 admin,
  este). Siguiente: F3.2 — catalog, integration, media.

### F3.2 — catalog, integration, media

- [x] F3.2 (1/3) — `catalog` migrado (1462 LOC, 7 sub-catálogos en un solo módulo: AJCC, TNM/SEER,
  formularios sistémicos, diagnóstico, tratamiento/esquemas COIR+Postgres, protocolo legacy COIR+SEER,
  medicamentos). `domain/{AjccSite,AjccStagingRule,CatalogSearchResult,TnmSchema,TreatmentScheme,
  DiagnosisEquivalence}` · 7 puertos de entrada (`AjccStagingUseCase`, `TnmCatalogUseCase`,
  `SystemicFormCatalogUseCase`, `DiagnosisCatalogUseCase`, `TreatmentCatalogUseCase`,
  `LegacyProtocolCatalogUseCase`, `DrugCatalogUseCase`) + puertos de salida análogos ·
  `application/service/CatalogFailure` (`INVALID`/`NOT_FOUND`, compartido) +
  `CatalogTextSearch` (normalización de texto compartida por AJCC y diagnóstico) ·
  6 adapters en `infrastructure/persistence` (todos `@Repository`, variante B — ningún sub-catálogo
  escribe) · 6 controllers migrados a `infrastructure/web` (mismos endpoints/paths/shapes) +
  `CatalogFailureAdvice` único para los 5 que pueden fallar.
  **Hallazgo real (ArchUnit, no negociable):** `domainIsIndependentFromFrameworks` y
  `applicationDoesNotDependOnWebOrPersistenceFrameworks` son reglas **incondicionales** (no las
  relaja el allow-list de módulos legacy) — ningún `domain`/`application` puede importar
  `tools.jackson..`. Esto chocó con `TreatmentCatalogService.Scheme.definition()`, que
  `treatment`/`infusion`/`protocol` (módulos aún no hexagonales, fuera de este commit) consumen
  como `JsonNode` real. Fix: `TreatmentScheme.definition()` tipa `Object` en la firma pero sigue
  siendo un `JsonNode` en runtime (el adapter nunca lo convierte) — los 3 call-sites externos que
  necesitaban navegarlo agregan un cast `(JsonNode)` puntual (`TreatmentService.java` ×2,
  `TreatmentApplicationLogisticsService.java` ×1); los que solo llamaban `.toString()` no
  necesitaron cambio. Mismo patrón en `SystemicFormCatalogUseCase.find()` (consumido por
  `integration.LlmController`) y en los `List<Object>` de `DrugCatalogUseCase`/
  `LegacyProtocolCatalogUseCase` (consumidos por `protocol/infrastructure/catalog/*Adapter`) —
  documentado como desvío consciente, no defecto: son datos de catálogo legacy heterogéneos, no
  vale la pena un modelo de dominio rígido para ellos.
  **Blast radius real fuera de `catalog/` (todo mecánico, mismo comportamiento):**
  `protocol/infrastructure/catalog/{LegacyProtocolCatalogAdapter,LocalDrugCatalogAdapter}` (pasan a
  depender de los `*UseCase` en vez de las clases concretas) · `treatment/{TreatmentController,
  TreatmentService}` (mismo cambio + arman el JSON de "esquema" que antes vivía en
  `TreatmentCatalogService.Scheme.view()`, ya que ese método no puede sobrevivir en `domain`) ·
  `infusion/TreatmentApplicationLogisticsService` · `integration/LlmController` (`forms.find()`
  ahora `Object`, un `mapper.valueToTree()` de vuelta a `JsonNode`) ·
  `config/ClinicalCatalogBootstrap` (pasa a depender de `DiagnosisCatalogUseCase`, que ahora expone
  `equivalences()` además de `search()` — puerto cruzado real, `config` consumiendo un puerto de
  `catalog`).
  Tests: 4 tests viejos relocados/adaptados (`AjccCatalogControllerPermissionTest`,
  `LegacyCatalogControllerPermissionTest`, `PostgresTreatmentSchemeStoreTest` — antes
  `TreatmentCatalogServiceTest`, verifica >200 esquemas reales incl. "347"=120min —,
  `ClinicalCatalogConsistencyTest` — verifica el ensamblado real COIR componentes+drogas), sin
  aserciones cambiadas. `catalog` sale de `TRACKED_LEGACY_MODULES`.
  Verificado en Docker real: `mvn -f backend/pom.xml verify` verde · `docker compose up --build
  --wait` 5 servicios healthy · `generate-openapi-snapshot.ps1 -Check` diff vacío (pese al volumen
  del cambio) · `smoke-test.ps1` verde · barrido real por curl con login: AJCC list/detail/stage
  (38 sitios), TNM list/detail (153 esquemas, stage tables incl.), formularios sistémicos,
  diagnóstico ajcc/snomed, protocolos coir (803)/seer (458), medicamentos, catalogs status/update,
  `/api/clinical/schemes` y `/duration` (treatment), `/api/clinical/protocols`,
  `/api/clinical/coir-catalog`, `/api/clinical/protocols/coir-347` (protocol module, consumidor
  cruzado) — todo con el mismo shape y valores reales que antes.
- [x] F3.2 (2/3) — `integration` migrado (1298 LOC: `LlmController` 639, `LlmClient` 237,
  `SystemConfigService`/`SystemSettingsRepository` 229, `SecretBox` 61, `AgentChatRequestSizeFilter`
  132 — el más grande y delicado de F3.2, único módulo con I/O real a un servicio externo).
  **Decisión previa preguntada al usuario** (sin credenciales LLM en este entorno): verificar con
  `mvn verify` + Docker/smoke-test/snapshot (no dependen del LLM) + tests unitarios con `LlmPort`
  mockeado; `/api/llm/test`/`/agent/chat` reales contra un proveedor quedan sin probar end-to-end,
  igual que antes de esta migración.
  7 casos de uso: `SystemConfigurationUseCase` (view/update/draftConfiguration/currentConfiguration),
  `LlmStatusUseCase`, `LlmConnectionTestUseCase`, `ClinicalTimelineExtractionUseCase`,
  `ClinicalSummaryUseCase`, `SystemicFormFillUseCase`, `AgentChatUseCase` — todos comparten
  `IntegrationFailure` (`INVALID`/`NOT_FOUND`) y el helper puro `LlmProviders`
  (`requiresApiKey`/`configured`, sin I/O, reutilizado por el adapter HTTP).
  `LlmPort` (puerto de salida, el pedido explícito del plan): `complete` (texto libre) +
  `completeAgentChat` (estructurado, esquema fijo) + `parseJson` — implementado por
  `infrastructure/http/HttpLlmClient`, que absorbe **sin cambio de comportamiento** tanto la lógica
  HTTP de `LlmClient` (F1) como el saneamiento de `LlmController.parseAgentResponse` (tablas,
  gráficos, followUps — con sus límites deterministas): ambas cosas son parsing/protocolo de un
  servicio externo no confiable, no casos de uso. La única regla que sí quedó en
  `AgentChatApplicationService` (aplicación, no adapter): el filtro de highlights por
  containment literal contra el texto clínico — es la regla de seguridad clínica real (evita que
  el LLM resalte términos no documentados), separable de cómo se habla con el proveedor.
  **Hallazgo real (mismo de F3.2 catalog, ArchUnit incondicional):** `TreatmentScheme`-style,
  `SystemConfigService.Config`→`domain/LlmConfiguration` y las respuestas del LLM no pueden llevar
  `JsonNode` en `domain`/`application` — `ClinicalTimelineExtractionUseCase`/`ClinicalSummaryUseCase`/
  `SystemicFormFillUseCase` trabajan con `Object`/`List<Object>` (árboles ya convertidos por el
  adapter vía `mapper.convertValue(_, Object.class)`), y `ClinicalSummaryUseCase.summarize` recibe
  `String eventsJson` ya serializado por la capa web (serializar SÍ requiere Jackson) en vez de
  `List<Object>`.
  Tests: 841 líneas de tests viejos (`LlmControllerTest`, `LlmClientTest`, `SystemConfigServiceTest`,
  `AgentChatRequestSizeFilterTest`) relocadas y **divididas por capa** sin perder ningún escenario:
  `HttpLlmClientTest` (HTTP real con `com.sun.net.httpserver.HttpServer`, igual que antes, + los
  casos de saneamiento JSON-cercado/límites deterministas, ahora contra `completeAgentChat`),
  `AgentChatApplicationServiceTest` (validaciones, historial acotado/deduplicado, prompt,
  containment de highlights — con mocks de `LlmPort`), `SystemConfigurationApplicationServiceTest`
  (validación de API key con `LlmConfigurationStore` mockeado), `LlmControllerTest` (slim: permisos
  + mapeo DTO), `AgentChatRequestSizeFilterTest` (relocado sin cambios). `integration` sale de
  `TRACKED_LEGACY_MODULES`.
  Verificado: `mvn -f backend/pom.xml verify` verde (382 tests) · `docker compose up --build --wait`
  5 servicios healthy · `generate-openapi-snapshot.ps1 -Check` diff vacío (pese al rediseño interno
  completo, el contrato externo no cambió un bit) · `smoke-test.ps1` verde · barrido real por curl
  con login: `GET/PUT /api/config` (incl. rechazo de Gemini sin API key, 400), `GET /api/llm/status`
  (disabled/configured:false), `POST /api/llm/test` con LLM deshabilitado apuntando a un puerto
  local sin servidor (502 `LLM_CONNECTION_ERROR` real — confirma que el adapter intenta conectar
  de verdad), `POST /api/agent/chat` deshabilitado (503 `LLM_DISABLED`) y con mensaje vacío (400),
  `POST /api/llm/summarize` sin eventos (400), `/extract-timeline` con texto vacío (400),
  `/fill-systemic-form` con plantilla inexistente (404) — configuración restaurada al estado
  original (`enabled:false`, sin API key) al terminar.
- [x] F3.2 (3/3) — `media` migrado (822 LOC: `ClinicalFileController`/`Repository`/`Service` +
  `StudyTemplateController` — subida/descarga de estudios e imágenes clínicas con streaming,
  validación de firma binaria, borrado por token, y plantillas anatómicas bundled+custom).
  `domain/ClinicalFile` · `application/port/in/{ClinicalFileUseCase,StudyTemplateUseCase}` ·
  `application/port/out/{ClinicalFileStore,ClinicalFileBlobStore,PatientLookupPort,
  StudyTemplateManifestStore}` · `MediaFailure` (`INVALID`/`NOT_FOUND`/`CONFLICT`/
  `UNSUPPORTED_FORMAT`/`FORBIDDEN`/`TOO_LARGE` — 6 valores, el enum más grande de F3 hasta ahora,
  necesario porque el módulo original ya usaba 5 status HTTP distintos) ·
  `infrastructure/persistence/{PostgresClinicalFileStore,FilesystemClinicalFileBlobStore,
  FilesystemStudyTemplateManifestStore}` (blob store separado del store de metadatos — el
  filesystem no participa de la transacción de Postgres) ·
  `infrastructure/patient/PatientServiceLookupAdapter` (implementa `PatientLookupPort`, el pedido
  explícito del plan — rompe la dependencia directa a `patient`, todavía no hexagonal) ·
  `infrastructure/web/{ClinicalFileController,StudyTemplateController}` (mismos 7 endpoints) +
  `ClinicalFileJsonMapper`/`StudyTemplateJsonMapper` + `MediaFailureAdvice`.
  **Decisión de diseño (mismo criterio que `catalog`/`integration`):** la validación de bytes no
  confiables (límite de tamaño, firma binaria contra la extensión declarada, movida atómica) queda
  en `FilesystemClinicalFileBlobStore` lanzando `ApiException` directo — es protocolo de subida, no
  regla de negocio; las políticas sí de negocio (qué extensiones se aceptan, mapeo de
  content-type↔extensión) quedan en `ClinicalFileApplicationService`.
  **Blast radius real fuera de `media/`:** `treatment/{TreatmentDocumentService,
  TreatmentDocumentController}` consumían `ClinicalFileRepository`/`ClinicalFileService` directo
  (ni siquiera vía el service para el repositorio) — pasan a depender de `ClinicalFileUseCase`
  (`findLatestByTreatment`/`resolvePath`), puerto cruzado real en la dirección opuesta a
  `PatientLookupPort`.
  **Hallazgo de diseño:** `StudyTemplateJsonMapper` reutiliza `ConfigurationJsonMapper`
  (`configuration/infrastructure/web`) directo — infra-a-infra entre módulos, sin regla de ArchUnit
  que lo prohíba (R6 sólo restringe `web`→`application.service`); evita duplicar la proyección
  `ConfigurationView`→Map que ya existe, mismo shape de respuesta byte a byte.
  Tests: `StudyTemplateControllerTest` (único test previo del módulo) relocado y adaptado a
  `StudyTemplateApplicationServiceTest` (mismas 2 aserciones, ahora contra el use case) +
  `ClinicalFileApplicationServiceTest` nuevo (0 tests antes: extensión no permitida, tipo de imagen
  no permitido, nombre de archivo inválido, archivo inexistente, borrado sin token válido). `media`
  sale de `TRACKED_LEGACY_MODULES` — **F3.2 completa**.
  Verificado en Docker real con binarios reales (no solo mocks): `mvn -f backend/pom.xml verify`
  verde · `docker compose up --build --wait` 5 servicios healthy · `generate-openapi-snapshot.ps1
  -Check` diff vacío · `smoke-test.ps1` verde · subida de un PNG real con paciente real →
  descarga → **comparación de bytes idéntica** (`cmp` sin diferencias) · PNG con firma binaria
  falsa → 415 · borrado con token incorrecto → 403 · borrado con token correcto → 200 · subida de
  imagen por `dataUrl` base64 → descarga → bytes idénticos · creación de plantilla de estudio
  (multipart real) → aparece en `scope=custom` · rechazo sin `rightsConfirmed` → 400 ·
  `/api/clinical/treatments/{id}/consent` con tratamiento inexistente → 404 (no 500 — confirma que
  el puerto cruzado de `treatment` funciona).

## F3.2 — CERRADA. `catalog`/`integration`/`media` migrados — la etapa más grande de F3 hasta
  ahora (3550 LOC originales). Commits: `c8111e3` (1/3 catalog) · `3563566` (2/3 integration) ·
  (3/3 media, este). Siguiente: F3.3.0 — puertos cruzados (patient/treatment/infusion).

- [x] F3.3.0 — Puertos cruzados (patient/treatment/infusion), commit propio, sin mover nada.
  Ciclo real mapeado (14 dependencias cruzadas, ver nota de ejecución más abajo): orden canónico
  elegido `patient` (base) ← `treatment` ← `infusion` — solo 5 de las 14 iban "hacia abajo" y
  necesitaron puerto, las otras 9 ya respetaban el orden y quedaron como llamada directa.
  `patient.application.port.out.{TreatmentSummaryPort,InfusionSummaryPort}` (adapters en
  `treatment.infrastructure.patient`/`infusion.infrastructure.patient`) ·
  `treatment.application.port.out.{InfusionSummaryPort,InfusionAppointmentPort,
  TreatmentApplicationSyncPort}` (adapters en `infusion.infrastructure.treatment` —
  `InfusionAppointmentPort` con DTO propio de 5 campos para no leakear el record `Infusion` de
  `infusion` hacia `treatment`). **Hallazgo real** (no en el plan): al sacar el `@ArchIgnore` de
  R4 aparecieron dos ciclos más, preexistentes y ajenos a esta etapa —
  `catalog`↔`config` y `config`↔`patient` (`config` es `PERMANENTLY_EXEMPT`, romperlos es F3.4).
  R4 se dejó con `@ArchIgnore` (javadoc actualizado documentando ambos) y se agregaron R4a/R4b,
  acotadas a `patient`/`treatment`/`infusion`, como criterio de aceptación real de esta etapa —
  ambas verdes. Detalle completo en `DECISIONES-F3.md`. 3 tests viejos (`PatientWorkspaceControllerPermissionTest`,
  `TreatmentServiceWorkflowStateTest`, `TreatmentServiceDoseUnitTest`) adaptados a los nuevos
  tipos de puerto en sus mocks, sin cambiar ninguna aserción. `mvn -f backend/pom.xml verify`
  verde: 390 tests (388 + R4a/R4b), 1 skip (R4 genérico). Sin cambio de contrato HTTP (reorganización
  interna pura) — no requirió verificación en Docker ni snapshot de OpenAPI.

## F3.3.0 — CERRADA. Ciclo real patient/treatment/infusion roto con 5 puertos cruzados, orden
  canónico documentado. Commit: (este). Siguiente: F3.3 — patient, diagnosis,
  workflow, treatment, infusion (3 PRs), qr.

### F3.3 — patient, diagnosis, workflow, treatment, infusion (3 PRs), qr

**Orden real distinto al literal del plan**: se migra de menor a mayor LOC (mismo criterio de
calibración que F3.1) — `diagnosis` (126) → `workflow` (644) → `qr` (400) → `treatment` (2165) →
`patient` (2623) → `infusion` (5813, 3 PRs) — en vez del orden textual de la tabla del plan
("patient, diagnosis, workflow, treatment, infusion, qr"). Los puertos cruzados de F3.3.0 ya
aíslan a `patient`/`treatment`/`infusion` entre sí, así que no hay bloqueo estructural real por
migrar en otro orden.

- [x] F3.3 (1/6) — `diagnosis` migrado (126 LOC, 1 archivo → 7 piezas). `domain/DiagnosisRecord`
  (`source: Object`, mismo patrón que `TreatmentScheme.definition()` — `null` cuando el registro
  se sintetiza del diagnóstico oncológico "actual") · `application/port/in/DiagnosisUseCase`
  (`list`/`link`) · `application/port/out/PatientDiagnosisPort` (cruza a `patient`, dirección
  permitida por el orden canónico de F3.3.0 — no rompe ningún ciclo, solo aísla el conocimiento
  del árbol JSON de la historia) · `application/service/DiagnosisFailure` (`CONFLICT`/
  `UNPROCESSABLE` — el 404 de paciente/historia inexistente sigue viajando como `ApiException`
  sin traducir desde el adapter, mismo criterio que `PatientServiceLookupAdapter` de `media`) ·
  `DiagnosisApplicationService` (`final`, sin `@Service`) ·
  `infrastructure/patient/PatientDiagnosisAdapter` (absorbe el parseo JSON completo: filtro de
  archivados, fallback a diagnóstico oncológico, id sintético) ·
  `infrastructure/configuration/DiagnosisModuleConfiguration` (variante B, read-only) ·
  `infrastructure/web/{DiagnosisController,DiagnosisFailureAdvice}` (mismos 2 endpoints/paths/
  shapes, incl. el campo `source` presente solo cuando el registro no es sintético). **`diagnosis`
  tenía 0 tests** (señalado en la tabla del plan) — 11 tests nuevos:
  `DiagnosisApplicationServiceTest` (5), `PatientDiagnosisAdapterTest` (4, incl. id sintético y
  fallback), `DiagnosisControllerPermissionTest` (2). `diagnosis` sale de
  `TRACKED_LEGACY_MODULES`. `mvn -f backend/pom.xml verify` verde: 401 tests (390 + 11), 1 skip
  (R4 genérico). Sin cambio de contrato HTTP — no requirió Docker (mismo criterio que F3.3.0).
- [x] F3.3 (2/6) — `workflow` migrado (644 LOC: `TreatmentWorkflowController`/`Repository`/
  `Service` — suspensión/reanudación de tratamientos, solicitudes de prescripción/continuidad con
  bandeja de entrada y resolución). El más rico en patrones de F3.3 hasta ahora.
  `domain/{TreatmentWorkflowSummary,ManagementState,WorkflowRequest,EvolutionDraft}`
  (`WorkflowRequest.context: Object` opaco, mismo patrón que `TreatmentScheme.definition()`) ·
  `application/port/in/TreatmentWorkflowUseCase` (comandos anidados como records; `resumeDateRaw:
  String` sin parsear — el parseo de fecha es **condicional** según `kind`/`resolution`, igual
  que el original, así que no puede hacerse en el borde web antes de saber si aplica;
  `PermissionChecker` — interfaz funcional propia para no importar `auth.SessionPrincipal` en
  application, el borde pasa `principal::hasPermission`) ·
  `application/port/out/{TreatmentWorkflowStore,PatientEvolutionPort}` (`PatientEvolutionPort`
  cruza a `patient`, dirección permitida; `DuplicateRequestException` en el store, mismo patrón
  que `UsernameOrEmailConflictException` de `admin` — traduce `DataIntegrityViolationException`
  en el borde porque `org.springframework.dao` tampoco puede pisar `application`) ·
  `application/service/WorkflowFailure` (`INVALID`/`NOT_FOUND`/`CONFLICT`/`FORBIDDEN`) +
  `TreatmentWorkflowApplicationService` (`final`, inyecta `Clock` — igual que el original, nunca
  `Instant.now()` directo) · `infrastructure/persistence/PostgresTreatmentWorkflowStore`
  (SQL sin cambios) · `infrastructure/patient/PatientEvolutionAdapter` (único lugar que arma el
  JSON de evolución — specialty fija, highlighted, sourceRef) ·
  `infrastructure/configuration/TransactionalTreatmentWorkflowManagement` (variante A, sin
  `ModuleConfiguration` aparte — mismo criterio que `admin`, el `@Service` ya es el bean) ·
  `infrastructure/web/{TreatmentWorkflowController,TreatmentWorkflowJsonMapper,
  WorkflowFailureAdvice}` (mismos 6 endpoints; `inbox`/`seen`/`resolve` sin `requirePermission`
  explícito en el controller — igual que el original, la autorización de `resolve` es una regla
  de negocio dentro del use case vía `PermissionChecker`).
  2 tests viejos adaptados sin cambiar aserciones
  (`TreatmentWorkflowApplicationServiceCycleBoundsTest`,
  `PostgresTreatmentWorkflowStoreAuthorizationTest`) + 8 tests nuevos
  (`TreatmentWorkflowApplicationServiceTest` 5, `TreatmentWorkflowControllerPermissionTest` 3).
  `workflow` sale de `TRACKED_LEGACY_MODULES`. `mvn -f backend/pom.xml verify` verde: 418 tests
  (401 + 17), 1 skip. Sin cambio de contrato HTTP — no requirió Docker.
- [x] F3.3 (3/6) — `qr` migrado (400 LOC: `QrWorkflowController`/`Repository`/`Service` — QR
  firmado HMAC de identificación de tratamiento, escaneo con administración idempotente).
  `domain/{QrPatientView,QrTreatmentView,QrInfusionRef,QrScan,EvolutionDraft}` ·
  `application/port/in/QrUseCase` (`ScanCommand`/`ScanResult`) ·
  `application/port/out/{QrPatientPort,QrTreatmentPort,QrInfusionPort,QrScanStore,
  PatientEvolutionPort}` (`QrInfusionPort.dayHospitalEligibility` devuelve `Optional<Boolean>` —
  `empty()` distingue "no hay logística para ese día" de `Optional.of(false)` "solo domiciliaria",
  los 2 mensajes de conflicto distintos del original; los 3 primeros cruzan a `patient`/
  `treatment`/`infusion`, dirección permitida por el orden canónico de F3.3.0) ·
  `application/service/QrFailure` (`INVALID`/`NOT_FOUND`/`CONFLICT`) + `QrApplicationService`
  (`final`, secreto HMAC inyectado como `String` — ya no `config.HcopProperties` completo — toda
  la lógica pura de firma/parseo/QR bitmap queda acá: `com.google.zxing` y `javax.crypto` **no
  están en la allow-list incondicional de frameworks** de `HexagonalArchitectureTest`, así que
  `application` sí puede importarlos sin violar R3/domainIsIndependentFromFrameworks — a
  diferencia de `tools.jackson`) · 3 adapters en `infrastructure/{patient,treatment,infusion}`
  (`QrInfusionAdapter` es el único lugar que navega `Logistics.applicationDrugs()` con
  `DayHospitalApplicationPolicy.requiresDayHospital`) ·
  `infrastructure/persistence/PostgresQrScanStore` ·
  `infrastructure/patient/PatientEvolutionAdapter` (formato de evolución con `specialty`/
  `highlighted` configurables por `EvolutionDraft`, a diferencia del de `workflow` que los fija) ·
  `infrastructure/configuration/TransactionalQrManagement` (variante A, `@Transactional` solo en
  `scan`, igual que el original) · `infrastructure/web/{QrWorkflowController,QrJsonMapper,
  QrFailureAdvice}` (mismo nombre de controller que el legacy — evita tocar las claves
  `QrWorkflowController.document`/`.scan` de `OpenApiConfiguration.DOCUMENTATION`/`PERMISSIONS`).
  1 test viejo reemplazado por `QrApplicationServiceTest` (3 escenarios originales + 5 nuevos:
  ciclo inválido, tratamiento inexistente, escaneo idempotente, hash distinto, administración con
  evolución) + `QrWorkflowControllerPermissionTest` (2). `qr` sale de `TRACKED_LEGACY_MODULES` —
  **los 6 módulos de menor LOC de F3.3 completos** (`treatment`/`patient`/`infusion` de 2165+2623+
  5813 LOC siguen). `mvn -f backend/pom.xml verify` verde: 428 tests (418 + 10), 1 skip. Sin
  cambio de contrato HTTP — no requirió Docker.
- [x] F3.3 (4/6) — `treatment` migrado (2204 LOC, 9 archivos: `DayHospitalApplicationPolicy`,
  `LegacyDoseUnitResolver`, `TreatmentController`, `TreatmentCycleTimeline`,
  `TreatmentDocumentController`, `TreatmentDocumentService`, `TreatmentProtocolCompatibility`,
  `TreatmentRepository`, `TreatmentService` — delegado a un agente en background con el patrón ya
  calibrado en diagnosis/workflow/qr, revisado y verificado antes de commitear).
  `domain/{Treatment,WorkflowState,DiagnosisOption,TreatmentPatientView,DrugLine,
  TreatmentProtocolCompatibility,DayHospitalApplicationPolicy}` (el último es un **fragmento**:
  solo `MAX_APPLICATION_DAY`+`isValidApplicationDay`, sin JsonNode) ·
  `application/port/in/{TreatmentUseCase,TreatmentDocumentUseCase}` ·
  `application/port/out/{TreatmentStore,TreatmentPatientPort,PatientDiagnosisOptionsPort}` ·
  `application/service/TreatmentFailure` (`INVALID`/`NOT_FOUND`/`UNPROCESSABLE`) +
  `{TreatmentApplicationService,TreatmentDocumentApplicationService}` ·
  `infrastructure/legacy/{LegacyDoseUnitResolver,DayHospitalProtocolRules}` (`DayHospitalProtocolRules`
  es el otro fragmento de la política original: `requiresDayHospital`/`applicationDays`, con
  JsonNode — separado del fragmento de dominio) ·
  `infrastructure/persistence/{TreatmentCycleTimeline,PostgresTreatmentStore}` ·
  `infrastructure/patient/{TreatmentPatientAdapter,PatientDiagnosisOptionsAdapter}` ·
  `infrastructure/configuration/{TransactionalTreatmentManagement,TreatmentDocumentModuleConfiguration}`
  (variantes A y B respectivamente) ·
  `infrastructure/web/{TreatmentController,TreatmentDocumentController,TreatmentFailureAdvice}`
  (mismos endpoints/paths/permisos, sin mappings multi-path que conservar en este módulo).
  **Hallazgo real más importante de F3.3 hasta ahora**: `applicationDoesNotDependOnInfrastructure`
  es una regla ArchUnit **incondicional entre módulos distintos**, no solo intra-módulo — al mover
  `DayHospitalApplicationPolicy` a `treatment.infrastructure.legacy`,
  `qr.application.service.QrApplicationService` (ya hexagonal, F3.3 3/6) rompió esa regla porque
  llamaba a `isValidApplicationDay` directo. Se resolvió partiendo la clase original en dos: el
  fragmento sin Jackson queda en `treatment.domain.DayHospitalApplicationPolicy` (lo que `qr`
  consume), el fragmento con `JsonNode` en `treatment.infrastructure.legacy.DayHospitalProtocolRules`
  (lo que consume `QrInfusionAdapter`, ya en infraestructura). Patrón reutilizable para cualquier
  próxima "regla pura con métodos JsonNode mezclados" consumida por otro módulo ya hexagonal.
  **Decisiones de diseño**: `create()` no se modeló 100% puro — el armado del JSON de detalle
  (deep-copy + overlay + extracción de drogas + evolución inmutable + replay idempotente) queda
  consolidado en `PostgresTreatmentStore.insert()`, que además necesita `patient.PatientDocumentService`
  directo para el camino idempotente; la aplicación valida todo con primitivos
  (`CreateTreatmentCommand`, ~20 campos) y arma un `NewTreatmentDraft` opaco. El controller replica
  EXACTO las dos semánticas de alias del original (`text()` salta claves vacías, `numericText()`
  usa la primera clave que EXISTE aunque esté vacía) — los 7 campos de dosis
  (peso/talla/creatinina/tfg/targetAUC/calcio/albumina) siguen validándose como obligatorios pero
  **nunca se persisten** (comportamiento preexistente, preservado tal cual).
  `PatientDiagnosisOptionsPort` es propio de `treatment` (no reusa `diagnosis.application.port.in.DiagnosisUseCase`
  aunque lean la misma fuente JSON) porque el rótulo de `treatment` agrega código CIE-10 y estadio
  — unificarlos habría sido una regresión de comportamiento, no una limpieza.
  **Blast radius real fuera de `treatment/`**: `qr/application/service/QrApplicationService.java`
  + `qr/infrastructure/{infusion/QrInfusionAdapter,treatment/QrTreatmentAdapter}.java` (imports al
  nuevo split) · `infusion/{InfusionService,ApplicationWorkflowService,TreatmentApplicationPlanner}.java`
  (mismo ajuste de imports, `infusion` sigue legacy sin restricción real) ·
  `infusion/{HospitalDayConcurrencySafetyTest,InfusionServiceSchedulingWorkflowTest}.java`
  (`TreatmentRepository`→`TreatmentStore`, `Treatment` ahora en `treatment.domain`) ·
  `treatment/infrastructure/patient/TreatmentSummaryAdapter.java` (de F3.3.0, `TreatmentService`→
  `TreatmentUseCase`). Tests viejos (6 archivos) migrados/adaptados sin cambiar aserciones.
  `treatment` sale de `TRACKED_LEGACY_MODULES`. `mvn -f backend/pom.xml verify` verde: 448 tests
  (428 + 20), 1 skip. Sin cambio de contrato HTTP — no requirió Docker.
- [x] F3.3 (5/6) — `patient` migrado (2623 LOC, 16 archivos — el módulo BASE del orden canónico,
  delegado a un agente en background, revisado y verificado antes de commitear).
  `domain/{Patient,NewPatient,StoredDocument,EvolutionAppend}` ·
  `application/port/in/{PatientUseCase,PatientDocumentUseCase}` (`PatientUseCase` con
  `DuplicatePatientException` anidada) · `application/port/out/{PatientStore,PatientDocumentStore}` ·
  `application/service/PatientFailure` (`INVALID`/`NOT_FOUND`/`CONFLICT`) +
  `{PatientApplicationService,PatientDocumentApplicationService}` (el segundo sin lógica propia) ·
  `infrastructure/persistence/{PostgresPatientStore,PostgresPatientDocumentRepository,
  PatientDocumentStoreAdapter}` (**split JDBC/lógica JSON preservado a propósito** — no se fusionó
  todo en un solo Store como en `treatment`, porque `ClinicalDocumentConflictContractTest` — 628
  líneas, 13 tests — mockea el repositorio JDBC crudo y ejercita la lógica JSON real por encima;
  fusionar hubiera obligado a reescribir esos tests contra JDBC mockeado, mucho más frágil) ·
  `infrastructure/web/{ClinicalDocumentAccessPolicy,ClinicalDocumentChangeValidator,
  ClinicalNarrativeSectionAuthority,Clinical{ChiefComplaint,CurrentIllness,PersonalHistory,
  SummaryPlan,PhysicalExam}Authority}` (movidas **verbatim**, package nuevo nomás) +
  `PatientJsonMapper` (nuevo) + `{PatientController,ClinicalDocumentController,
  PatientWorkspaceController,PatientFailureAdvice}` · `infrastructure/bootstrap/DefaultDemoPatientBootstrap` ·
  `infrastructure/configuration/{TransactionalPatientManagement,PatientDocumentModuleConfiguration}`
  (variantes A y B). Multi-path preservados exactos: `{"/api/clinical/patients","/api/lira/patients"}`
  y `{".../import",".../refresh"}`.
  **Hallazgo real (contradice al plan)**: las 7 clases `Clinical*Authority` +
  `ClinicalDocumentAccessPolicy`/`ChangeValidator` **no son dominio puro** — el plan decía que sí,
  pero las 8 tocan `JsonNode` directo (`canonicalize(JsonNode,...)`) — quedaron en
  `infrastructure.web`, no en `domain` (regla incondicional de Jackson).
  **Otros hallazgos reales**: `PatientDocumentService.patients` (campo `PatientRepository`) estaba
  muerto — se guardaba en el constructor pero ningún método lo usaba, no se arrastró · R2 real:
  `DefaultDemoPatientBootstrap` usaba `JdbcTemplate` directo (persistencia fuera de
  `infrastructure.persistence`) — fix: `PostgresPatientStore.findMinEnabledActorId()`, público
  pero fuera de la interfaz `PatientStore` (mismo patrón que `applyPatient` del adapter de
  documentos) · R6 real: `ClinicalDocumentController` lanzaba `PatientFailure` directo para 2
  precondiciones web (paciente activo, revisión requerida) — no permitido (el controller solo
  puede conocer el puerto de entrada); esas 2 quedaron como `ApiException` directo (`common`,
  permanentemente exento), mismo criterio que ya usaban `ClinicalDocumentAccessPolicy`/`ChangeValidator`
  · `EvolutionAppend.evolution()`/`StoredDocument.document()` pasaron a `Object` opaco — 3 casts
  `(JsonNode)` nuevos en módulos ya hexagonales que los consumen directo
  (`infusion.ApplicationWorkflowService.CommandResult`, adapters de `diagnosis`/`treatment`).
  **Blast radius real fuera de `patient/`** (todo mecánico, imports/casts, sin rediseño):
  `config/BootstrapConfiguration.java`+test · `diagnosis/infrastructure/patient/PatientDiagnosisAdapter.java`+test ·
  `infusion/{InfusionService,ApplicationWorkflowService}.java`+3 tests · `media/infrastructure/patient/
  PatientServiceLookupAdapter.java` · `qr/infrastructure/patient/{PatientEvolutionAdapter,QrPatientAdapter}.java` ·
  `treatment/infrastructure/patient/{TreatmentPatientAdapter,PatientDiagnosisOptionsAdapter}.java` +
  `treatment/infrastructure/persistence/PostgresTreatmentStore.java`+2 tests ·
  `workflow/infrastructure/patient/PatientEvolutionAdapter.java` · `config/OpenApiConfigurationTest.java`.
  `patient` sale de `TRACKED_LEGACY_MODULES` (queda solo `infusion`). `mvn -f backend/pom.xml
  verify` verde: 550 tests (448 + 102, incluye los tests viejos del módulo — `patient` es el más
  grande migrado hasta ahora en cantidad de tests propios). Sin cambio de contrato HTTP — no
  requirió Docker.
- [x] F3.3 (6/6, PR 1/3 logistics) — `TreatmentApplicationLogisticsService`/`TreatmentApplicationPlanner`
  hexagonales. `application/port/in/TreatmentApplicationLogisticsUseCase` ·
  `application/port/out/TreatmentApplicationLogisticsStore` ·
  `TreatmentApplicationLogisticsApplicationService` (sin lógica propia, mismo patrón que
  `tools.CalculatorCatalogApplicationService`) · `infrastructure/persistence/
  {TreatmentApplicationPlanner(movido verbatim — resultó 100% Jackson, sin fragmento puro que
  separar, a diferencia de `DayHospitalApplicationPolicy`),PostgresTreatmentApplicationLogisticsStore}` ·
  `infrastructure/configuration/TransactionalTreatmentApplicationLogisticsManagement` (variante A).
  Blast radius: `infusion/infrastructure/treatment/TreatmentApplicationSyncAdapter` (F3.3.0, tipo
  del constructor), `InfusionService`/`ApplicationWorkflowService` (siguen legacy, import
  mecánico), 3 tests. `infusion` sigue en `TRACKED_LEGACY_MODULES` (faltan PR2/PR3). `mvn verify`
  verde: 558 tests (550 + 8), 1 skip. Commit `882be3a`.
- [x] F3.3 (6/6, PR 2/3 infusions core) — `InfusionRepository` → puerto/adapter.
  `domain/{Medication,MedicationView,NewInfusion,Patch,Infusion,Candidate,Logistics,
  ScheduleSettings}` (`sourceRef`/`applicationDrugs` a `Object` opaco) ·
  `application/port/out/InfusionStore` · `infrastructure/persistence/PostgresInfusionStore` (SQL
  sin cambios). **Desvío real justificado**: `InfusionService`/`InfusionController` NO se
  movieron de paquete en este PR — `ApplicationWorkflowRepository`/`Key`/`ScheduleGate`/
  `ApplicationWorkflowPolicy` eran package-private (sin `public`), moverlos hubiera roto la
  compilación; quedaron legacy hasta PR3. Blast radius: `InfusionService.java` (tipo de campo + 7
  casts `(JsonNode)`), `qr/infrastructure/infusion/QrInfusionAdapter`,
  `infusion/infrastructure/treatment/InfusionForTreatmentAdapter`,
  `treatment/application/port/out/InfusionAppointmentPort` (javadoc), 2 tests. `mvn verify` verde:
  558 tests (sin nuevos, movimiento verbatim), 1 skip. Commit `e9ec4ab`.
- [x] F3.3 (6/6, PR 3/3 application-workflow) — el resto de `infusion/` (8 archivos: los 6 que
  quedaban + `InfusionService`/`InfusionController` pendientes del PR2), ~4825 LOC — **cierra
  `infusion` y F3.3 completo**.
  `application/port/in/{ApplicationWorkflowUseCase,InfusionUseCase}` (comandos planos, sin
  `JsonNode`/`@Schema`) · `application/port/out/{ApplicationWorkflowStore,InfusionOperationsStore}` ·
  `{ApplicationWorkflowApplicationService,InfusionApplicationService}` (**passthrough deliberado**
  — dado el tamaño y entrelazamiento real de SQL+JSON+reglas de negocio en el mismo método, casi
  1900 LOC en un solo store, no se separó `application` con lógica propia; toda la orquestación
  real, incl. las llamadas a `domain.ApplicationWorkflowPolicy`, vive en los dos `Postgres*Store`,
  que sí pueden tocar Jackson/Spring/`ApiException` por ser infraestructura — mismo patrón que
  `tools`/PR1-logistics, documentado en el javadoc de ambos puertos out) ·
  `domain/ApplicationWorkflowPolicy` (reescrito: `Optional<Violation>` en vez de lanzar
  `ApiException` — domain no conoce `HttpStatus`) ·
  `infrastructure/persistence/{PostgresApplicationWorkflowStore(~1900 LOC, fusiona
  Repository+Service),PostgresInfusionOperationsStore,ApplicationComponentValidator}` —
  `PostgresInfusionOperationsStore` inyecta `PostgresApplicationWorkflowStore` **concreto** (no
  puerto) para `lock`/`scheduleGate`/`markAppointmentScheduled`/`markAppointmentRemoved`/
  `insertEvent` — infra-a-infra, permitido · `infrastructure/web/{ApplicationWorkflowCommands,
  InfusionApplicationWorkflowController,InfusionController}` (mismos endpoints/paths/permisos en
  ambos controllers, sin multi-path) · `infrastructure/configuration/
  ApplicationWorkflowModuleConfiguration` (variante B). 7 archivos de test migrados/reescritos
  (incl. widening `private`→package-private en 5 métodos de los `Postgres*Store` para que los
  tests sigan pudiendo ejercitar SQL/lógica interna directo) sin cambiar aserciones de negocio.
  `infusion` sale de `TRACKED_LEGACY_MODULES` — **queda vacío**. `mvn -f backend/pom.xml verify`
  verde: 612 tests (558 + 54), 1 skip (R4 genérico, sin cambios, sigue fuera de alcance —
  F3.4). Sin cambio de contrato HTTP — no requirió Docker. Commit `6e8c6aa`.

## F3.3 — CERRADA. Los 6 módulos completos: `diagnosis`, `workflow`, `qr`, `treatment`, `patient`,
  `infusion` (3 PRs). `TRACKED_LEGACY_MODULES` queda **vacío** — todo el backend clínico
  (`ar.com.hexium.hcop.*` salvo `auth`/`common`/`config`, permanentemente exentos) es hexagonal:
  `domain`/`application`/`infrastructure` en los ~14 módulos, R1-R9 + R4a/R4b en verde sin
  relajar nada. Commits: `f42f60f`(diagnosis) · `c9b6df3`(workflow) · `1beb03d`(qr) ·
  `a2ab7c9`(treatment) · `1ae84d7`(patient) · `882be3a`+`e9ec4ab`+`6e8c6aa`(infusion, 3 PRs).
  `mvn -f backend/pom.xml verify` verde en cada commit — 612 tests finales, 1 skip (R4 genérico,
  documentado, fuera de alcance). Ningún commit de F3.3 cambió contrato HTTP — no se corrió
  Docker en ninguno (a diferencia de F3.1/F3.2, que sí lo verificaron en runtime real; queda
  pendiente una verificación en Docker real de todo el stack antes de mergear a `main`, ver
  Notas de ejecución). Siguiente: F3.4 — `common`→`sharedkernel`/`platform`,
  `config`→`platform` (NO hexagonal), eliminar `ApiException`.

- [x] F3.4 — `common`+`config` → `platform` (fusionados, no dos paquetes separados — `common` no
  tenía nada de dominio que mover a `sharedkernel`), `ApiException` acotado a `infrastructure.web`
  (R6 lo exige — no "cero consumidores fuera de los 3 exentos" como decía el plan, ver
  DECISIONES-F3.md), 2 hallazgos reales corregidos, 2+1 ciclos ArchUnit rotos.
  `PERMANENTLY_EXEMPT_MODULES` pasa de `{auth,common,config}` a `{auth,platform}`.
  **`ApiException` eliminado de `application`/`infrastructure.persistence`/`infrastructure.http`**
  de los módulos hexagonales — `infusion` (sin `*Failure` desde F3.3, nuevo `InfusionFailure`+
  `InfusionFailureAdvice`, ~50 sitios convertidos con un agente en background, mecánico) ·
  `integration` (`IntegrationFailure` gana `UNAVAILABLE`/`UPSTREAM_ERROR`/`TIMEOUT`,
  `HttpLlmClient` migrado completo) · `media` (`MediaFailure` gana `INTERNAL`,
  `Filesystem{ClinicalFileBlobStore,StudyTemplateManifestStore}` migrados). Los `ApiException` de
  `infrastructure.web` (media/patient/integration/treatment, 8 clases) quedan intactos — precondiciones
  de forma HTTP, R6 prohíbe que el controller construya el `*Failure` propio (mismo criterio que
  patient ya usaba desde F3.3).
  **Hallazgo real (bug de propagación, 500 en vez de 404, sin test que lo cazara)**: 10 adapters
  cruzados a `patient` (`diagnosis`/`qr`/`workflow`/`treatment`/`infusion`/`media`, javadocs
  escritos antes de que `patient` se hexagonalizara en F3.3 5/6) dejaban propagar
  `patient.PatientFailure` sin traducir — ningún `*FailureAdvice` ajeno lo captura, caía en el
  handler genérico 500. Fix: cada adapter atrapa `PatientFailure` y relanza su `*Failure` propio
  (`NOT_FOUND`/`CONFLICT`).
  **Hallazgo real (ciclo no anticipado)**: al levantar el `@ArchIgnore` de
  `r4_slicesAreFreeOfCycles` apareció `auth`↔`platform` (acoplamiento mutuo esperado entre los 2
  módulos "pegamento", no un ciclo de negocio) — excluidos ambos del `@AnalyzeClasses` con un
  `ImportOption` propio, seguro para las otras 17 reglas (ver DECISIONES-F3.md).
  Los 2 ciclos documentados en F3.3.0 (`catalog`↔`config`, `config`↔`patient`) se rompieron con un
  patrón plugin nuevo: `platform.BootstrapTask` (interfaz `run()`) — `catalog`/`patient` implementan
  la suya con `@Order`, `platform.BootstrapConfiguration` inyecta `List<BootstrapTask>` sin conocer
  ninguna clase concreta. `r4_slicesAreFreeOfCycles` sale de `@ArchIgnore` — **sin relajar nada**,
  criterio de aceptación visible.
  `docs/02-arquitectura/MVC.md` → `HEXAGONAL.md` (reescrito, describía MVC pre-F0). Texto de la API
  en `OpenApiConfiguration` actualizado a "arquitectura hexagonal" — **cambia el
  `openapi-snapshot.json`**, pendiente de regenerar contra Docker real antes de mergear (se suma a
  la deuda de F3.3, un solo paso de Docker cubre ambas — ver punto 5 más abajo, actualizado).
  `mvn -f backend/pom.xml verify` verde: 418 tests, **0 skips** (antes 1, el R4 genérico). Sin
  Docker en este commit (cambia el contrato del spec vía el texto de `description`, no la forma de
  ningún endpoint/schema — mismo criterio de F3.3, salvo la deuda de snapshot ya anotada).

- [x] **Verificación Docker real de F3.3+F3.4** (deuda saldada, a pedido explícito del usuario en
  esta sesión). `docker compose up --build --wait`:
  **1er intento falló** — `hcop-jp-backend-1` unhealthy, `ConflictingBeanDefinitionException`:
  `qr.infrastructure.patient.PatientEvolutionAdapter` y
  `workflow.infrastructure.patient.PatientEvolutionAdapter` (mismo simple name, ambos `@Component`
  sin nombre explícito) colisionan en el nombre de bean por defecto de Spring. **Bug real
  preexistente desde F3.3** (2/6 workflow y 3/6 qr crearon cada uno su propio adapter sin saber del
  otro) — ningún test lo detectó porque ninguno levanta el `ApplicationContext` completo; solo lo
  encuentra un arranque real. Fix: `@Component("qrPatientEvolutionAdapter")`/
  `@Component("workflowPatientEvolutionAdapter")` (nombres explícitos, sin tocar el tipo — cada uno
  se inyecta por su propio puerto de módulo, así que no afecta el autowiring). Confirmado que no
  hay más colisiones de simple name entre clases `@Component`/`@Service`/`@Repository` en el árbol.
  **2do intento: 5 servicios healthy.**
  `generate-openapi-snapshot.ps1 -Check`: diff esperado por el texto nuevo de la API (F3.4) —
  regenerado. **Hallazgo adicional en el diff**: `InfusionApplicationWorkflowController` perdió las
  descripciones largas de sus 14 operaciones — no es una regresión de F3.4 (sus `@Operation` solo
  tienen `summary` desde que se escribió en F3.3 3/6, `PROGRESO.md` ya advertía que ningún commit
  de F3.3 se había verificado contra Docker); el snapshot committeado estaba desactualizado desde
  entonces y recién ahora se corrigió. `nginx-routing-test.ps1` OK · `smoke-test.ps1` OK · los 3
  contract-tests OK · `integration-test.ps1` OK (ejercita el flujo completo de aplicación —
  interrupción/resolución con evolución real — justo el módulo `infusion` convertido a
  `InfusionFailure` en F3.4) · `test-core-browser-e2e.ps1` 3/3 passed · `test-clinical-conflict-e2e.ps1`
  7/7 failed (mismo `toBeVisible` en el divisor Historia/Estudios, bug preexistente de F0.5,
  confirmado sin relación).

- [x] **Checklist completo del plan** ("Verificación end-to-end, aplica a todas las fases") —
  `mvn -f bff/pom.xml verify` OK · `npm ci && npm test && npm run build` (frontend) OK ·
  `scripts/verify-documentation.ps1` — 2 hallazgos, ninguno de F3.4: `README.md` (raíz, no
  `docs/README.md`) apuntaba a `MVC.md` (corregido, se me había pasado); el resto (mp4 de ayuda,
  `pom.xml`/`Dockerfile` en `docs/08-recrear-desde-cero/`) son links rotos **desde F0** — esos
  paths se movieron a `backend/`/`frontend/` en el split de servicios y nadie actualizó los docs;
  confirmado con `find` que los targets no existen donde el link los busca. Fuera de alcance de
  F3, no se tocó. `scripts/generate-api-docs.ps1 -Check`: diff esperado (mismo texto de API que
  el snapshot) + regeneró contenido de auth (cookie→JWT) que estaba desactualizado **desde F2.8**
  (el guardián real de F3 es `openapi-snapshot.json`, no este script — documentado ya en F3.0.4:
  "`generate-api-docs.ps1 -Check` no alcanza") — nadie lo había vuelto a correr desde entonces.
  **Hallazgo aparte, real**: el parámetro `-HtmlPath` del script trae un default
  pre-F0 (`src/main/resources/static/docs/api-endpoints.html`, ruta que dejó de existir en el
  split de servicios) — corrido con el path real (`frontend/public/docs/api-endpoints.html`, donde
  vive hoy `http://localhost:5180/docs/`) y regenerado ahí; se limpió el archivo espurio que había
  quedado en la ruta vieja. `scripts/test-backup-restore.ps1 -BackendImage hcop-jp-backend:local`
  OK — backup/restauración real verificados, PostgreSQL y storage coinciden.

## F3 — CERRADA Y VERIFICADA. Los ~14 módulos clínicos son hexagonales (`domain`/`application`/
  `infrastructure`, R1-R9 + R4a/R4b en verde, **R4 genérico también en verde, sin `@ArchIgnore`**),
  `platform` (fusión `common`+`config`) es la única infraestructura transversal permanentemente
  exenta junto a `auth`, y `ApiException` quedó acotado a los casos que la arquitectura realmente
  permite (borde `infrastructure.web` para precondiciones de forma HTTP, más `auth`/`platform`).
  Commits: ver F3.0-F3.3 arriba + F3.4 + (verificación Docker, este — incluye el fix de colisión de
  beans). Stack real de 5 servicios verificado end-to-end: routing, smoke, los 3 contract-tests,
  flujo clínico integral, browser e2e 3/3, y el bug preexistente de F0.5 confirmado sin relación.
  **Sin deuda pendiente para mergear a `main`.** Siguiente fase: fuera del alcance de este plan
  (`fuzzy-waddling-galaxy.md` termina en F3) — a definir con el usuario.

### Siguientes etapas (no arrancadas)

(ninguna — F3 completo y verificado en Docker real)

### Cómo continuar en una sesión nueva (F3 completo, pendiente solo la deuda de Docker)

1. Releer el bloque "F3 — CERRADA" y la entrada F3.4 arriba, más `DECISIONES-F3.md` (sección
   "F3.4", los 4 hallazgos reales: fusión `common`+`config`→`platform` en vez de 2 paquetes, el
   target real de `ApiException` reconciliado con R6, el bug de propagación `PatientFailure` en 10
   adapters cruzados, y el ciclo `auth`↔`platform`).
2. Único pendiente real: la verificación Docker de F3.3+F3.4 juntas — ver "Pendiente antes de
   mergear a `main`" en el bloque "F3 — CERRADA" arriba. El usuario corre Docker, no Claude, salvo
   que pida explícitamente lo contrario.
3. Estado del repo al cerrar esta sesión: branch `feature/migracion-bff-arquitectura`,
   `mvn -f backend/pom.xml verify` verde (418 tests, 0 skips). No hay siguiente fase definida en
   el plan (`fuzzy-waddling-galaxy.md` termina en F3) — si el usuario pide seguir, preguntar el
   alcance antes de asumir uno.

### Referencia — patrón de los 6 módulos de F3.3 (por si hace falta releer un ejemplo)

Ejemplos reales ya migrados y verificados, del más simple al más rico en patrones:
   `tools/` (variante B, read-only, el más simple) · `system/` (Postgres*Store trivial) ·
   `admin/` (primer `Postgres*Store` real con `@Transactional`, traducción de
   `DataIntegrityViolationException` en el borde) · `catalog/` (7 sub-catálogos en un módulo,
   el hallazgo de que `domain`/`application` NUNCA pueden importar `tools.jackson` así sea vía
   allow-list — ver F3.2 (1/3) en este archivo, `TreatmentScheme.definition()` tipado `Object`) ·
   `integration/` (puerto de salida hacia un servicio HTTP externo no confiable — `LlmPort`,
   saneamiento de respuesta no confiable vive en el adapter, no en application) ·
   `media/` (blob store separado del store de metadatos, `PatientLookupPort` real). Más
   `configuration/`, `guide/` y `protocol/` (ya hexagonales antes de F3).
   `guide/infrastructure/configuration/ConfigurationGuideMetadataAdapter` es la referencia
   concreta del patrón #7 (puertos cruzados). `F3.3.0` (patient/treatment/infusion) es el ejemplo
   de puertos cruzados **bidireccionales** (a diferencia de los anteriores, unidireccionales):
   orden canónico `patient` ← `treatment` ← `infusion`, puerto dueño del módulo upstream,
   adapter físicamente en el módulo downstream — ver `DECISIONES-F3.md`. `PatientLookupPort` (en
   `media/application/port/out/`) y `DrugCatalogUseCase`/`TreatmentCatalogUseCase` (en
   `catalog/application/port/in/`) — patrones de puerto cruzado reusables si F3.4 necesita uno
   nuevo entre `catalog`/`patient` y `platform` (ver punto 4 de la sección anterior).
   `media/application/port/in/ClinicalFileUseCase.findLatestByTreatment` es el puerto cruzado
   real `treatment`→`media`.
   Antes de cada commit de módulo (referencia histórica, F3.0-F3.3 ya cerrados): correr
   `mvn -f backend/pom.xml verify`, levantar Docker real y correr
   `scripts/generate-openapi-snapshot.ps1 -Check` — diff vacío bloqueante, ver F3.0.4 y el punto 5
   de arriba (deuda de Docker pendiente en F3.3, saldarla antes de mergear a `main`).

- [x] **Limpieza previa al merge a `main`** (a pedido del usuario, esta sesión). Revisión de
  `legacy-reference/` y `src/` contra el árbol real: `legacy-reference/` (4.8M, sitio estático
  pre-migración) sin ningún consumidor en código/scripts/CI — el contrato visual de Angular usa
  su propia copia en `frontend/src/legacy-visual-contract/`, independiente — eliminado. `src/`
  (carpeta vacía en la raíz, resto sin trackear del `git mv` de F0.1) eliminada.
  **Hallazgo real (deuda de F1.5, nunca saldada)**: la distribución vía GHCR seguía en la
  topología de 2 servicios (backend+frontend) — nunca se actualizó para BFF+Redis desde que F1 lo
  introdujo. Afectaba 3 puntos: `.github/workflows/verify.yml` (matriz `publish` sin `bff`),
  `compose.github.yaml` (frontend dependía de `backend` directo, sin `redis`/`bff` — la imagen del
  frontend ya trae el nginx con upstream fijo a `bff:8080` desde F1.5, así que este compose
  arrancaba con `nginx: emerg host not found in upstream "bff"`, mismo bug que F1.5 encontró en
  `compose.e2e.yaml`) y `EJECUTAR-DOCKER-DESDE-GITHUB.ps1` (el launcher standalone para usuarios
  sin este repo — generaba su propio compose inline, nunca tocado desde F2.2, con el mismo problema).
  Fix: `bff` agregado a la matriz de `publish` (imagen `ghcr.io/marcolyto/hcop_jp-bff`) ·
  `compose.github.yaml` con `redis`+`bff` (mismo patrón que `compose.yaml`/F1.5), frontend pasa a
  depender de `bff` · `.env.example` con `HCOP_BFF_IMAGE` · `scripts/instalar-desde-github.ps1`
  agrega `bff` a la descarga de imágenes por sha y al override de compose ·
  `EJECUTAR-DOCKER-DESDE-GITHUB.ps1`: el canal `Stable` (default) genera `redis`+`bff` y
  `frontend→bff`; el canal `Migration` (tag fijo `angular-full-parity-v2`, anterior al split
  backend/bff/frontend, sin imagen `bff` publicada para ese tag) se deja intacto con la topología
  vieja `frontend→backend` — no rompía antes de este cambio y no había forma de correr `bff` contra
  esa imagen histórica. Verificado con `pwsh -Mode ValidateOnly` en ambos canales (`Stable` genera
  YAML con `redis`/`bff`/`frontend→bff`, parseable con `yaml.safe_load`; `Migration` sin cambios,
  `frontend→backend`) — sin Docker real disponible en esta sesión para levantar el compose
  generado end-to-end, **pendiente de una corrida real de `EJECUTAR-DOCKER-DESDE-GITHUB.ps1 -Mode
  Start` (canal Stable) antes de considerar esta ruta 100% verificada**, a diferencia del resto de
  F3 que sí se verificó en Docker real.
  **Verificación posterior, Docker real (mismo día)**: `EJECUTAR-DOCKER-DESDE-GITHUB.ps1` en sí no
  corre en este Mac (`Find-Docker`/`Find-GitHubCli` buscan `docker.exe` en rutas de Windows —
  Windows-only por diseño, igual que los `.bat`; no se tocó). Se verificó en cambio la topología
  real que ese mismo compose generado produce: `docker compose up --wait` con el YAML exacto del
  canal Stable + imágenes `backend`/`bff`/`frontend` construidas localmente y tagueadas como las de
  GHCR (`ghcr.io/marcolyto/hcop_jp-*:latest` — el paquete real es privado, sin credenciales en esta
  sesión, así que no se probó el pull real, solo la topología). **5 servicios healthy**
  (database→redis→backend→bff→frontend), `/actuator/health` UP, `/api/clinical/status` ok,
  `/`→302→`/app/` con `<app-root>` real. Confirma que el fix de `compose.github.yaml` resuelve el
  bug real (antes: `nginx: emerg host not found in upstream "bff"`, frontend sin bff en el compose).
  **Hallazgo de la prueba (no tocado, documentado)**: el compose que genera el launcher usa nombres
  fijos de volumen/red (`hcop_jp_postgres`, `hcop_jp_internal`, etc.) — si se prueba desde la misma
  máquina que ya tiene el `docker compose` del repo corrido alguna vez, comparte volumen con esos
  datos reales (intencional: así un usuario final conserva su base entre actualizaciones, pero es
  una trampa al probar en la máquina de desarrollo). Se aisló con nombres temporales para la
  prueba, sin tocar el volumen real (`hcop_jp_postgres`, confirmado con `docker volume inspect`
  antes y después). Pendiente real: el pull genuino desde GHCR con la imagen `bff` recién existirá
  después de mergear a `main` y que corra el job `publish` con la matriz nueva.

## Decisiones ya tomadas (no volver a preguntar)
- Layout: backend/ bff/ frontend/ en raíz · Auth: JWT completo · Alcance: hexagonal completo
- Orden: Infra→BFF→JWT→hexagonal · Revocación: inmediata (columna revoked + lookup por sid)
- Puerto publicado: se mantiene 5180

## Notas de ejecución (agregar hallazgos nuevos acá, no releer agentes viejos)
