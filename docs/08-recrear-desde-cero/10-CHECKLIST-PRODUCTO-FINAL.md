# 10 · Checklist de producto final

Marque cada punto con evidencia. “Funciona en mi PC” no es evidencia de
producción.

## Arquitectura

- [ ] Tres servicios (`backend`/`bff`/`frontend`), un solo punto de entrada
      público.
- [ ] El navegador nunca habla directo con `backend` (sólo con nginx→`bff`).
- [ ] Cada módulo clínico separa `domain`/`application`/`infrastructure`.
- [ ] `domain`/`application` no importan Spring, JDBC ni Jackson (ArchUnit
      en verde).
- [ ] Ningún módulo importa el repositorio interno de otro directo (sólo su
      `port/in`, vía adapter propio).
- [ ] Los módulos clínicos son libres de ciclos entre sí.
- [ ] Controllers sin SQL.
- [ ] `ApplicationService` concentra reglas/transacciones.
- [ ] `Postgres*Store` usa SQL parametrizado.
- [ ] No existe dependencia operativa de Lira, Node.js legacy vanilla o
      MySQL.

## Base de datos

- [ ] Una base vacía aplica todas las migraciones.
- [ ] Reiniciar no duplica seeds.
- [ ] PK, FK, `CHECK`, únicos e índices revisados.
- [ ] Recursos compartidos tienen revisión.
- [ ] Turnos superpuestos se rechazan en PostgreSQL.
- [ ] Base y storage se respaldan/restauran juntos.

## Seguridad

- [ ] Login obligatorio.
- [ ] Contraseñas BCrypt.
- [ ] Cookie `BFF_SESSION` (BFF): HttpOnly/SameSite y Secure con HTTPS.
- [ ] El navegador nunca recibe el access/refresh token JWT (test explícito
      sobre el body de login).
- [ ] `backend` valida Bearer JWT firmado, sin cookies propias.
- [ ] Revocación inmediata: deshabilitar usuario/cambiar contraseña/
      reasignar roles invalida el access token ya emitido, sin esperar el
      TTL.
- [ ] Permisos verificados en servidor.
- [ ] Matriz de roles probada.
- [ ] QR firmado e idempotente.
- [ ] Secretos fuera de Git/logs/respuestas.
- [ ] PostgreSQL y Redis no están expuestos públicamente.

## Clínica

- [ ] Nuevo paciente comienza con hoja en blanco.
- [ ] Todos los modales tienen `X` y permanecen abiertos al tocar el fondo,
      presionar `Esc` o esperar.
- [ ] Diagnóstico conserva SNOMED, CIE-10, AJCC, TNM y estadio.
- [ ] Agregar diagnóstico no borra los anteriores.
- [ ] Prescripción vincula diagnóstico/protocolo y crea ciclos.
- [ ] Peso/talla y datos del tratamiento generan evolución.
- [ ] Farmacia separa prescripción y medicación.
- [ ] Turnos respetan duración y sillón.
- [ ] Suspensión/reanudación deja trazabilidad.
- [ ] QR abre el ciclo correcto y finalización es idempotente.
- [ ] Actos clínicos relevantes generan evolución y auditoría.

## Archivos

- [ ] Formatos/tamaños validados en servidor.
- [ ] Firma binaria y SHA-256.
- [ ] Storage fuera del JAR/Git.
- [ ] Descargas autenticadas.
- [ ] No existe traversal.
- [ ] Original y derivado de imagen se distinguen.
- [ ] Eliminación temporal exige sesión/grant.

## API y documentación

- [ ] Todos los endpoints tienen resumen y descripción.
- [ ] Cada operación declara controller, permiso y respuestas.
- [ ] Swagger abre y ejecuta con Bearer.
- [ ] `generate-openapi-snapshot.ps1 -Check` sin diff (guardián real del
      contrato — bloqueante).
- [ ] Catálogo Markdown/HTML está sincronizado.
- [ ] Modelo, campos, variables y operación están documentados.
- [ ] Decisiones importantes tienen ADR.
- [ ] Todos los enlaces documentales pasan validación.

## Interfaz

- [ ] No hay errores de consola.
- [ ] No hay doble scroll.
- [ ] Modales manejan foco/teclado.
- [ ] Estados no dependen sólo de color.
- [ ] Cargando, vacío, error y conflicto son claros.
- [ ] Recargar conserva sólo lo persistido en servidor.
- [ ] Segunda sesión no comparte paciente activo.
- [ ] Turnero relee después de conflicto.

## Pruebas

- [ ] `mvn verify` exitoso en `backend/` y en `bff/`.
- [ ] ArchUnit (hexagonal + libre de ciclos) en verde.
- [ ] Repositories probados con PostgreSQL real.
- [ ] Permisos `401/403`.
- [ ] Revisión `409`.
- [ ] Concurrencia de sillones.
- [ ] Reintentos QR/finalización.
- [ ] Flujo integral completo.
- [ ] Docker desde cero.
- [ ] CI verde.
- [ ] Datos de prueba ficticios y efímeros.

## Operación

- [ ] Contenedores no root.
- [ ] Health/readiness.
- [ ] Volúmenes persistentes.
- [ ] HTTPS/VPN para acceso externo.
- [ ] Logs/alertas/retención.
- [ ] Backup automatizado.
- [ ] Restauración ensayada.
- [ ] Procedimiento de actualización y rollback.
- [ ] Imagen versionada y digest registrado.

## Aprobación

| Rol | Nombre | Fecha | Evidencia / observaciones |
|---|---|---|---|
| Responsable clínico | | | |
| Responsable técnico | | | |
| Operación/infraestructura | | | |
| Seguridad/privacidad | | | |
