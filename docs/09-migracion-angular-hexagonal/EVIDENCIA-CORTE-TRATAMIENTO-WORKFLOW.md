# Evidencia del corte: tratamiento y turno Angular

Fecha de validacion: 2026-07-31

Este documento registra el alcance que ya fue probado sobre la rama Angular y
Java. No reemplaza la matriz de paridad; deja una evidencia reproducible para
el siguiente corte.

## Flujo validado

1. Login Angular con un usuario autorizado.
2. Apertura de un paciente y acceso a Tratamientos.
3. Visualizacion del arbol tratamiento -> ciclo -> dia -> aplicacion y de las
   drogas calculadas por el protocolo.
4. Apertura de Programar turno sobre un dia sin aplicacion.
5. Consulta del workflow de esa aplicacion.
6. Bloqueo de Guardar turno mientras Farmacia esta pendiente.
7. Seleccion de la procedencia `center_stock`.
8. Validacion de la orden y reserva atomica de todos los componentes.
9. Actualizacion de la duracion desde la aplicacion real: el tratamiento
   mostraba 145 minutos y el dia concreto corrigio la pantalla a 150.
10. Seleccion del sillon 2 y guardado del turno.

## Contratos involucrados

- `GET /api/clinical/patients/{patientId}/treatments`
- `GET /api/clinical/patients/{patientId}/treatments/{treatmentId}/detail`
- `GET /api/clinical/application-workflows/{patientId}/{treatmentId}/{cycleNumber}/{applicationDay}`
- `POST /api/clinical/application-workflows/{patientId}/{treatmentId}/{cycleNumber}/{applicationDay}/pharmacy-validation`
- `POST /api/clinical/application-workflows/{patientId}/{treatmentId}/{cycleNumber}/{applicationDay}/stock-reservation`
- `POST /api/clinical/infusions`

El servidor continua siendo la autoridad: exige permisos, revision optimista,
prescripcion confirmada, validacion de Farmacia, reserva cuando corresponde,
duracion exacta, jornada valida y ausencia de superposiciones. Angular solo
presenta el estado y guia el orden de las acciones.

## Pruebas ejecutadas

### Compilacion reproducible

```powershell
docker build --file Dockerfile --tag hcop-jp:treatment-actions-workflow-test .
```

Resultado: Angular genero el bundle, Java compilo 132 fuentes, Maven empaqueto
el jar y no hubo advertencias de presupuesto de estilos.

### Integracion completa

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\integration-test.ps1 `
  -BaseUrl http://127.0.0.1:5197 `
  -Username <USUARIO_DE_PRUEBA> `
  -Password <CONTRASENA_DE_PRUEBA>
```

Resultado observado: `ok=true`, motor `java-postgresql`, cuatro drogas,
Farmacia, reserva, turno, triaje PASS, preparacion, QR, interrupcion,
reanudacion, administracion completada y cinco evoluciones. Las credenciales
de prueba se proporcionan por variables del entorno y no se guardan en el
repositorio.

## Limite conocido del corte

La agenda visual completa de sillones, las colas especializadas de triaje,
preparacion y administracion, y sus formularios aun conviven con la interfaz
vigente. Sus endpoints Java y sus reglas de seguridad ya son la autoridad; el
proximo corte llevara esas pantallas a Angular con la misma evidencia E2E.
