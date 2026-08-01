# Corte Angular: biblioteca de Guías

## Alcance

La ruta `/configuration/guides` reemplaza el flujo de biblioteca de guías con:

- listado y búsqueda incluyendo guías inactivas;
- importación de archivos PDF;
- alta y edición de título, categoría, audiencia, fuente, versión, etiquetas,
  descripción y estado activo;
- apertura autenticada del archivo PDF;
- configuración versionada mediante el módulo de configuración Java.

El archivo físico y sus metadatos se mantienen separados. Primero se importa
el PDF y luego se guarda la ficha; esto permite validar el contenido antes de
ofrecerlo a la biblioteca clínica.

## Contratos

| Operación | Endpoint |
| --- | --- |
| Listar | `GET /api/guides?includeInactive=1` |
| Importar PDF | `PUT /api/guides/import?name={nombre}` |
| Abrir PDF | `GET /api/guides/file?name={nombre}` |
| Crear ficha | `POST /api/clinical/configuration/guide` |
| Actualizar ficha | `PUT /api/clinical/configuration/guide/{id}` |

## Verificación integrada

En una instancia Docker con PostgreSQL y almacenamiento aislados se realizó:

1. inicio de sesión administradora;
2. subida de un PDF de prueba con firma válida;
3. creación de la ficha `Guia validacion Angular`;
4. consulta posterior de título, estado y etiquetas;
5. apertura autenticada del archivo, con respuesta HTTP 200 y tipo
   `application/pdf`.

Los recursos temporales fueron eliminados tras la comprobación.
