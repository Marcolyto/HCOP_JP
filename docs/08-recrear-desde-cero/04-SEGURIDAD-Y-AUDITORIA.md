# 04 · Implementar seguridad y auditoría

## Modelo de amenaza mínimo

Proteja contra:

- acceso sin sesión;
- usuario deshabilitado;
- escalada de permisos desde la UI;
- robo/reutilización de cookies;
- fuerza bruta y contraseñas débiles;
- inyección SQL;
- traversal y carga de archivos falsos;
- exposición de PHI en logs;
- alteración o reutilización de QR;
- sobrescritura concurrente;
- secretos dentro de Git o imagen.

## Contraseñas

- hash BCrypt con coste actualizado;
- nunca cifrado reversible;
- nunca log;
- longitud mínima y bloqueo de contraseñas triviales;
- cambio de contraseña revoca otras sesiones;
- usuario inicial sólo para bootstrap y con cambio obligatorio en producción.

## Sesiones — Token Handler (BFF)

El navegador nunca ve un JWT. El patrón es **Token Handler**, repartido en
dos servicios:

**`bff` (lo único que habla el navegador):**

- expone una cookie `BFF_SESSION`: HttpOnly, SameSite=Strict, Secure detrás
  de HTTPS, token opaco de alta entropía;
- guarda en Redis `{accessToken, accessExpiresAt, refreshToken,
  refreshExpiresAt}` del backend — nunca los reenvía al navegador (verifique
  esto con un test explícito: el body de la respuesta de login se rearma a
  partir del `session`, no de los tokens crudos);
- en cada request agrega `Authorization: Bearer <accessToken>` antes de
  proxear hacia `backend`;
- refresco transparente: si el access token tiene poca vida restante, llama
  `POST /api/auth/refresh` del backend de forma síncrona antes de proxear —
  con lock (`SETNX`) para que N requests concurrentes sólo disparen un
  refresh;
- si el refresh falla (401 — sesión revocada), borra la sesión de Redis: la
  request sigue sin sesión y responde `401` sin llegar al backend.

**`backend` (nunca lo toca el navegador):**

- valida el JWT firmado con `JwtAuthenticationFilter` (HS512, TTL corto —
  15 min es razonable), sin cookies propias;
- el access token lleva el principal completo (roles con id/key/name) para
  no releer la base en cada request, salvo el paciente activo, que sí se
  resuelve con una lectura por PK a `local_session_state` (cambia más
  seguido que el resto del principal);
- revocación inmediata: `local_session_state.revoked` — deshabilitar un
  usuario, cambiarle la contraseña o reasignarle roles marca la fila y el
  siguiente request con ese access token (aunque siga dentro de su TTL)
  responde `401` sin esperar el vencimiento;
- `POST /api/auth/refresh` es público pero nunca se expone al navegador —
  sólo lo llama el BFF, server-to-server.

Paciente activo pertenece a la sesión (`local_session_state.sid`), no a una
variable global.

Por qué dos servicios y no uno: si el backend hablara directo con el
navegador necesitaría CORS, manejo de cookies y quedaría expuesto a XSS
robando el token. Separando el Token Handler, el backend sólo confía en un
Bearer corto y el BFF es la única superficie que necesita hablar HTTP con
un cliente no confiable.

## Roles y permisos

Roles iniciales:

- Administrador;
- Médico oncólogo;
- Enfermería;
- Farmacia;
- Admisión.

Los permisos son capacidades granulares (`section.*`, `workflow.*`, `admin.*`).
Cada ruta protegida exige el permiso en servidor. Ocultar un botón mejora la
interfaz, pero no autoriza.

Pruebe siempre:

- sin `Authorization: Bearer` (backend) / sin cookie `BFF_SESSION` (BFF) →
  `401`;
- token/cookie válido sin permiso → `403`;
- usuario deshabilitado → `local_session_state.revoked`, request siguiente
  con el access token vigente → `401` sin esperar el TTL;
- permiso concedido → caso normal;
- token tamperado (firma alterada) → `401`.

## CSRF y mismo origen

`SameSite=Strict` en la cookie del BFF reduce CSRF. El backend no acepta
cookies en absoluto (sólo Bearer), así que no tiene superficie CSRF propia.
Mantenga nginx→BFF→backend en un único punto de entrada público (mismo
origen desde el navegador) y no habilite CORS global con `*` en ningún
servicio.

## Archivos

Al cargar:

- limite tamaño;
- permita una lista de formatos;
- valide MIME y firma binaria;
- genere nombre interno;
- normalice la ruta dentro de storage;
- calcule SHA-256;
- no sirva el directorio como estático;
- autorice cada descarga;
- permita borrado temporal sólo a la misma sesión mediante grant.

## Secretos

- variables de entorno o secret manager;
- claves QR y cifrado diferentes;
- API key LLM cifrada con AES-GCM;
- respuestas nunca devuelven la API key;
- `.env.example` sólo contiene placeholders;
- rotación y recuperación documentadas.

## QR clínico

El QR debe incluir identificadores mínimos, vencimiento/versión y firma HMAC.
No debe mostrar texto clínico sensible. Al escanear:

1. verificar firma;
2. resolver paciente/tratamiento/ciclo/turno;
3. comprobar permiso;
4. persistir hash y `operation_id` único;
5. agregar evolución;
6. devolver el contexto operativo.

## Auditoría frente a evolución

- **Auditoría:** quién cambió técnicamente qué, antes/después, cuándo y por qué.
- **Evolución:** explicación clínica legible del acto.

Una no reemplaza a la otra. Ambas se escriben en la transacción del acto. Evite
auditar secretos o el documento completo si basta un resumen estructurado.

## Transporte y red

- HTTPS obligatorio fuera de localhost/intranet controlada;
- PostgreSQL no se publica a Internet;
- acceso remoto mediante VPN o proxy inverso;
- firewall limitado;
- headers del proxy configurados;
- `HCOP_PUBLIC_BASE_URL` coincide con la URL real.

## Hito de aceptación

Realice una matriz rol × operación y demuestre que cada celda permitida funciona
y cada celda prohibida responde `403`. Revise que logs, Swagger, historial del
navegador y repositorio no contengan contraseñas, cookies, claves ni pacientes.
