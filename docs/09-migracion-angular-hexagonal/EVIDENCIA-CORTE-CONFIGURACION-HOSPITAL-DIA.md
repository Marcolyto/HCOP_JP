# Evidencia de corte: Configuracion de Hospital de Dia Angular

Fecha: 2026-07-31

La ruta `/configuration/day-hospital` consulta y actualiza el registro
versionado `day-hospital-settings`. Permite administrar cantidad de sillones,
fraccion de 5/10/15/20/30 minutos y horario de jornada; expone tambien el
historial de revisiones.

La actualizacion conserva revision optimista. El servidor es quien valida y
aplica los ajustes al planificador y a la Agenda; los turnos existentes no se
modifican de forma implicita desde el navegador.

Verificacion:

```powershell
docker build --target frontend-build --file Dockerfile --tag hcop-jp:configuration-check .
```

Resultado: compilacion Angular correcta y modulo diferido.
