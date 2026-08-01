# ADR-0002: Angular y convivencia progresiva

- Estado: aceptada.
- Fecha: 30/07/2026.

## Contexto

La interfaz actual conserva muchas funciones dentro de `app.js`. Reemplazarla
en un único corte haría difícil demostrar paridad en historia clínica, editor
de imágenes, workflow y turnero.

## Decisión

Crear Angular en `frontend/` y migrar por capacidades. Durante la transición:

- `/legacy` servirá la interfaz anterior;
- `/app` servirá Angular;
- ambas utilizarán `/api`, la cookie de sesión y el paciente activo;
- una configuración permitirá habilitar la versión Angular por capacidad.

Cuando la matriz de paridad esté completa, Angular pasará a `/` y la interfaz
anterior se retirará.

## Criterios técnicos

- TypeScript estricto y componentes standalone.
- Formularios reactivos.
- Cliente generado desde OpenAPI.
- Lazy loading por funcionalidad.
- Signals y RxJS antes de evaluar un store global.
- Angular CDK para overlay, accesibilidad y drag-and-drop.
- Sistema visual propio para preservar la apariencia clínica.

## Consecuencias

- El paquete crecerá temporalmente porque contendrá dos interfaces.
- Se podrá revertir una capacidad sin restaurar la base.
- Los contratos REST deberán permanecer compatibles durante la convivencia.
