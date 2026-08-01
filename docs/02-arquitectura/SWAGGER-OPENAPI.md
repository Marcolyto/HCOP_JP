# Swagger / OpenAPI

Swagger es la documentación ejecutable de la API real. Se genera desde los
controladores Spring MVC, los modelos Java y los metadatos de
`OpenApiConfiguration`; no es un inventario mantenido por separado.

## Direcciones

```text
Interfaz:              http://localhost:5180/swagger-ui.html
API completa JSON:     http://localhost:5180/v3/api-docs/hcop-jp-completa
Clínica JSON:          http://localhost:5180/v3/api-docs/clinica
Administración JSON:   http://localhost:5180/v3/api-docs/administracion
API base JSON:         http://localhost:5180/v3/api-docs
API base YAML:         http://localhost:5180/v3/api-docs.yaml
```

Swagger agrupa:

- **HCOP JP · API completa:** todas las rutas `/api/**`;
- **Clínica y Hospital de Día:** historia, diagnósticos, tratamientos, turnos,
  archivos, catálogos clínicos y operaciones LLM;
- **Administración y configuración:** usuarios, roles, seguridad, protocolos,
  parámetros versionados, guías, plantillas anatómicas y prueba del LLM.

Cada operación informa:

- método, ruta, resumen y finalidad;
- módulo y controlador MVC responsable;
- parámetros y cuerpos inferidos de Java;
- respuestas normales y errores esperables;
- seguridad por cookie y permiso efectivo mediante `x-hcop-permission`;
- si la operación es pública o autenticada mediante
  `x-hcop-authentication`.

## Probar una ruta

1. Inicie sesión en HCOP JP en otra pestaña del mismo navegador.
2. Abra Swagger.
3. Seleccione el grupo.
4. Abra una operación.
5. Pulse **Try it out** y luego **Execute**.

La cookie `HCOP_SESSION` es HttpOnly: Swagger no la lee ni la muestra; el
navegador la envía por ser el mismo origen.

Para comenzar sin Swagger:

```http
POST /api/auth/login
Content-Type: application/json

{"username":"usuario","password":"contraseña"}
```

La respuesta establece la cookie. Las siguientes solicitudes deben conservarla.
No se envían contraseñas, claves del LLM ni el valor de la cookie en URLs.

## Catálogo legible y buscable

El archivo [ENDPOINTS.md](ENDPOINTS.md) y la página
`/docs/api-endpoints.html` se generan con:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\generate-api-docs.ps1
```

HCOP JP debe estar iniciado. Para verificar sin sobrescribir:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\generate-api-docs.ps1 -Check
```

El segundo comando es parte de la validación del repositorio y falla si se
agregó, eliminó o cambió un endpoint sin regenerar la referencia.

## Respuestas y errores

Las respuestas de éxito pueden ser un objeto, una lista, un archivo o un
documento imprimible según la operación. Las altas responden `201`; el resto de
las operaciones exitosas usa el estado indicado en cada ruta.

Los errores JSON generales referencian el componente reutilizable `ApiError`:

```json
{
  "ok": false,
  "error": "Mensaje seguro para el usuario",
  "code": "codigo_opcional",
  "status": 409
}
```

Cuando una ruta protegida no recibe una sesión válida, la respuesta `401`
referencia `AuthenticationRequired`. Conserva `authenticated` y
`loginRequired` para la interfaz vigente y agrega `code` y `status` para el
cliente Angular:

```json
{
  "ok": false,
  "authenticated": false,
  "loginRequired": true,
  "error": "Debe iniciar sesión.",
  "code": "AUTHENTICATION_REQUIRED",
  "status": 401
}
```

| Estado | Significado |
|---|---|
| `400` | Parámetros, archivo o cuerpo inválidos. |
| `401` | Sesión ausente, vencida o revocada. |
| `403` | El usuario no tiene el permiso requerido. |
| `404` | El recurso no existe o no está disponible. |
| `409` | Revisión desactualizada, estado incompatible o superposición. |
| `413` | El archivo supera el límite permitido. |
| `415` | El tipo declarado o la firma binaria no están permitidos. |
| `422` | Los datos no cumplen una regla clínica. |
| `502` | El servicio LLM respondió con un resultado inválido. |
| `503` | El servicio LLM está desactivado o no está configurado. |
| `504` | El servicio LLM excedió el tiempo de espera. |
| `500` | Error interno sin exposición de datos sensibles. |

Los cambios clínicos usan `revision` o `version` cuando existe riesgo de que dos
usuarios modifiquen simultáneamente el mismo recurso. Un `409` obliga a releer y
revisar, no a repetir ciegamente.

## Archivos

Las cargas grandes se transmiten como cuerpo binario, sin convertir el archivo
a JSON ni retenerlo completo en memoria:

- estudios: `application/octet-stream`, con identidad y nombre en parámetros;
- guías: `application/pdf`;
- plantillas anatómicas: PNG, JPEG, GIF, WebP, BMP o TIFF.

Swagger muestra estos cuerpos con formato `binary`. Las descargas y documentos
pueden responder PDF, imagen o el tipo MIME original. El acceso continúa
protegido por sesión: conocer una ruta de archivo no evita la autorización.

## Regla de mantenimiento

Todo nuevo endpoint debe tener:

- permiso explícito;
- descripción en `OpenApiConfiguration`;
- parámetros, cuerpo y respuestas representados en OpenAPI;
- una única etiqueta funcional canónica;
- `operationId` único;
- cuerpo binario visible cuando el controlador consume un stream;
- `ApiError` en toda respuesta `4xx` o `5xx`, salvo el `401` protegido que usa
  `AuthenticationRequired`;
- prueba de éxito, error y autorización;
- modelo de datos documentado;
- ausencia de secretos o datos clínicos reales en ejemplos;
- catálogo `ENDPOINTS.md` regenerado.

El detalle de convenciones está en
[Contratos y convenciones de API](../04-desarrollo/CONTRATOS-DE-API.md).
