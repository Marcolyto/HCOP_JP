# Evidencia de corte: Triaje Angular

Fecha: 2026-07-31

## Alcance

La ruta Angular `/hospital-day/triage` usa la cola existente
`GET /api/clinical/application-workflows?queue=triage&date=YYYY-MM-DD`.
Presenta los pacientes en el orden entregado por la API, filtrables por texto
y estado clinico.

Al abrir una aplicacion, el operador registra laboratorio, signos vitales,
ECOG, toxicidad y observaciones. PASS exige los campos clinicos minimos en la
interfaz y vuelve a ser validado por el servidor. FAIL exige motivo, permite
fecha propuesta y delega al servidor la liberacion de stock, retiro de turno y
traza clinica correspondiente.

No hay cambios de estado al navegar ni al cerrar el modal. Cada decision usa
revision optimista e idempotencia de `POST clinical-authorization`.

## Verificacion

Se ejecutó:

```powershell
docker build --file Dockerfile --tag hcop-jp:triage-queue-test .
```

Resultado: build Angular correcto y empaquetado Spring Boot Java correcto.
El contrato clinico y sus gates se mantienen en
`ApplicationWorkflowService` y se cubren mediante las pruebas de integracion
del circuito de aplicacion contra PostgreSQL aislado.
