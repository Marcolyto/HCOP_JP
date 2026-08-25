# Progreso migración BFF — feature/migracion-bff-arquitectura

Plan completo: `~/.claude/plans/fuzzy-waddling-galaxy.md` (leer ahí el detalle de cada tarea).
Este archivo es el tracker de estado — actualizar después de cada tarea completada, ANTES de
seguir con la próxima. Si el contexto se compacta, releer este archivo primero.

## Convención
- `[ ]` pendiente · `[~]` en curso · `[x]` hecho y verificado · `[!]` bloqueado (anotar por qué)

## F0 — Separar en 3 servicios Docker (sin BFF, sin tocar auth)

- [ ] F0.1 — git mv (backend/, frontend/public, legacy-reference/)
- [ ] F0.2 — Backend deja de servir el frontend (WebConfiguration, pom.xml, Dockerfile, StudyTemplateController)
- [ ] F0.3 — frontend/Dockerfile + frontend/nginx.conf + scripts de contrato visual
- [ ] F0.4 — compose.yaml/.github/.e2e/.validation + scripts (backup/restore/instalar) + CI
- [ ] F0.5 — Verificación F0 (gates completos, ver plan)

## F1 — BFF + Redis (sesión opaca actual, sin JWT)

- [ ] F1.1 — Scaffolding bff/ (pom.xml, Dockerfile, application.yml)
- [ ] F1.2 — BffAuthController + BffSession + BffSessionService (Redis) + BackendAuthClient
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
