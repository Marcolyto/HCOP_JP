# ADR-0001: monolito modular hexagonal

- Estado: aceptada.
- Fecha: 30/07/2026.

## Contexto

HCOP JP contiene historia clínica, tratamientos, farmacia, turnos, triaje,
preparación, administración, configuración y archivos. La estructura actual
por funcionalidad con Controller–Service–Repository permitió consolidar el
producto, pero los servicios dependen de repositorios concretos y parte del
modelo está ligado a HTTP, JSON o SQL.

## Decisión

Evolucionar hacia un monolito modular hexagonal. Cada módulo tendrá dominio,
aplicación, puertos y adaptadores. La aplicación continuará desplegándose como
una sola unidad Java conectada a PostgreSQL.

## Motivos

- Probar reglas clínicas sin navegador, Spring ni base de datos.
- Sustituir adaptadores sin reescribir los casos de uso.
- Hacer explícita la autoridad de cada dato.
- Reducir efectos laterales entre tratamientos, farmacia y agenda.
- Conservar una operación y un despliegue sencillos.

## Consecuencias

- Aparecerán más interfaces y mapeadores, pero con responsabilidades claras.
- La migración será progresiva y convivirá con paquetes anteriores.
- ArchUnit vigilará las reglas de dependencia.
- No se crean microservicios ni bases separadas por módulo.
