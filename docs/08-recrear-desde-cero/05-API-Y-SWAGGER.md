# 05 · Diseñar API y Swagger

## Convenciones HTTP

- sustantivos para recursos;
- métodos HTTP coherentes;
- JSON UTF-8;
- `YYYY-MM-DD` para fechas civiles;
- ISO-8601 con offset para instantes;
- `multipart/form-data` para cargas;
- filtros opcionales en query;
- IDs en path cuando identifican el recurso;
- acciones sólo cuando representan una transición real.

Ejemplos:

```text
GET    /api/clinical/patients
POST   /api/clinical/patients
GET    /api/clinical/patients/{patientId}/treatments
POST   /api/clinical/infusions
PATCH  /api/clinical/infusions/{id}
POST   /api/clinical/qr-scans
```

## Contratos

Defina request y response explícitos. Valide:

- requeridos;
- longitud;
- rango;
- formato;
- combinación de campos;
- pertenencia al paciente;
- transición de estado;
- revisión esperada.

No acepte silenciosamente campos desconocidos en operaciones críticas sin una
decisión consciente.

## Códigos

| Código | Uso |
|---|---|
| `200` | lectura o cambio exitoso |
| `201` | recurso creado, si el contrato lo usa |
| `204` | éxito sin cuerpo |
| `400` | entrada inválida |
| `401` | sesión inválida |
| `403` | permiso insuficiente |
| `404` | recurso inexistente/no disponible |
| `409` | revisión, estado o agenda en conflicto |
| `413` | archivo demasiado grande |
| `415` | formato no admitido |
| `500` | fallo interno seguro |

## Error común

```json
{
  "ok": false,
  "error": "Mensaje comprensible",
  "code": "revision_conflict",
  "status": 409
}
```

No use siempre `200` con `ok:false`; dificulta clientes, métricas y pruebas.

## OpenAPI

Configure:

- título, versión, contacto y alcance;
- servidor relativo `/`;
- cookie `HCOP_SESSION` como security scheme;
- grupos completa, clínica y administración;
- tags funcionales;
- resumen y descripción por operación;
- permiso mediante `x-hcop-permission`;
- controlador mediante `x-hcop-controller`;
- cuerpos, parámetros y respuestas.

Swagger debe ser útil para un tercero sin leer Java.

## Catálogo reproducible

El proyecto actual genera Markdown y HTML desde la API:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\generate-api-docs.ps1
```

Y valida:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\generate-api-docs.ps1 -Check
```

Al agregar una ruta:

1. documente finalidad;
2. asigne tag y permiso;
3. agregue pruebas;
4. regenere catálogo;
5. actualice mapa funcional si cambió persistencia.

## Compatibilidad

Si debe conservar una ruta histórica:

- documente que es compatibilidad;
- impleméntela sobre los servicios locales;
- no conecte silenciosamente con un sistema retirado;
- defina fecha/condición de deprecación;
- mantenga pruebas del consumidor.

## Hito de aceptación

`/v3/api-docs/hcop-jp-completa` debe contener todas las operaciones con resumen,
descripción, controller y permiso. Swagger debe poder ejecutar una ruta pública,
una autenticada y una restringida mostrando los códigos correctos.
