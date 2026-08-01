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
- [Evidencia del corte de tratamiento](EVIDENCIA-CORTE-TRATAMIENTO-WORKFLOW.md):
  validacion Docker y visual del gate de Farmacia, reserva y programacion.
- [Evidencia del corte de Farmacia Angular](EVIDENCIA-CORTE-FARMACIA-ANGULAR.md):
  cola, detalle y acciones auditadas de validacion y reserva.
- [Evidencia del corte de Triaje Angular](EVIDENCIA-CORTE-TRIAJE-ANGULAR.md):
  cola diaria, formulario clinico y decisiones PASS o FAIL auditadas.
- [Evidencia del corte de Preparacion Angular](EVIDENCIA-CORTE-PREPARACION-ANGULAR.md):
  trazabilidad por droga, segundo control y liberacion a sala.
- [Evidencia del corte de Sala Angular](EVIDENCIA-CORTE-SALA-ANGULAR.md):
  doble chequeo, administracion, interrupcion, reanudacion y cierre.
- [Evidencia del corte de Agenda Angular](EVIDENCIA-CORTE-AGENDA-ANGULAR.md):
  lista de espera, grilla, arrastre y prevencion de superposiciones.
- [Evidencia del corte de Protocolos Angular](EVIDENCIA-CORTE-PROTOCOLOS-ANGULAR.md):
  catalogo COIR/local, detalle y componentes del esquema.
- [Evidencia de Configuracion de Hospital de Dia Angular](EVIDENCIA-CORTE-CONFIGURACION-HOSPITAL-DIA.md):
  sillones, fraccion, jornada e historial versionado.
- [Evidencia de Configuracion LLM Angular](EVIDENCIA-CORTE-CONFIGURACION-LLM-ANGULAR.md):
- [Evidencia de paciente activo hexagonal](EVIDENCIA-CORTE-PACIENTE-ACTIVO-HEXAGONAL.md):
  apertura, reemplazo y cierre de contexto por sesión sin acoplamiento a HTTP o JDBC.
  proveedor, endpoint, modelo, prueba y resguardo de la clave privada.
- [Evidencia de autenticación hexagonal](EVIDENCIA-CORTE-AUTENTICACION-HEXAGONAL.md):
  login, sesión, contraseña y almacenamiento de tokens hasheados.
- [Evidencia de Guías Angular](EVIDENCIA-CORTE-GUIAS-ANGULAR.md): biblioteca
  PDF, metadatos versionados, búsqueda y apertura autenticada.
- [Evidencia de Accesos Angular](EVIDENCIA-CORTE-ACCESOS-ANGULAR.md): usuarios,
  roles, permisos, sesiones y corrección del alta en PostgreSQL.
- [Evidencia del editor Angular de protocolos locales](EVIDENCIA-CORTE-EDITOR-PROTOCOLOS-ANGULAR.md):
  alta y edicion versionada de esquemas, drogas y tiempos operativos.
- [Validacion 31/07/2026](EVIDENCIA-VALIDACION-2026-07-31.md): compilacion,
  empaquetado completo y bateria de 142 pruebas Java.
