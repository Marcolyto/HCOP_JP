# Evidencia de corte: Agenda Angular

Fecha: 2026-07-31

La ruta `/hospital-day/schedule` muestra la lista de espera y la grilla de
sillones configurada para la fecha operativa. Soporta arrastrar una aplicacion
elegible a un casillero, mover un turno existente y quitarlo de la agenda.

El cliente detecta ocupacion visual para orientar al operador. La API de Java
es la autoridad final: valida duracion canonica de cada aplicacion, fraccion
horaria, jornada configurada, gate de Farmacia, revision optimista y la
restriccion de base que impide superposiciones.

La ruta es un modulo Angular diferido.

Verificacion:

```powershell
docker build --target frontend-build --file Dockerfile --tag hcop-jp:schedule-check .
```

Resultado: compilacion Angular correcta sin advertencias de presupuesto.
