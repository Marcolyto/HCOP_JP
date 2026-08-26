# Contratos y convenciones de API

Esta guía explica las reglas transversales de los endpoints. El inventario ruta
por ruta está en [ENDPOINTS.md](../02-arquitectura/ENDPOINTS.md) y el contrato
ejecutable en `/swagger-ui.html`.

## Transporte

- HTTP/HTTPS y JSON UTF-8.
- Fechas civiles: `YYYY-MM-DD`.
- Instantes: ISO-8601 con zona u offset.
- Archivos: `multipart/form-data` al cargar y tipo MIME real al descargar.
- Ningún dato clínico se envía en la URL si puede ir en el cuerpo.

En una instalación expuesta fuera del equipo debe existir HTTPS delante de
HCOP JP. `HCOP_PUBLIC_BASE_URL` tiene que contener la dirección pública correcta.

## Sesión

`POST /api/auth/login` responde al navegador con `Set-Cookie: BFF_SESSION`
(HttpOnly, SameSite=Strict) — la emite el BFF, no el backend. El BFF guarda
el access/refresh token JWT real en Redis y nunca los reenvía al navegador;
en cada request agrega `Authorization: Bearer <accessToken>` antes de
proxear hacia el backend. No existe token en `localStorage`. La sesión se
valida en el servidor (backend valida el JWT; BFF resuelve la cookie) y
puede revocarse al cambiar contraseña, desactivar usuario, reasignar roles o
cerrar sesión — la revocación es inmediata, no espera el TTL del access
token.

Las rutas públicas están marcadas `x-hcop-permission: public`. Las restantes
exigen sesión y, cuando corresponde, un permiso granular como
`section.day-hospital.edit`.

## Autorización

Los controles visuales ayudan al usuario, pero no son una barrera de seguridad.
Cada controlador vuelve a comprobar el permiso. Los roles iniciales son
Administrador, Médico oncólogo, Enfermería, Farmacia y Admisión.

Los permisos de cada rol pueden consultarse y administrarse desde
Configuración. La lista completa está en el
[diccionario de datos](../03-base-de-datos/DICCIONARIO-DE-DATOS.md#roles-y-permisos-iniciales).

## Paciente activo

La interfaz mantiene un paciente activo por sesión. Las rutas de la hoja
clínica (`/api/hc`) trabajan con ese contexto. Las rutas cuyo contrato incluye
`patientId` lo reciben explícitamente. Abrir un paciente en una PC no cambia el
paciente activo de otro usuario ni de otra sesión.

El arranque de la interfaz siempre recupera `GET /api/hc` después de validar la
sesión. Por eso, navegar a Configuración, volver o recargar no libera el
paciente. El cierre explícito ejecuta `PUT /api/auth/active-patient` con
`patientId: null` y después vuelve a pedir `/api/hc`, que entrega la plantilla
en blanco sin borrar ningún dato clínico.

## Control de concurrencia

Las escrituras versionadas reciben `revision` o `version`. El servidor actualiza
solamente si coincide con la revisión vigente:

1. el cliente lee el recurso y su revisión;
2. envía cambios con esa revisión;
3. PostgreSQL actualiza y aumenta el valor;
4. si otro usuario escribió antes, responde `409`;
5. el cliente relee, muestra el estado actual y permite decidir.

El turnero suma una garantía de base de datos: dos bloques activos no pueden
superponerse en el mismo sillón. La UI calcula espacios disponibles, el servicio
valida y PostgreSQL actúa como última barrera transaccional.

La hoja clínica diferencia sus precondiciones con
`ACTIVE_PATIENT_REQUIRED`, `CLINICAL_REVISION_REQUIRED` y
`CLINICAL_PATIENT_MISMATCH`. Sólo `VERSION_CONFLICT` significa que otro actor
avanzó la revisión. Angular conserva el borrador en memoria y no reintenta ni
mezcla cambios automáticamente.

## Idempotencia clínica

Las operaciones que no deben duplicarse usan una identidad estable o verifican
el estado previo. El escaneo QR persiste `operation_id` único; finalizar una
administración ya completada no debe crear una segunda evolución. Ante una
respuesta desconocida por corte de red, primero se consulta el estado vigente.

## Errores

Base de error JSON:

```json
{
  "ok": false,
  "error": "Mensaje comprensible y sin información sensible",
  "code": "codigo_opcional",
  "status": 400
}
```

- `400`: entrada inválida;
- `401`: sesión inválida;
- `403`: permiso insuficiente;
- `404`: recurso inexistente;
- `409`: conflicto de revisión, estado o agenda;
- `500`: fallo no previsto.

Un endpoint de archivo puede devolver el mismo estado sin cuerpo JSON. Los logs
internos contienen el identificador de solicitud; no deben registrar
contraseñas, cookies, claves ni contenido clínico completo.

## Archivos y seguridad

El servicio valida tamaño, extensión declarada, tipo MIME y firma binaria cuando
corresponde. La base guarda metadatos y SHA-256; el binario permanece en
`HCOP_STORAGE_ROOT`. Una carga nueva puede eliminarse durante la misma sesión
mediante un hash temporal y fecha límite, nunca por confiar sólo en un ID.

## Compatibilidad

Algunas rutas conservan formas históricas usadas por la interfaz Lira/HCOP, pero
su implementación es local. No consultan otra aplicación ni una base heredada.
`GET /api/lira/status` informa expresamente ese modo de compatibilidad.

## Evolución de contratos

Antes de cambiar una ruta:

1. localizar sus consumidores;
2. preservar campos existentes o versionar el contrato;
3. agregar validaciones en servicio y restricciones en PostgreSQL;
4. actualizar pruebas;
5. revisar Swagger;
6. regenerar `ENDPOINTS.md`;
7. actualizar el mapa funcional si cambió la persistencia.

No se modifican manualmente archivos ya aplicados de Flyway. Cada cambio de
esquema crea una nueva migración.
