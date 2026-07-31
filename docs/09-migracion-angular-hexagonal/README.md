# Migración Angular y arquitectura hexagonal

Esta carpeta gobierna la migración de HCOP JP. El objetivo es reemplazar el
frontend estático por Angular y evolucionar el backend hacia un monolito
modular hexagonal sin perder comportamiento, datos, permisos, documentación ni
capacidad de despliegue.

La versión operativa permanece disponible durante toda la transición. Cada
capacidad nueva se compara contra la línea base antes de habilitarse y el
frontend anterior sólo se retira cuando la matriz de paridad está completa.

## Documentos de control

- [Línea base](BASELINE-2026-07-30.md): evidencia técnica y funcional del punto
  de partida.
- [Matriz de paridad](MATRIZ-DE-PARIDAD.md): capacidades que deben conservarse y
  criterio de aceptación de cada una.
- [Arquitectura objetivo](ARQUITECTURA-OBJETIVO.md): módulos, capas, reglas de
  dependencia, Angular e infraestructura.
- [Contratos REST](CONTRATOS-REST.md): forma estable de respuestas, fechas,
  estados, concurrencia e idempotencia que consumirá Angular.
- [Reglas de arquitectura](REGLAS-ARQUITECTURA.md): límites hexagonales
  verificados automáticamente y kernel compartido.
- [Migración de Configuración](MIGRACION-CONFIGURACION.md): primer corte
  vertical, estrategia de convivencia y evidencia de paridad.
- [Migración de Protocolos](MIGRACION-PROTOCOLOS.md): unificación del catálogo
  COIR, protocolos locales, drogas y administración versionada.
- [Migración de Guías](MIGRACION-GUIAS.md): separación entre metadatos
  versionados y archivos, validación PDF y descargas seguras.
- [Migración del frontend Angular](MIGRACION-FRONTEND-ANGULAR.md): proyecto
  Angular, sesión, paciente activo, build Docker y convivencia bajo `/app/`.
- [ADR-0001](adr/ADR-0001-MONOLITO-MODULAR-HEXAGONAL.md): monolito modular
  hexagonal.
- [ADR-0002](adr/ADR-0002-ANGULAR-Y-CONVIVENCIA.md): Angular y convivencia
  progresiva.
- [ADR-0003](adr/ADR-0003-CONTRATOS-DATOS-Y-ROLLBACK.md): contratos, datos y
  rollback.

## Ciclo obligatorio por capacidad

1. Caracterizar el comportamiento vigente.
2. Incorporar o identificar pruebas que lo demuestren.
3. Implementar la nueva estructura.
4. Comparar API, persistencia, permisos e interfaz.
5. Corregir diferencias.
6. Actualizar OpenAPI y documentación.
7. Registrar un commit local verificable.
8. Publicar únicamente al completar el producto y su auditoría final.

## Reglas de seguridad de la migración

- La base PostgreSQL existente es la autoridad operacional.
- Flyway es el único mecanismo autorizado para modificar el esquema.
- Las migraciones serán aditivas mientras convivan ambas interfaces.
- No se elimina una ruta, tabla o campo sin demostrar que dejó de tener
  consumidores.
- El dominio no dependerá de Spring MVC, JDBC, JSON ni archivos.
- La interfaz Angular no implementará reglas clínicas que pertenezcan al
  servidor.
- `main` y la instancia estable del puerto 5180 no se utilizan para pruebas de
  la migración.
- La validación local usa el puerto 5181 y recursos Docker con prefijo
  `hcop_ajp_validation`.
- La imagen publicada para probar esta rama usa la etiqueta
  `angular-hexagonal-migration` y recursos persistentes `hcop_ajp_*`; nunca
  reemplaza `latest` ni los volúmenes estables.
