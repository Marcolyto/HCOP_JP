# ADR-0003: contratos, datos y rollback

- Estado: aceptada.
- Fecha: 30/07/2026.

## Contexto

La migración debe funcionar tanto sobre una instalación vacía como sobre una
base HCOP JP existente. Angular necesita contratos tipados y la interfaz
anterior debe continuar operando durante la transición.

## Decisión

- OpenAPI será la fuente contractual de los clientes.
- Las rutas actuales se preservarán mientras tengan consumidores.
- Los cambios incompatibles utilizarán una versión explícita.
- Flyway aplicará cambios aditivos durante la convivencia.
- No se editarán migraciones ya publicadas.
- Toda nueva escritura conservará revisión optimista e idempotencia cuando el
  caso de uso pueda reintentarse.
- Las imágenes Docker tendrán etiqueta inmutable además de `latest`.

## Rollback

El rollback de aplicación consiste en ejecutar la imagen anterior contra un
esquema compatible. Por eso una migración no eliminará inmediatamente tablas,
columnas o valores utilizados por la versión previa. La contracción del
esquema ocurrirá después de retirar la interfaz anterior y completar una copia
de seguridad restaurable.

## Evidencia requerida

- Instalación vacía.
- Actualización desde una copia de la base vigente.
- Ejecución de la versión anterior después de una migración aditiva.
- Comparación de OpenAPI.
- Respaldo y restauración comprobados.
