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
- [ ] F1.3 — ApiProxyController streaming + DocsProxyController
- [ ] F1.4 — Security/logging/cache filters + BackendHealthIndicator
- [ ] F1.5 — Compose con redis+bff, verificación F1

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
