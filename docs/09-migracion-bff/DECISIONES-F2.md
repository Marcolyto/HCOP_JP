# Decisiones F2 — Token Handler JWT real

Desvíos conscientes de la redacción literal del plan (`~/.claude/plans/fuzzy-waddling-galaxy.md`),
tomados durante la implementación y verificados contra el sistema real. El detalle de cada tarea
está en `PROGRESO.md`; este documento reúne solo las decisiones, no la crónica.

## jjwt 0.12.6 + Gson en vez de 0.13.0 + Jackson (F2.1)

El plan asumía `jjwt-api/impl/jackson` 0.13.0. Esa versión no existe en Maven Central (la última
es 0.12.6). Además `jjwt-jackson` trae Jackson 2 (`com.fasterxml.jackson`) transitivo, y el
proyecto está en Jackson 3 (`tools.jackson`, Boot 4.1) — convivir dos majors de Jackson sin
necesidad real es un riesgo sin beneficio. Se usa `jjwt-gson` en su lugar: jjwt solo lo usa para
serializar el payload interno del JWT, nunca sale al contrato REST, así que Gson no tiene
ningún efecto observable hacia afuera.

## `logout` público en modo JWT (F2.5)

`AuthInterceptor.isPublic()` incluye `/api/auth/logout`. Motivo: el cliente debe poder cerrar
sesión incluso con el access token ya vencido (le queda solo el refresh token) —
`JwtAuthenticationFilter` no puebla el principal si el access token no verifica, así que exigir
sesión ya resuelta ahí dejaría sin forma de cerrar sesión en ese caso, justo cuando más se
necesita (logout tras inactividad).

## `SecurityConfiguration` se queda en `permitAll()` (F2.6)

El plan pedía "endurecer a `anyRequest().authenticated()`" al entrar `JwtAuthenticationFilter`.
No se hizo: los filtros de Spring Security corren **antes** que cualquier `HandlerInterceptor`.
La autorización real (93 `requirePermission`, 4 `hasPermission` de filtrado de datos) vive en
`AuthInterceptor`, que es un `HandlerInterceptor` — para cuando correría, Security ya habría
rechazado la request si el gate estuviera ahí. Cumplir la redacción literal exigía romper el modo
cookie (mientras convivió, F2.5–F2.7) o reescribir esos 93 call-sites contra el modelo de
`Authentication`/`GrantedAuthority` de Spring Security — las dos cosas están fuera de alcance de
F2 y violan el hallazgo 6 del plan ("`SessionPrincipal` + `AuthContext.requirePermission`
intactos"). `SecurityConfiguration` existe solo para desactivar el auto-config de login por
defecto de Boot y hospedar el bean de `JwtAuthenticationFilter`.

## Access token con el `SessionPrincipal` completo (F2.6)

El plan no especificaba el contenido exacto de los claims. Se decidió embeber roles (id+key+name,
no solo la key) y permisos completos en el access token, para que `JwtAuthenticationFilter`
reconstruya el principal sin repetir el join de 6 tablas de `AuthRepository.findSession` en cada
request — es la lectura barata que el propio plan reclama para la revocación (hallazgo:
"1 lookup por PK, más barato que el join de 6 tablas"). `activePatientId` es la única excepción:
vive en `local_session_state` y se relee por request porque cambia sin reemitir el token
(`PUT /api/auth/active-patient` no fuerza un refresh).

## Refresh token también firmado, no un secreto aleatorio crudo (F2.5)

El plan no detallaba el formato del refresh token. Se decidió emitirlo como JWT firmado (claims
mínimos: `sid`, `jti`, sin roles/permisos) en vez de un valor aleatorio guardado tal cual: la fila
de `local_refresh_tokens` es el ledger de revocación real (columna `revoked`), la firma es lo que
prueba posesión sin depender de que la tabla nunca sea legible por nadie más.

## F2.7.5 — BFF actualizado a JWT (no estaba en el plan original)

**Decisión escalada al usuario** (`AskUserQuestion`, no una decisión unilateral como las
anteriores): F2.8 iba a eliminar `local_sessions`, y el BFF (F1) todavía hablaba el contrato
viejo (`Set-Cookie` del backend, token opaco reenviado como Bearer). Sin actualizar el BFF antes,
F2.8 rompía el login vía frontend de punta a punta. El usuario eligió "reescribir BFF a JWT
primero" sobre la alternativa de frenar F2 sin tocar el flag default. Detalle completo en
`PROGRESO.md`, sección F2.7.5.

## `HcopProperties.sessionCookieName`/`sessionDurationMinutes` no se eliminaron (F2.8)

Quedaron sin lector en el código de aplicación tras F2.8, pero no se sacaron del record: es un
`@ConfigurationProperties` ancho que muchos tests no relacionados con auth construyen
posicionalmente (`StudyTemplateControllerTest`, `TreatmentCatalogServiceTest`,
`ClinicalCatalogConsistencyTest`, `LocalGuideFileStoreTest`). Sacar los campos hubiera obligado a
tocar esos tests sin ningún beneficio funcional — se documenta acá en vez de en el código para no
dejar el `record` con un comentario "TODO eliminar" que nadie va a accionar.

## Bug real encontrado en la propia limpieza, no en verificación de fase (F2.8)

`AuthService.setActivePatient` seguía llamando a `AuthRepository.setActivePatient` (una query
contra `local_sessions.token_hash`) con un `sessionId` que desde F2.5 es un `sid` UUID, no un
hash — el `WHERE` nunca matcheaba nada. `PUT /api/auth/active-patient` en modo JWT devolvía `200`
pero no persistía nada, desde F2.5 hasta que se corrigió acá. F2.6 y F2.7 nunca lo detectaron
porque sus verificaciones end-to-end solo probaron el endpoint con `patientId: null`. Corregido
para escribir en `SessionStateRepository` (la tabla real del active-patient en modo JWT) y
verificado con un paciente real de punta a punta.

## F2.9 — security-review

Corrida sobre el diff completo de F2 (auth/, config/, admin/, bff/auth+security+proxy). Sin
hallazgos de severidad alta o media: sin inyección SQL (todo el SQL nuevo usa `?` parametrizado),
sin logging de tokens/secretos, sin fuga de `accessToken`/`refreshToken` hacia el navegador
(verificado con test explícito en `BffAuthControllerTest`), sin bypass de autorización nuevo. El
único hallazgo real de esta fase (el bug de `active-patient`) es de correctness, no de seguridad
explotable — está documentado arriba.
