# Pruebas

## Compilación

```powershell
mvn verify
```

## Prueba integral

Con el sistema iniciado:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\integration-test.ps1
```

Valida:

- salud;
- autenticación;
- paciente e historia;
- diagnóstico;
- protocolo y duración;
- tratamiento y ciclos;
- Farmacia y reserva por componente;
- turno sin superposición;
- triaje PASS;
- preparación y liberación;
- QR firmado;
- administración multidroga interrumpida y reanudada;
- conservación de dosis parcial, reacción e historial al cerrar;
- administración finalizada;
- hoja imprimible;
- evoluciones persistidas.

La prueba genera pacientes sintéticos solo en la base donde se ejecuta. No la
ejecute sobre producción.

En la aceptación final del 30/07/2026, la suite Java terminó con **101/101
pruebas aprobadas**. El E2E utilizó una aplicación de cuatro drogas, interrumpió
Carboplatino al 50 %, reanudó la administración y finalizó en `completed`
conservando dosis parcial, interrupción y reacción.

## Documentación y OpenAPI

Con HCOP JP iniciado:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\generate-api-docs.ps1 -Check
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-documentation.ps1
```

La primera orden comprueba que el catálogo de endpoints coincide exactamente
con Swagger. La segunda valida enlaces Markdown, páginas públicas, documentación
HTML y que cada operación OpenAPI tenga resumen, descripción, controlador y
permiso. Ambas se ejecutan también en GitHub Actions.

## Matriz de 100 casos de Hospital de día

Con una instancia QA aislada en `http://127.0.0.1:5181`:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\qa\hospital-day-100-cases.ps1 `
  -BaseUrl http://127.0.0.1:5181
```

La última evidencia registró
[100 PASS, 0 FAIL, 0 NO_DATA y 0 MANUAL](../08-auditoria/resultados/hospital-dia-100-casos-20260730-100711.md).
Esta matriz es independiente de la prueba integral multidroga anterior.

## Docker en GitHub

El workflow `verify.yml` construye el producto, espera la salud y destruye sus
volúmenes temporales al finalizar.
