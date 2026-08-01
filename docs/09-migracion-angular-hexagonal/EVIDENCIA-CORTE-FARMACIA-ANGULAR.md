# Evidencia de corte: Farmacia Angular

Fecha: 2026-07-31

## Alcance

La ruta Angular `/hospital-day/pharmacy` consume la cola existente
`GET /api/clinical/application-workflows?queue=pharmacy`. No duplica reglas
clinicas: el servidor Java mantiene permisos, validacion de la prescripcion,
control de revision optimista, procedencia y reserva de stock.

La pantalla permite:

- buscar por paciente, DNI, HC, esquema, diagnostico, droga o fecha;
- priorizar vencidas mas 7 dias, hoy, vencidas mas 30 dias o todas;
- filtrar por validacion y custodia de la medicacion;
- abrir el detalle de una aplicacion sin efectos laterales;
- validar o rechazar la orden con nota auditada;
- reservar stock del centro de forma explicita y solo cuando el servidor ya
  aprobo la orden y cada componente tiene dosis interpretable.

La reserva construye componentes por droga y deja que la API rechace faltantes,
duplicados, revision vencida o disponibilidad no demostrada. Procedencias que
no corresponden a stock del centro no exponen accion de reserva.

## Verificacion

Se ejecutó:

```powershell
docker build --file Dockerfile --tag hcop-jp:pharmacy-queue-test .
```

Resultado: compilacion Angular correcta y empaquetado Spring Boot Java
correcto. La API usada por la pantalla forma parte de la prueba de integracion
del circuito de aplicacion, que ya comprueba validacion, reserva, triaje,
preparacion y administracion contra PostgreSQL aislado.

## Pendiente del siguiente corte

Triaje, preparacion, sala y agenda siguen en la interfaz vigente. Se migran
despues como pantallas operativas independientes, reutilizando el mismo
contrato `application-workflows` y sin cambiar estados clinicos desde el
cliente.
