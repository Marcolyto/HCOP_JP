# Auditoría reproducible de Hospital de día

Esta carpeta define una revisión fija de **100 casos**, repartidos en partes iguales:

- 25 de Farmacia.
- 25 de Enfermería.
- 25 de Oncología.
- 25 de Turnos.

El arnés es deliberadamente no destructivo. Solo hace consultas `GET` y un único `POST /api/auth/login` para abrir la sesión QA. No crea pacientes, no prescribe, no reserva stock, no mueve turnos y no cambia estados.

## Regla de seguridad

La URL predeterminada es `http://127.0.0.1:5181`.

El script **aborta siempre** si recibe el puerto `5180`, porque corresponde a la instancia principal. También rechaza otros puertos y equipos remotos salvo confirmación explícita.

## Cómo ejecutarlo

Con la instancia QA activa en `5181`:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\qa\hospital-day-100-cases.ps1
```

El proceso abre un diálogo seguro para las credenciales QA. Para fijar la URL
de forma explícita:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\qa\hospital-day-100-cases.ps1 `
  -BaseUrl http://127.0.0.1:5181
```

Si se omite `-Credential`, el propio script abre el diálogo seguro de credenciales.

Los resultados se guardan en `docs/08-auditoria/resultados/` como:

- JSON, para CI, comparación y procesamiento automático.
- Markdown, para lectura humana y seguimiento.

El arnés es compatible con Windows PowerShell 5.1. Los dos archivos se escriben
explícitamente como UTF-8 sin BOM; los nombres y evidencias con acentos se
preservan aunque la consola use otra página de códigos.

## Significado de los modos

| Modo | Qué demuestra |
|---|---|
| `REAL` | Consulta el servidor QA y verifica la respuesta real sin modificar datos. |
| `CONTRACT` | Comprueba que Swagger o la interfaz desplegada contengan el control y el contrato esperados. |
| `MANUAL` | Requiere interacción humana o concurrencia. Nunca se informa automáticamente como aprobado. |

Estados posibles:

- `PASS`: comprobación automática satisfactoria.
- `FAIL`: comportamiento o contrato ausente.
- `NO_DATA`: el endpoint funciona, pero faltan filas QA para demostrar una coincidencia u orden.
- `MANUAL`: caso preparado para revisión humana.

## Preparación recomendada

Antes de ejecutar la matriz, cargar una semilla en la base aislada `hcop_jp_qa_100` mediante la prueba integral del proyecto. Así los casos de búsqueda por nombre, DNI, HC, esquema, droga, ciclo, día y fecha se ejercitan con datos reales en lugar de quedar como `NO_DATA`.

No se debe apuntar este arnés a una base con pacientes reales.

## Documento relacionado

- [Matriz de 100 casos](HOSPITAL-DIA-100-CASOS.md)
- [Reporte de auditoría y remediación del 30/07/2026](REPORTE-AUDITORIA-HOSPITAL-DIA-2026-07-30.md)
- [Resultado final del 30/07/2026: 100 PASS, 0 FAIL, 0 NO_DATA y 0 MANUAL](resultados/hospital-dia-100-casos-20260730-100711.md)

La prueba integral multidroga se ejecuta por separado:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\integration-test.ps1
```

Recorre alta de paciente, prescripción, Farmacia, reserva, turno, triaje,
preparación, QR, interrupción/reacción, reanudación y cierre. La aceptación
final utilizó cuatro drogas, interrumpió Carboplatino al 50 %, reanudó y terminó
en `completed`, comprobando que la interrupción no se pierda al cerrar.

La suite Java complementaria terminó con **101/101 pruebas aprobadas**.
