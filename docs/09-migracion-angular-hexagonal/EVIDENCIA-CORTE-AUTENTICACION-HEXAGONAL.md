
## Estado actualizado

La persistencia de identidad y sesiones ya está implementada por
`auth/infrastructure/persistence/JdbcAuthenticationStoreAdapter`, que satisface
`AuthenticationStorePort` con PostgreSQL. El antiguo repositorio no se registra
como componente Spring y sólo se conserva de forma recuperable durante esta
etapa de transición. No hay rutas activas que dependan de él.

El estado sigue siendo `En convivencia` únicamente porque `AuthService` conserva
el contrato de entrada de controladores e interceptor mientras se migran esos adaptadores web.
# Corte hexagonal: autenticación y sesión

Fecha de validación: 2026-07-31

## Alcance

El núcleo de autenticación deja de vivir dentro del facade Spring
`AuthService`. Las rutas, cookie y respuestas existentes se mantienen, pero
login, bootstrap, autenticación de sesión, logout y cambio de contraseña ahora
pasan por `AuthenticationUseCase`.

## Diseño aplicado

```text
AuthController / AuthInterceptor / servicios existentes
                         │
                         ▼
                   AuthService (compatibilidad)
                         │
                         ▼
              AuthenticationUseCase (Java puro)
                         │
       ┌─────────────────┼─────────────────┐
       ▼                 ▼                 ▼
 AuthenticationStore   PasswordHash   SessionToken
       │                 │                 │
       └────────── adaptadores PostgreSQL / BCrypt / SecureRandom ──────────┘
```

El token aleatorio no se persiste: el puerto de sesión calcula su huella
SHA-256 antes de escribir `local_sessions`. Los servicios de archivos clínicos
consumen ese mismo puerto para sus tokens de eliminación, sin depender del
facade de autenticación.

## Compatibilidad conservada

No se modificaron los endpoints ni el formato de las respuestas:

- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `PUT /api/auth/password`
- `PUT /api/auth/active-patient`

`AuthService` continúa como adaptador de compatibilidad para controladores,
interceptor y módulos heredados. La excepción de aplicación se traduce a los
mismos estados HTTP: `401` para credenciales o clave actual inválidas y `400`
para una nueva contraseña fuera de rango.

## Pruebas reproducibles

Pruebas unitarias y de arquitectura:

```powershell
docker run --rm --mount "type=bind,source=${PWD},target=/workspace" `
  --workdir /workspace maven:3.9.11-eclipse-temurin-21 `
  mvn -q '-Dtest=AuthenticationApplicationServiceTest,ActivePatientContextApplicationServiceTest,HexagonalArchitectureTest' test
```

Resultado: correcto. Se verifican token hasheado, credenciales incorrectas,
cambio de clave, revocación de sesiones ajenas, token actual obligatorio y los
límites de arquitectura.

Verificación HTTP sobre PostgreSQL temporal: inicio de sesión, cambio de
contraseña, rechazo de la contraseña anterior, logout, nuevo login y ciclo de
paciente activo. Resultado: correcto. La red, los contenedores y los datos de
esa prueba se eliminaron al finalizar; no se utilizó la instalación estable.

## Límite actual

El repositorio JDBC heredado permanece detrás de `LegacyAuthenticationStoreAdapter`
mientras se traslada a `auth/infrastructure/persistence`. La regla clínica y
el contrato ya están aislados; por eso el estado es `En convivencia`.
