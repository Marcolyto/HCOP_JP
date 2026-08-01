# Corte Angular: edicion de protocolos locales

## Alcance entregado

La ruta Angular `/protocols` ya ofrece la lectura del catalogo COIR y de los
protocolos locales. Este corte incorpora el editor local versionado:

- `/protocols/new`: crea una definicion local.
- `/protocols/{id}/edit`: edita una definicion local existente.
- Cada componente registra droga, identificador del catalogo cuando existe, dia de aplicacion,
  dosis, unidad, metodo de calculo, via, tiempo de administracion y ambito.
- Los campos `cycleDays` y `durationMinutes` mantienen la informacion que usan
  prescripcion, farmacia, agenda por sillones y Hospital de Dia.
- Un protocolo COIR se muestra como catalogo de solo lectura. No se modifica
  desde este formulario.
- El campo de droga consulta el catalogo local y COIR por coincidencia, permite
  elegir una entrada catalogada y conserva el ingreso manual para una droga que
  todavia no figure en el catalogo.

## Contrato y protecciones

La interfaz consume los contratos ya expuestos por el backend:

| Operacion | Endpoint | Proteccion |
| --- | --- | --- |
| Leer catalogo | `GET /api/clinical/protocols?includeCatalog=1` | permisos de lectura |
| Leer definicion | `GET /api/clinical/protocols/{id}` | permisos de lectura |
| Crear protocolo local | `POST /api/clinical/protocols` | permisos de configuracion |
| Actualizar protocolo local | `PUT /api/clinical/protocols/{id}` | permisos de configuracion y revision |

El campo `revision` se envía en cada actualizacion. El servidor es la autoridad
para concurrencia, permisos y persistencia; Angular solo valida que exista la
informacion clinica minima antes de solicitar el cambio.

## Verificacion realizada

El 31/07/2026 se ejecuto:

```text
docker build --target frontend-build --file Dockerfile --tag hcop-jp:protocol-editor-check .
```

Resultado: compilacion Angular correcta. El editor se entrega como chunk
diferido `protocol-editor-page-component`, sin aumentar el paquete inicial.

## Prueba integrada contra PostgreSQL

El 31/07/2026 se levantó una instancia aislada con proyecto Docker
`hcop-ajp-validation`, puerto `5181`, base y volumenes propios. Con una sesion
administradora real se comprobó:

1. Inicio de sesion correcto.
2. Lectura de 803 protocolos y busqueda de `Carboplatino` en el catalogo.
3. Creacion de un protocolo local temporal con una droga, dia, dosis, via y
   tiempo de administracion.
4. Actualizacion del mismo protocolo con la revision devuelta por el servidor.
5. Lectura posterior que confirmó la revision `2`, duracion `100` y componente
   `Carboplatino` en dia `1`.

La prueba se hizo solo sobre la base aislada de validacion; no se modificaron
datos de uso ni volumenes de la instancia estable.

## Pendiente antes de retirar la interfaz historica

1. Exponer archivado y restauracion con confirmacion explicita y auditoria.
2. Ejecutar una prueba de integracion autenticada de crear, editar y prescribir
   un protocolo local desde la imagen completa.
