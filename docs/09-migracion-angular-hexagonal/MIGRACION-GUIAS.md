# Corte vertical: Guías

Guías es el tercer corte vertical del backend hexagonal. Conserva las rutas y
el JSON utilizados por la interfaz vigente, pero separa dos responsabilidades
que antes estaban acopladas en un único servicio:

- los metadatos, la búsqueda, el estado y la revisión viven en Configuración;
- el contenido PDF vive en el almacenamiento de archivos.

## Flujo de dependencias

```text
adaptador HTTP
    |
    v
GuideCatalogUseCase
    |
    +--> GuideMetadataPort --> Configuración versionada --> PostgreSQL
    |
    +--> GuideFileStore ----> almacenamiento local persistente
```

El dominio define nombres de archivo y metadatos inmutables. La aplicación
coordina carga, descarga y archivado sin depender de Spring MVC, Jackson,
Servlet ni el sistema de archivos. Los adaptadores traducen HTTP, JSON,
Configuración y almacenamiento.

## Reglas conservadas y fortalecidas

- sólo se aceptan documentos PDF;
- el nombre se normaliza y no puede escapar del directorio asignado;
- el tamaño máximo se controla antes de persistir;
- el archivo se escribe mediante un temporal y se mueve de forma atómica
  cuando el sistema lo permite;
- una actualización exige revisión esperada;
- `expectedRevision`, usado por la interfaz, se acepta como alias de
  `revision`;
- archivar conserva metadatos e historial;
- las descargas reproducen exactamente los bytes almacenados;
- los errores de validación, archivo faltante, tamaño y conflicto usan el
  contrato común de errores.

## Contrato verificado

`scripts/guide-contract-test.ps1` ejecuta contra la aplicación empaquetada y
PostgreSQL reales:

1. carga un PDF;
2. consulta el catálogo y sus metadatos;
3. descarga y compara los bytes;
4. modifica el registro con control de versión;
5. rechaza una revisión desactualizada;
6. archiva y verifica los filtros activo/inactivo;
7. rechaza contenido y tipo inválidos;
8. devuelve `404` para un archivo inexistente.

Las pruebas unitarias cubren además la normalización del nombre, el caso de uso
y las escrituras seguras. ArchUnit verifica los límites de dependencia.

## Estado

El backend queda `En convivencia`: el límite hexagonal y su contrato están
validados, mientras la interfaz actual continúa operativa. La capacidad se
marcará `Validada` cuando exista su pantalla Angular, se comparen permisos y
apariencia y se pruebe la persistencia completa desde la nueva interfaz.
