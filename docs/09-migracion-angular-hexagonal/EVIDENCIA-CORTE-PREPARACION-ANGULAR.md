# Evidencia de corte: Preparacion Angular

Fecha: 2026-07-31

La ruta `/hospital-day/preparation` consume la cola `preparation` y opera una
aplicacion por vez. Permite iniciar preparacion, registrar una traza por cada
droga y liberar la mezcla hacia sala.

La interfaz requiere lote, vencimiento, cantidad, unidad, diluyente, volumen,
concentracion, TTL y un segundo profesional. El cliente no determina si una
mezcla es valida: Java verifica PASS previo, disponibilidad, reserva, lote,
componentes uno a uno, TTL, usuario verificador y revision optimista.

Las rutas de Farmacia, Triaje y Preparacion son lazy-loaded. La compilacion
Angular produjo un paquete inicial de 457.47 kB y tres modulos operativos
independientes, sin advertencia de presupuesto inicial.

Verificacion ejecutada:

```powershell
docker build --target frontend-build --file Dockerfile --tag hcop-jp:angular-workflows-check .
```
