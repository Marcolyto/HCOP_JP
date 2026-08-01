# Corte hexagonal: paciente activo por sesión

Fecha de validación: 2026-07-31

## Alcance

La acción de abrir, reemplazar o cerrar el paciente de una sesión deja de
estar implementada dentro del servicio MVC de autenticación. El nuevo módulo
`patientcontext` conserva los contratos existentes y concentra la regla en un
caso de uso Java puro.

Las rutas públicas no cambian:

- `PUT /api/auth/active-patient`
- `POST /api/clinical/patients/{patientId}/activate`
- creación local de paciente, que lo deja activo automáticamente.

## Diseño aplicado

```text
HTTP / servicios heredados
        │
        ▼
ActivePatientContextUseCase
        │
        ├── PatientContextPatientPort
        └── SessionActivePatientPort
                 │
                 ▼
       JdbcPatientContextAdapter (PostgreSQL)
```

El caso de uso valida una sesión no vacía, verifica el paciente antes de
asignarlo y permite `null` exclusivamente para limpiar el contexto. El
adaptador conserva el hash SHA-256 del token en la tabla `local_sessions`; el
token sin hash no se persiste. Los errores se traducen al mismo contrato HTTP:
paciente inexistente devuelve `404` y sesión ausente o inválida devuelve `401`.

## Pruebas reproducibles

Como la estación de trabajo no tiene Maven instalado, la prueba se ejecutó en
el contenedor Java 21 que usa la imagen final:

```powershell
docker run --rm --mount "type=bind,source=${PWD},target=/workspace" `
  --workdir /workspace maven:3.9.11-eclipse-temurin-21 `
  mvn -q '-Dtest=ActivePatientContextApplicationServiceTest,HexagonalArchitectureTest' test
```

Resultado: correcto. Las cuatro pruebas del caso de uso verifican asignación,
limpieza, rechazo de paciente inexistente y rechazo de token vacío. ArchUnit
también confirma que el dominio y la aplicación no dependen de Spring, JDBC,
JSON ni del adaptador de persistencia.

## Límite actual

El resto de autenticación (login, contraseña, expiración y bootstrap de
usuarios) continúa en el módulo heredado. Por eso la matriz marca esta
capacidad como `En convivencia`, no como `Validada`.

## Verificación HTTP con PostgreSQL temporal

Sobre una base PostgreSQL efímera se inició la imagen Docker recién construida
y se comprobó el contrato desde HTTP: inicio de sesión, alta de paciente,
activación automática al crear, limpieza mediante `PUT /api/auth/active-patient`
con `patientId: null` y reapertura del mismo paciente. Las lecturas de
`GET /api/auth/me` devolvieron el identificador esperado después de crear y
reabrir, y `null` luego de cerrar.

Los contenedores, red y paciente usados en esta comprobación se eliminaron al
finalizar. La prueba no utiliza el puerto, la base ni los volúmenes de la
instalación estable.
