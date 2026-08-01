# Contratos REST de la migración

Este documento fija las convenciones que deben respetar simultáneamente la
interfaz vigente, Angular y los adaptadores HTTP del backend hexagonal. OpenAPI
es el contrato ejecutable; este texto explica las decisiones que no conviene
repetir en cada endpoint.

## Compatibilidad durante la convivencia

- Una ruta existente no cambia de método, dirección ni significado.
- Un campo existente no cambia de nombre, tipo o unidad.
- Un campo nuevo sólo puede ser aditivo y debe tener un valor por defecto
  seguro para el consumidor anterior.
- Los DTO nuevos se incorporan por capacidad. Mientras una pantalla antigua
  necesite JSON dinámico, el adaptador traduce ese contrato al comando tipado de
  aplicación.
- Angular utiliza el cliente generado desde OpenAPI. No mantiene manualmente
  una segunda definición de las mismas respuestas.

## Éxito

Las altas responden `201 Created`. Una modificación responde `200 OK` mientras
el frontend vigente necesite el recurso actualizado en el cuerpo. Las consultas
responden `200 OK`. Los archivos conservan su tipo MIME real.

Durante la convivencia se mantienen los contenedores históricos como:

```json
{
  "ok": true,
  "item": {},
  "total": 1
}
```

Cada capacidad migrada sustituirá internamente los mapas por DTO, pero no
cambiará la forma JSON sin una versión explícita del endpoint.

## Error general

Todo error gestionado utiliza `ApiError`:

```json
{
  "ok": false,
  "error": "Mensaje seguro para el usuario",
  "code": "CODIGO_ESTABLE_OPCIONAL",
  "status": 409
}
```

`error` se puede mostrar. `code` se usa para decisiones automáticas y puede
omitirse durante la compatibilidad. El frontend no debe decidir leyendo el
texto del mensaje. `status` coincide siempre con el estado HTTP.

Una ruta protegida sin sesión usa `AuthenticationRequired`, conservando los
indicadores de la interfaz anterior:

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

No se devuelven excepciones, SQL, rutas locales, secretos ni datos clínicos en
mensajes técnicos.

## Identificadores

- Los identificadores se tratan como opacos fuera del módulo propietario.
- Angular no calcula ni incrementa identificadores.
- Los identificadores que hoy pueden superar el entero seguro de JavaScript se
  representan como texto en los DTO nuevos.
- `patientId`, `treatmentId`, `cycleNumber` y `applicationDay` identifican una
  aplicación clínica concreta.

## Fechas, horas y zona

| Concepto | Tipo de contrato | Ejemplo |
|---|---|---|
| Día clínico sin hora | ISO `LocalDate` | `2026-07-30` |
| Instante auditable | ISO `Instant` en UTC | `2026-07-30T12:15:00Z` |
| Hora de configuración | `HH:mm` | `08:00` |
| Duración | minutos enteros | `90` |

La zona clínica del sistema es `America/Argentina/Buenos_Aires`. El servidor
calcula el día clínico usando esa zona y persiste los instantes auditables en
UTC. El frontend sólo formatea para presentación; no cambia el día de un turno
por conversión implícita.

## Estados

- Los estados viajan como códigos estables en minúsculas y `snake_case`.
- La etiqueta en español pertenece a la presentación o a un catálogo, no al
  valor persistido.
- Un estado desconocido no se convierte silenciosamente: produce `400` si el
  comando es inválido o se presenta como desconocido si proviene de datos
  históricos.
- Las transiciones clínicas se validan en el dominio aunque el botón esté
  deshabilitado en Angular.

## Concurrencia

Los recursos editables publican `revision`. Todo comando que pueda pisar la
acción de otra persona envía `expectedRevision`. PostgreSQL actualiza sólo si
coincide:

1. el cliente lee revisión `N`;
2. envía el comando con `expectedRevision: N`;
3. el servidor persiste revisión `N + 1`;
4. si otro actor ya cambió el recurso, responde `409`;
5. Angular vuelve a leer y muestra el cambio antes de permitir una nueva
   decisión.

Nunca se reintenta automáticamente un `409` clínico.

## Idempotencia

Las transiciones clínicas que podrían repetirse por doble clic, reconexión o
reintento incluyen `idempotencyKey`, de 8 a 128 caracteres seguros. Repetir la
misma clave y el mismo comando devuelve el resultado registrado sin duplicar
evoluciones, reservas ni administraciones. Reutilizar una clave con otro
comando es un conflicto.

La clave la genera el cliente una vez por intención del usuario y permanece
estable durante sus reintentos.

## Archivos

Los archivos se transmiten como binario y se validan por extensión, tipo
declarado y firma real. La respuesta nunca expone una ruta del host. La
eliminación temporal de una carga exige el token emitido a la misma sesión.

## Criterio de aceptación de un endpoint migrado

1. Entrada y salida poseen DTO Java o adaptadores explícitos de compatibilidad.
2. Validación sintáctica en web y regla clínica en dominio.
3. Permiso documentado y probado.
4. Errores tipados en OpenAPI.
5. Fechas, estados y unidades cumplen este documento.
6. Escrituras concurrentes e idempotentes están cubiertas cuando corresponda.
7. La interfaz vigente y Angular reciben el mismo significado.
8. Hay prueba de éxito, validación, autorización y conflicto relevante.
