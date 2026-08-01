# Corte Angular: usuarios, roles y permisos

## Alcance

La ruta `/configuration/access-control` permite administrar desde Angular:

- usuarios, sus datos profesionales, estado, contraseña y roles;
- roles locales, estado y catálogo de permisos;
- duración de sesión.

El login obligatorio se informa como regla de trazabilidad y no se ofrece una
opción para desactivarlo. El backend sigue siendo la única autoridad: al
cambiar contraseña o desactivar una cuenta, revoca sus sesiones activas.

## Corrección descubierta durante validación

La prueba integrada detectó que el alta de usuarios fallaba en PostgreSQL
cuando la consulta de duplicados recibía `excludedId = null`: la base no podía
inferir el tipo del parámetro. Se corrigió
`AdminRepository.usernameOrEmailExists` con el casteo explícito a `bigint`.
Con esto la API deja de devolver un error 500 y conserva la respuesta de
conflicto para duplicados reales.

## Verificación integrada

En PostgreSQL aislado se creó un rol temporal y luego un usuario asociado a
ese rol. La lectura posterior confirmó la relación. También se actualizó la
duración de sesión a 60 minutos y el servidor confirmó `loginRequired: true`.
Los recursos de esta prueba se eliminaron al finalizar.
