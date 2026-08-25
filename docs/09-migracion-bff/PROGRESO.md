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

- [ ] F2.1 — TokenIssuerTest (validar jjwt/Jackson 3 ANTES de seguir)
- [ ] F2.2 — JwtProperties + TokenIssuer + deps + SecurityConfiguration en permitAll
- [ ] F2.3 — V013 migración (local_refresh_tokens, local_session_state) + repos
- [ ] F2.4 — AuthContext.sessionId (9 call-sites) + media/patient a sid
- [ ] F2.5 — login/refresh/logout modo dual (flag hcop.auth.mode)
- [ ] F2.6 — JwtAuthenticationFilter + SecurityFilterChain (commit aislado, riesgoso)
- [ ] F2.7 — Revocación inmediata + revocar en AdminService/changePassword
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
