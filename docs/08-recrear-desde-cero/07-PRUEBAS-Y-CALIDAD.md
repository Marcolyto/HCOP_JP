# 07 · Aplicar pruebas y calidad

## Pirámide de pruebas

### Unitarias

Para reglas puras:

- fechas de ciclos;
- duración;
- normalización;
- estadificación;
- transiciones;
- firma/verificación;
- cálculo de antropometría.

### Repositorio con PostgreSQL real

Use Testcontainers, no H2, cuando se prueben:

- JSONB;
- índices parciales;
- locks;
- triggers;
- sintaxis PostgreSQL;
- zonas horarias;
- restricciones.

### Service

Verifique transacciones y reglas:

- rollback completo;
- idempotencia;
- conflicto de revisión;
- estado incompatible;
- auditoría/evolución.

### Controller/API

Verifique:

- cuerpos válidos e inválidos;
- códigos HTTP;
- cookie;
- permisos;
- MIME/descargas;
- ausencia de información interna.

### Integración integral

El flujo mínimo crea datos sintéticos en una base efímera y recorre:

```text
login → paciente → diagnóstico → tratamiento → ciclos → farmacia
→ turno → QR → administración → evolución/documento
```

Nunca ejecute una prueba que crea pacientes sobre producción.

## Casos críticos

- dos turnos concurrentes para el mismo sillón;
- dos guardados con la misma revisión;
- reintento de escaneo QR;
- reintento de finalizar administración;
- suspensión transitoria/definitiva;
- reanudación que exige prescripción;
- archivo con extensión válida y firma falsa;
- traversal en nombre;
- rol sin permiso;
- sesión vencida;
- LLM caído o respuesta inválida.

## Datos de prueba

- siempre ficticios y claramente marcados;
- sin DNI, nombres o estudios reales;
- creados y eliminados dentro del entorno de prueba;
- fixtures pequeños;
- variaciones generadas sin copiar producción;
- secretos de prueba separados.

## Calidad automática

En cada push:

1. compilar Java;
2. ejecutar tests;
3. construir Docker;
4. iniciar PostgreSQL/aplicación;
5. esperar health;
6. verificar OpenAPI/documentación;
7. ejecutar flujo integral;
8. mostrar logs sólo si falla;
9. destruir volúmenes efímeros.

## Revisión documental

Valide:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\generate-api-docs.ps1 -Check
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-documentation.ps1
```

La documentación rota debe fallar CI igual que una prueba.

## Prueba visual

Para cada cambio relevante:

- verificar DOM y accesibilidad;
- revisar a tamaño real;
- comprobar foco y teclado;
- probar scroll/overflow;
- revisar consola;
- probar estados vacío, error, cargando y muchos registros.

## Criterio de cobertura

No persiga un porcentaje aislado. Exija cobertura de:

- invariantes;
- permisos;
- transiciones;
- concurrencia;
- pérdida/reintento de red;
- migraciones;
- restauración.

Un bloque con 100 % de líneas pero sin prueba concurrente sigue siendo
insuficiente para un turnero.

## Hito de aceptación

Una copia limpia debe pasar:

```powershell
mvn --batch-mode verify
docker compose up --build --detach --wait
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\integration-test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\generate-api-docs.ps1 -Check
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-documentation.ps1
docker compose down --volumes
```

En Windows, ejecute los `.ps1` como en el bloque anterior:
`powershell.exe -NoProfile -ExecutionPolicy Bypass -File`. La excepción se
aplica sólo a ese proceso.
