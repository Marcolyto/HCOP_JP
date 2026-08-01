# Corte vertical: Protocolos

Protocolos es el segundo corte vertical de la migración. La capacidad combina
dos fuentes sin confundir su autoridad:

- los esquemas COIR distribuidos con la aplicación son catálogo de referencia;
- los protocolos locales versionados en PostgreSQL son configuraciones
  clínicas administrables.

La API presenta ambas fuentes como un catálogo único y evita mostrar una
entrada COIR como pendiente cuando ya existe un protocolo local vinculado.

## Arquitectura

```text
adaptador HTTP
    |
    v
ProtocolManagementUseCase
    |
    +--> ConfigurationManagementUseCase --> PostgreSQL
    |
    +--> ProtocolCatalogPort -------------> catálogo COIR
    |
    +--> DrugCatalogPort -----------------> drogas y preparaciones
```

El núcleo contiene identificadores diferenciados para protocolos locales y
COIR, además de un documento estructurado Java inmutable. No depende de Spring,
Jackson, JDBC ni archivos.

Los adaptadores conservan el catálogo histórico durante la convivencia. El
caso de uso nuevo decide:

- qué protocolos locales listar;
- qué entradas COIR ya están vinculadas;
- cómo abrir un detalle con sus componentes;
- cuándo invalidar la caché después de crear, editar o archivar;
- qué duraciones y categorías exponer;
- qué identificadores pueden modificarse.

Una entrada COIR no puede editarse ni archivarse directamente. Debe convertirse
en protocolo local desde la interfaz de administración.

## Contrato conservado

Se mantienen sin cambios:

- `GET /api/clinical/protocols`;
- `GET /api/clinical/protocols/{id}`;
- `POST /api/clinical/protocols`;
- `PUT /api/clinical/protocols/{id}`;
- `DELETE /api/clinical/protocols/{id}`;
- `GET /api/clinical/coir-catalog`;
- `GET /api/clinical/drugs`.

Los protocolos locales siguen entregando los metadatos de Configuración y sus
campos clínicos también a nivel superior. Las entradas COIR mantienen el
prefijo `coir-`, la duración legible, cantidad de componentes y detalle de
drogas, aplicaciones y presentaciones.

## Validaciones y concurrencia

- El nombre y la definición son obligatorios.
- Los días por ciclo y la duración operativa, cuando se informan, deben ser
  mayores que cero.
- Una edición usa la revisión recibida y devuelve `409 VERSION_CONFLICT` si el
  registro cambió.
- Archivar conserva el historial y los tratamientos que ya lo utilizaron.
- Un identificador inválido o un intento de mutar el catálogo devuelve
  `404 PROTOCOL_NOT_FOUND`.

## Evidencia automática

`scripts/protocol-contract-test.ps1` ejecuta contra la aplicación empaquetada y
PostgreSQL real:

1. integridad del catálogo COIR;
2. detalle de esquema con componentes;
3. creación de un protocolo local vinculado;
4. eliminación del duplicado visual COIR;
5. actualización de duración y revisión;
6. rechazo de una edición desactualizada;
7. búsqueda de drogas;
8. archivo y recuperación en listados inactivos;
9. rechazo de mutaciones sobre el catálogo.

Las pruebas unitarias cubren además los identificadores, la combinación de
fuentes, el mapeo JSON histórico y los puertos. La regresión clínica completa
confirma que el catálogo migrado continúa permitiendo prescribir, preparar y
administrar tratamientos.

La capacidad queda `En convivencia`: el backend ya está en el límite
hexagonal, pero su interfaz Angular y la comparación visual todavía están
pendientes.
