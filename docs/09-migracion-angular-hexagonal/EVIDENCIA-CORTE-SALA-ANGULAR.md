# Evidencia de corte: Sala y Administracion Angular

Fecha: 2026-07-31

La ruta `/hospital-day/administration` implementa la cola diaria de Sala. Usa
la cola `administration` del backend y permite abrir una aplicacion concreta.

El flujo disponible es:

1. verificar paciente, etiqueta y segundo profesional;
2. iniciar la administracion;
3. registrar dosis real, tolerancia y finalizacion;
4. documentar una interrupcion con motivo, medidas, condicion y destino;
5. resolverla por reanudacion o cierre sin completar.

Los requisitos, la hora operativa, el segundo profesional, la preparacion
liberada, el TTL, las transiciones y las evoluciones inmutables son validados
en Java. La pantalla Angular solo captura datos y muestra el resultado de la
API.

Verificacion:

```powershell
docker build --target frontend-build --file Dockerfile --tag hcop-jp:administration-queue-check .
```

Resultado: compilacion Angular correcta; Sala se publica como modulo diferido.
