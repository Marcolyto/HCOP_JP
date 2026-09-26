# HCOP: cambios de estudios, repositorios y diagnóstico

Guía para trasladar esta actualización a otro repositorio mediante GPT. Preparada el 18 de septiembre de 2026 a partir del código y del diff publicado, no sólo del historial de la conversación.

**Nota del 25 de septiembre:** esta guía y su parche siguen describiendo el commit `4bd266b`. La rama incorpora después correcciones del buzón clínico, turnero, empaquetado y operación. Por solicitud posterior del propietario, las credenciales de las seis fuentes de estudios se distribuyen ahora en [`config/study-repositories.env`](../../config/study-repositories.env); su carga y sustitución se explican en [`config/README.md`](../../config/README.md). Esa decisión posterior no forma parte del parche histórico ni incluye pacientes o claves internas de HCOP.

## 1. Versiones que se comparan

| Referencia | Valor |
| --- | --- |
| Repositorio de referencia | https://github.com/Marcolyto/HCOP_JP |
| Base anterior exacta | `f032b0ab4fcc40f6b393695e782b813c39958823` |
| Versión funcional de destino | `4bd266b67ee6402c9e6b892656f414e8c6b15776` |
| Rama donde se publicó | `codex/studies-diagnosis-repositories` |
| Alcance del commit | 81 archivos; 7.824 líneas añadidas y 137 eliminadas |

[Ver el commit completo](https://github.com/Marcolyto/HCOP_JP/commit/4bd266b67ee6402c9e6b892656f414e8c6b15776) · [Ver el árbol de código fijado a esta versión](https://github.com/Marcolyto/HCOP_JP/tree/4bd266b67ee6402c9e6b892656f414e8c6b15776) · [Comparar ambas versiones](https://github.com/Marcolyto/HCOP_JP/compare/f032b0ab4fcc40f6b393695e782b813c39958823...4bd266b67ee6402c9e6b892656f414e8c6b15776).

**“Versión previa” significa aquí la base `f032b0a`, sobre la que se trabajó.** No significa el estado actual de `main` ni el de otra rama estable. Esa base se eligió antes de incorporar el buscador de ensayos clínicos de Investigación. Este commit agrega los cambios descriptos abajo; no contiene una reversión general de Investigación ni autoriza a eliminar funcionalidades propias del repositorio receptor.

El archivo acompañante `hcop-f032b0a-a-4bd266b.patch` contiene el diff exacto de los 81 archivos. SHA-256:

```text
e68bd459ca82789c7a521a722374bd62fcc9012f69fbba53c8a4c094b64b1d48
```

La guía sirve sin el parche si GPT tiene acceso al commit de referencia. Entregar ambos permite trabajar también con una copia local del cambio.

## 2. Qué cambió respecto de esa base

| Área | Situación anterior / problema | Comportamiento de esta versión |
| --- | --- | --- |
| Plantillas e imágenes | La imagen podía no dibujarse al abrir el editor o perder la selección al completar cargas asíncronas. | La imagen se muestra al estar listos la carga y el lienzo; las respuestas anteriores no pisan la selección vigente. |
| Ediciones de imágenes | Era necesario recuperar la visualización y el uso de sus ediciones. | Original y copias anotadas permanecen dentro del mismo estudio, con selección de versión y persistencia al recargar. |
| Imágenes en evolución | Faltaba completar el recorrido desde Estudios/editor hasta la evolución. | Se adjunta la versión elegida a un borrador editable y se conserva esa versión aunque después se vuelva a editar el estudio. |
| Lista de estudios | La base no tenía este buscador integrado de siete fuentes en Java. | Primero se muestra la lista unificada; debajo, los archivos locales. Cada institución puede fallar sin impedir mostrar las demás. |
| Distribución de Estudios | Se pidió más espacio a la izquierda y evitar una tabla con scroll vertical propio. | Margen interno izquierdo de 16 px; tabla con altura natural y un desplazamiento general junto con la galería. |
| Archivos subidos | Las nuevas cargas podían aparecer al principio. | Se apilan al final por el orden de carga original; editar no cambia su posición. |
| PDF locales | Faltaba ver el documento completo y disponer de las acciones de las imágenes. | Todas las páginas tienen altura proporcional, ampliación, edición y envío a evolución. |
| Fuentes externas | Faltaba centralizar búsquedas, sesiones y resolución de informes en el proyecto. | Conectores Java independientes y respuestas JSON, sin instalar PHP en HCOP. Patología mantiene la excepción remota acordada. |
| Configuración | No existía la solapa Repositorio de esta integración. | Siete sitios con endpoints, usuario y gestión de contraseña; cambios efectivos en consultas siguientes. |
| Informes de Centro | El enlace directo podía responder `403 Forbidden`. | El backend recupera el PDF con la referencia del visor y lo entrega autenticado, en memoria y sin copia permanente. |
| Diagnóstico | La vista resumida podía omitir clasificaciones/TNM y leer una colección distinta de la usada al guardar. | Tarjetas azules arriba de la historia con AJCC, SNOMED CT, CIE-10 y TNM/estadio; lectura compatible con registros actuales y antiguos. |
| Diagnóstico a evolución | Faltaba una acción explícita desde el resumen. | `Agregar a evolución` por diagnóstico y `Guardar y agregar a evolución` en el formulario. |
| Fichas antiguas | Agregar un diagnóstico nuevo podía dejar de representar el diagnóstico legado. | Se conserva el anterior con fecha, códigos, TNM y auditoría al materializar la lista de diagnósticos. |
| Estadio manual | Un cálculo asíncrono pendiente podía sobrescribir un cambio posterior. | Se invalidan respuestas atrasadas al cambiar sitio, ejes o estadio manual. |

## 3. Instrucción para GPT en el repositorio receptor

Copiar este bloque como solicitud y adjuntar la guía y, de ser posible, el parche:

> Actualizá este repositorio para reproducir el comportamiento de HCOP documentado en GUIA_ACTUALIZACION_GPT.md, tomando como referencia funcional el commit 4bd266b67ee6402c9e6b892656f414e8c6b15776 y su diferencia respecto de f032b0ab4fcc40f6b393695e782b813c39958823. Primero inspeccioná la arquitectura, las instrucciones del repositorio, los cambios locales, los modelos clínicos y la configuración de despliegue. Elegí entre aplicar el commit/parche o adaptar sus cambios según la compatibilidad real. Implementá, verificá y entregá el resultado; no te limites a proponer un plan. Conservá las funcionalidades y los datos del destino. Usá el backend Java existente, credenciales fuera del código y el estilo visual actual. No copies pacientes, bases, archivos clínicos, secretos ni configuraciones privadas del proyecto de referencia. No ejecutes limpieza de Docker ni restablecimientos de fábrica. Si faltan credenciales externas, completá la integración y las pruebas con fuentes ficticias, e indicá qué configuración real queda pendiente. Cerrá con archivos modificados, verificaciones realizadas, limitaciones concretas e instrucciones de despliegue adaptadas al destino.

La entrega mínima son las siete áreas funcionales de las secciones 5 a 11 y sus verificaciones. No alcanza con copiar sólo HTML/CSS o cambiar el texto de un botón.

## 4. Estrategia de traslado

### 4.1 Inspección inicial

1. Leer las instrucciones locales del repositorio y comprobar rama, remoto, estado de trabajo y archivos sin guardar.
2. Identificar si el destino deriva de HCOP y si contiene Java/Spring, Angular y los mismos modelos. La referencia usa Java 21, Spring Boot 4.1.0, PostgreSQL, Angular 22.1 y un Dockerfile que compila frontend y backend.
3. Verificar servicios existentes de autenticación, permisos, paciente activo, guardado con revisión/conflictos, almacenamiento de medios, configuración cifrada y borradores clínicos. Reutilizarlos; no reemplazarlos por atajos.
4. Comparar los archivos afectados contra el commit de referencia. No actualizar de manera general las versiones del framework sólo para hacer coincidir los manifiestos.
5. Trabajar en una rama propia y conservar los cambios locales del usuario. No emplear `reset --hard`, un push forzado ni una sustitución completa del repositorio receptor.

Dependencias a revisar expresamente: `AuthContext`, `AuthService`, `SessionPrincipal`, `PatientService`, `ApiException`, `SystemSettingsRepository`, `SecretBox`, `HcopProperties` y `Clock`. El código de referencia usa Jackson 3 (`tools.jackson.databind`); si el destino tiene Jackson 2/Spring Boot 3, adaptar esa integración en lugar de copiar `pom.xml`. Los conectores utilizan virtual threads de Java 21 y el parser HTML de `java.desktop`, que debe existir en un runtime Java reducido. Este commit no agrega dependencias Maven ni modifica Dockerfile o Compose.

### 4.2 Si el destino tiene la misma base y contratos

La forma más fiel es importar el commit. Ejemplo, después de verificar que el estado de trabajo permite hacerlo:

```sh
git switch -c actualizacion/estudios-diagnostico
git fetch https://github.com/Marcolyto/HCOP_JP.git codex/studies-diagnosis-repositories
git show --stat 4bd266b67ee6402c9e6b892656f414e8c6b15776
git cherry-pick 4bd266b67ee6402c9e6b892656f414e8c6b15776
```

Verificar el hash fijo, aunque la rama remota reciba otros cambios. Resolver cada conflicto preservando la intención del destino y las invariantes clínicas de esta guía; un cherry-pick que termina sin conflictos igualmente requiere pruebas.

Con el parche local y código compatible:

```sh
git apply --check /ruta/al/hcop-f032b0a-a-4bd266b.patch
git apply /ruta/al/hcop-f032b0a-a-4bd266b.patch
```

Sustituir la ruta por la ubicación real; si la comprobación falla, adaptar los cambios. No forzar hunks ni dar por actualizada la aplicación sólo porque el parche pudo aplicarse.

### 4.3 Si el destino es más antiguo, distinto o tiene modificaciones propias

Usar el diff como especificación y trasladar por módulos en este orden:

1. Modelos y pruebas de imágenes/versiones, adjuntos, PDF y diagnósticos.
2. Conectores Java, búsqueda, manejadores de informes y configuración cifrada.
3. Clientes Angular y solapa Repositorio.
4. Panel Estudios, editor, PDF y borradores de evolución.
5. Resumen diagnóstico, impresión y compatibilidad con fichas antiguas.
6. Pruebas de integración, compilación y comprobación visual.

Si faltan servicios que ya existían en la base de referencia, implementar su equivalente mínimo respetando los contratos del destino. Cambiar los paquetes Java, imports y rutas sólo cuando la estructura receptora lo requiera. No renombrar contratos persistidos sin una estrategia explícita de compatibilidad.

## 5. Estudios locales, plantillas y editor

El panel debe renderizar primero la lista y después una galería vertical de archivos. Usar el estilo de HCOP y mantener visibles las imágenes cargadas por plantilla o por explorador de archivos.

- La presencia y las acciones de una imagen se calculan desde el registro persistido, no desde la cola temporal de subidas. Recargar debe conservarlas.
- Mantener el original y agregar copias anotadas al mismo estudio. No crear un nuevo estudio por cada edición.
- Conservar identificadores, metadatos de plantilla, auditoría y fechas originales. Editar no reordena el archivo en la galería.
- Ordenar archivos por `createdAt` ascendente; si falta, usar el `attachments[].uploadedAt` más antiguo. Los registros sin fecha de carga quedan primero y los empates conservan el orden persistido. No ordenar por fecha clínica, título o `updatedAt`.
- Separar estados de carga del catálogo, carga de imagen, lienzo montado y exportación. No exportar si la imagen o el lienzo todavía no están listos.
- Invalidar cargas/exportaciones antiguas al cambiar de archivo, plantilla, paciente o al cerrar/destruir el editor. Liberar los object URLs y los recursos que dejan de usarse.
- Mantener herramientas existentes: dibujo, figuras, colores, grosor, borrado y exportación, según los controles presentes en el destino.
- Ofrecer envío a evolución desde la imagen y desde el editor. Mantener permisos separados para ver, editar y agregar evolución.
- En la versión final los archivos con vista previa viven en la galería; no resolver la regresión únicamente renombrando `Abrir archivo` o `Mostrar` en una fila.

Los archivos continúan en `state.studies`, conservando `fileName`, `fileType`, `fileSize`, `fileCategory`, `fileSha256`, `fileUrl` y `attachments`. Las imágenes y sus copias usan `imageAssets`, con `versions`, `activeVersionId` y `currentUrl`. La proyección también lee representaciones históricas como `previewImageUrl`, `displayImageUrls` y adjuntos: no normalizar toda la ficha mediante escrituras al abrirla. Los archivos no representables por el navegador siguen disponibles como documentos.

La subida del PNG y el guardado del documento clínico son dos operaciones. Si falla la segunda, conservar URL, identificador de versión y auditoría ya obtenidos para reintentar sobre la revisión vigente, sin subir de nuevo ni duplicar la edición. Mantener los bloqueos por paciente y el registro de borradores. Conservar el prefijo CSS `angular-` de los nuevos controles: algunas clases del frontend histórico ocultan botones si se reutilizan sin revisar.

Archivos guía: `study-panel.component.*`, `study-panel.models.ts`, `study-image-presentation.ts`, `study-template-editor.component.*` y `study-template-editor.state.ts`.

## 6. PDF completos y acciones por página

Se incorpora **`pdfjs-dist` 6.3.289**, con `package-lock.json` actualizado. En `angular.json` se publican localmente:

- `build/pdf.worker.min.mjs` → `assets/pdfjs`.
- `cmaps`, `standard_fonts`, `wasm` e `iccs` → subcarpetas correspondientes de `assets/pdfjs`.

No depender de un CDN ni del visor PDF nativo dentro de un iframe de altura fija. El documento ocupa el alto de todas sus páginas y comparte el scroll del panel.

En la referencia los recursos se sirven en `/app/assets/pdfjs/` y los PDF locales se obtienen del mismo origen mediante `/api/media/studies/<archivo>`, usando la sesión. Adaptar conjuntamente las rutas si cambia la base del frontend. El visor no debe aceptar una URL arbitraria, credenciales embebidas, query/fragmentos ni navegación entre directorios. Conservar la desactivación de evaluación dinámica y XFA.

- Calcular la proporción de cada página por separado; admitir páginas horizontales o tamaños mixtos.
- Renderizar páginas cercanas a la vista para limitar memoria, conservando el espacio natural de las restantes.
- Permitir **Ampliar página**, zoom, **Editar página** y **Agregar a evolución**.
- Guardar las copias PNG por página en `pdfPageAssets` del mismo registro, separadas del PDF original y de las imágenes ordinarias.
- Cada entrada de `pdfPageAssets` identifica `pageNumber`, `versions` y `activeVersionId`. Cada versión conserva `id`, `url`, `kind`, tipo MIME, fecha y auditoría.
- `kind: "annotation"` representa una copia editada; `kind: "snapshot"`, una captura de la página original. No llamar “anotada” a una captura sin edición.
- Guardar una anotación selecciona esa versión; la copia del original no debe reemplazar silenciosamente la selección del usuario.
- Revalidar paciente, documento, página y versión antes de completar una captura o guardar. Un resultado atrasado no debe adjuntarse a otro paciente.
- El PDF original conserva sus bytes y su URL. La edición se aplica sobre una copia de la página, no modifica el documento fuente.
- Repetir identificador, URL y tipo de versión es idempotente; reutilizar el identificador con otro contenido debe producir un error.

Archivos guía: `study-pdf-viewer.component.*`, `study-pdf-viewer.models.ts` y `study-pdf-page.models.ts`.

## 7. Imágenes y PDF en evolución

`EvolutionEntryDraft` admite `attachments`. El nuevo contrato `EvolutionImageAttachment` incluye `id`, `url`, `title` y referencias/metadatos opcionales: `studyId`, `imageId`, `versionId`, `annotated`, `thumbnailUrl`, `studyDate`, `studyType`, `caption`, `templateSource`, `audit` y `createdAt`.

- Normalizar y validar las URLs con las reglas de medios de la aplicación.
- Guardar la versión elegida como referencia estable. Cambiar luego la selección o editar el estudio no debe alterar una evolución ya guardada.
- Conservar el texto clínico, el profesional, la fecha, la especialidad y los adjuntos al guardar mediante el servicio clínico normal.
- Mantener `linkedStudyIds` sin repeticiones.
- Mostrar los adjuntos en el borrador y permitir retirarlos antes de confirmar.
- Un borrador precargado con texto o adjuntos cuenta como modificado: cancelar debe permitir seguir editando o descartar.
- Persistir únicamente cuando el usuario confirma **Cargar en hoja**. Respetar permisos, revisiones y conflictos existentes.
- Exigir texto clínico aunque el borrador tenga imágenes; los adjuntos no sustituyen ese campo.

El envío a evolución no permite escribir silenciosamente en una ficha real ni convertir las pruebas de aceptación en registros de producción.

## 8. Buscador de estudios externos en Java

### Fuentes

| ID | Institución | Mecanismo |
| --- | --- | --- |
| `fuesmen` | FUESMEN | Sesión del portal y búsqueda directa desde Java. |
| `idm` | IDM | Sesión del portal y búsqueda directa desde Java. |
| `centro` | Centro del Diagnóstico | Cuenta propia en Informe Médico. |
| `espanol` | Hospital Español | Cuenta propia en Informe Médico. |
| `malargue` | Malargüe | Cuenta propia en Informe Médico. |
| `labo` | Laboratorio Schestakow | Login y consulta directa; informe desde `labResPdfinit.asp`. |
| `patologia` | Patología | Excepción: listado remoto existente; alternativa JDBC configurada en servidor. |

Consultar las siete fuentes. No usar el listado general de `fuesmen.com` como agregador de las otras instituciones. Patología puede consultar su listado en FUESMEN y resolver archivos en Pangea; no se agrega PHP al backend del proyecto.

### API y flujo

`GET /api/patients/{patientId}/external-studies`

- Exige sesión, permiso `section.studies.view` y correspondencia con el paciente activo.
- Obtiene el DNI desde la ficha del servidor. No acepta como autoridad un DNI arbitrario enviado por el frontend.
- Devuelve `patientId`, `searchedAt`, `studies`, `sources`, `partial` y `total`.
- Cada fila conserva identificador, institución/fuente, fecha, tipo, descripción y enlaces independientes `studyUrl` y `reportUrl` cuando existen.
- Cada fuente informa su resultado (`ok`, `empty` o `error`), cantidad y mensaje correspondiente. Un fallo de metadatos puede conservar estudios y marcar la respuesta parcial.
- Una fuente puede informar `error` con `count > 0`: significa que se conservaron resultados parciales, no que deban ocultarse.
- Aislar errores, sesiones, cookies y plazos por fuente. Ordenar los resultados combinados por fecha; conservar la institución de origen y evitar colisiones de identificadores.
- Revalidar el paciente activo al terminar. Angular debe descartar resultados de una ficha anterior.
- Actualizar estudios vuelve a consultar; un error externo no oculta la galería local ni bloquea su edición.
- En un refresco fallido del mismo paciente se conservan los resultados previos; al cambiar la identidad paciente/DNI se limpian. Un cambio cualquiera en la historia no debe lanzar otra búsqueda innecesaria.
- No insertar el HTML de las instituciones directamente en Angular ni escribir automáticamente el resultado de búsqueda en la historia clínica.

Campos exactos de cada elemento de `studies`: `id`, `date`, `type`, `title`, `source`, `reportUrl`, `studyUrl`, `sourceId`. El identificador lleva el prefijo `external:<sourceId>:`. Ordenar por fecha descendente y desempatar por ID. Una URL de informe ausente se representa vacía. El frontend debe aceptar las rutas locales relativas protegidas además de enlaces HTTP(S).

Informe Médico requiere tres sesiones independientes. Implementar el login con CSRF, redirección final del portal, consulta LiveView/WebSocket, paginación, validación de DNI por fila y resolución de los visores. Obtener primero los visores y luego sus metadatos de informes: un informe ausente no debe hacer desaparecer el estudio.

Copiar/adaptar el protocolo implementado y sus fixtures desde el commit. No reconstruirlo a partir de un único enlace real ni asumir que las tres cuentas muestran las mismas personas.

### Límites de la versión de referencia

| Variable | Valor predeterminado | Rango aceptado |
| --- | --- | --- |
| `EXTERNAL_STUDIES_SOURCE_TIMEOUT_SECONDS` | 30 s | 1–30 |
| `EXTERNAL_STUDIES_GLOBAL_TIMEOUT_SECONDS` | 80 s | 1–85 |
| `EXTERNAL_STUDIES_MAX_RESPONSE_BYTES` | 2.097.152 | 4.096–8.388.608 |
| `EXTERNAL_STUDIES_MAX_DOCUMENT_BYTES` | 26.214.400 | 4.096–52.428.800 |
| `EXTERNAL_STUDIES_MAX_RESULTS` | 500 | 1–1.000 |
| `EXTERNAL_STUDIES_MAX_PAGES` | 5 | 1–10 |

Los límites son defensas del servicio; no eliminarlos para ocultar un error de integración. Conservar estados parciales y errores comprensibles.

**Plazo efectivo:** el agregador calcula `min(sourceTimeout, globalTimeout)`, por lo que el valor predeterminado efectivo es **30 segundos**, aunque la variable global tenga 80 como valor inicial. Las siete fuentes se ejecutan en paralelo y no suman siete plazos. El máximo de resultados se aplica también al agregado final, que marca `partial` si queda truncado.

## 9. Configuración → Repositorio

Crear la solapa **Repositorio**, integrada con el centro de configuración existente. Lista las siete fuentes con endpoints editables y, cuando corresponde, usuario y contraseña.

| Endpoint | Función |
| --- | --- |
| `GET /api/admin/study-repositories` | Leer configuración pública y si hay credenciales configuradas. |
| `PUT /api/admin/study-repositories/{id}` | Actualizar una institución. |

Reutilizar los permisos `section.configuration.view` y `section.configuration.manage` y los servicios de ajustes/cifrado existentes.

- Persistir una entrada por fuente en `system_settings`, con clave `study-repository.<id>`.
- Guardar endpoints y usuario en el valor de configuración; la contraseña va en el campo cifrado de secretos.
- La lectura nunca devuelve la contraseña ni el cifrado al navegador.
- La respuesta pública usa `{ok, items}`; cada ítem incluye `id`, `name`, `requiresCredentials`, `username`, `hasPassword` y `endpoints: [{key, label, url}]`. Después de guardar se devuelve la lista actualizada.
- El contrato de actualización contiene `endpoints: [{key, url}]`, `username`, `passwordAction` (`keep`, `replace`, `remove`) y `password` sólo cuando corresponde reemplazarla.
- `remove` debe quitar efectivamente la credencial; no volver a utilizar silenciosamente la contraseña del entorno después de guardarlo.
- Si cambia el servidor de destino, no reenviar una contraseña conservada: exigir su reemplazo explícito para ese destino.
- Validar esquema, host, puerto y ausencia de usuario/contraseña embebidos en la URL. No ampliar las redirecciones ni destinos permitidos para sortear un fallo.
- Cada búsqueda toma una instantánea de la configuración vigente. Las siguientes consultas reciben los cambios sin reiniciar.
- Manejar correctamente el refresco de pantalla al terminar operaciones asíncronas, también en Angular sin Zone.js.

Los conectores y sus campos están predefinidos: esta actualización no implementa un generador de conectores arbitrarios ni una pantalla para agregar cualquier sitio nuevo.

### Configuración externa que no viaja en Git

Las credenciales iniciales pueden recibirse como `EXTERNAL_STUDIES_<FUENTE>_USERNAME` y `EXTERNAL_STUDIES_<FUENTE>_PASSWORD`, donde `<FUENTE>` es `FUESMEN`, `IDM`, `CENTRO`, `ESPANOL`, `MALARGUE` o `LABO`. Usar los nombres exactos del código.

Los endpoints configurables se derivan de `StudyRepositorySettings.fields(...)`. Respetar las claves de cada fuente: `BASE_URL`, `LOGIN_URL`, `SEARCH_URL`, `REPORT_URL`, `VIEWER_BASE_URL`, `SOCKET_URL` o `DOCUMENT_BASE_URL`, según corresponda. Patología usa el prefijo `EXTERNAL_STUDIES_PATHOLOGY_`, con `FALLBACK_URL` y `FILES_BASE_URL`.

| Fuente | Claves de endpoints |
| --- | --- |
| FUESMEN / IDM | `BASE_URL`, `LOGIN_URL`, `SEARCH_URL`, `REPORT_URL`, `VIEWER_BASE_URL` |
| Centro / Español / Malargüe | `BASE_URL`, `LOGIN_URL`, `SOCKET_URL`, `VIEWER_BASE_URL`, `DOCUMENT_BASE_URL` |
| Laboratorio | `BASE_URL`, `LOGIN_URL`, `SEARCH_URL`, `REPORT_URL` |
| Patología | `FALLBACK_URL`, `FILES_BASE_URL` |

Usar siempre el prefijo completo de la fuente al configurar variables de entorno. También existen `EXTERNAL_STUDIES_INFORMEMEDICO_BASE_URL` como respaldo general del portal y `EXTERNAL_STUDIES_LABO_SERVICE_CODE` (predeterminado `GIN`) para el servicio del laboratorio. Los endpoints de referencia están en `StudyRepositorySettings.fields(...)`; copiarlos desde ese código y comprobarlos para la institución receptora, sin deducirlos de un enlace individual de paciente.

Patología por JDBC admite `PATHOLOGY_DB_JDBC_URL`, `PATHOLOGY_DB_USERNAME` y `PATHOLOGY_DB_PASSWORD`; comprobar compatibilidad del motor y controlador. La referencia incluye PostgreSQL, no todos los motores posibles. La pantalla de su listado remoto no solicita credenciales.

JDBC requiere `registro_tumores_admisiones` con `dni`, `archivo` y `fecha_carga`, y conexión TLS verificada. Al configurarlo tiene prioridad absoluta: incluso si falla, no vuelve automáticamente al listado HTTP. Adaptar deliberadamente si la base receptora tiene otra estructura; no simular que hay un fallback que el código no implementa.

Conservar la clave de cifrado propia del destino. Inyectar las credenciales mediante su sistema de secretos o archivo de entorno privado referenciado por Compose. No incluir cuentas reales, contraseñas, cookies ni tokens de visores en la guía, el parche, los tests, la imagen o el commit.

## 10. Apertura de informes y corrección del 403

`GET /api/patients/{patientId}/external-studies/{sourceId}/{studyId}/{kind}`

El manejador local recupera el recurso solicitado después de comprobar sesión, permisos y pertenencia al paciente. Mantener la validación del `kind` y del identificador tal como la define el controlador de referencia.

`kind` admite `report` o `study`. Los informes de FUESMEN, IDM, Laboratorio, Centro, Español y Malargüe se exponen mediante rutas locales. Los visores de Informe Médico mantienen su enlace institucional; Patología mantiene sus enlaces externos. Al terminar una consulta o descarga se revalidan sesión, usuario, permiso, paciente activo y DNI, no sólo antes de iniciarla.

Para Centro, Español y Malargüe:

1. Volver a localizar el estudio solicitado en la sesión de su institución.
2. Comprobar DNI e identificador y resolver metadatos actuales.
3. Solicitar el PDF desde Java con `Referer` del **origen del visor oficial**, sin la clave del estudio y sin copiar cabeceras privadas del navegador de HCOP.
4. Validar origen de descarga, plazo, tamaño y firma de PDF.
5. Entregarlo con `Content-Type: application/pdf`, disposición en línea y `Cache-Control: no-store`.

El PDF permanece en memoria durante la respuesta: no requiere un archivo temporal persistente ni una copia en la historia clínica. Los enlaces para imágenes siguen llevando al visor institucional.

FUESMEN, IDM y Laboratorio también resuelven sus recursos mediante Java con la sesión correspondiente. Si el laboratorio devuelve HTML, conservar el aislamiento y la restricción de scripts/formularios implementados en el controlador; no inyectarlo en el DOM clínico.

No desactivar la validación TLS ni convertir el endpoint en un proxy de URLs arbitrarias. Conservar los controles de destinos, redirecciones y mensajes de error sin exposición de información sensible.

## 11. Diagnóstico estructurado y evolución

### Presentación

Mostrar el diagnóstico arriba de la historia, con tarjetas azules del estilo existente:

- AJCC: sitio y código disponibles.
- SNOMED CT: descripción y código.
- CIE-10: descripción y código.
- TNM/estadio: categorías, prefijo explícito y estadio almacenado.

Agregar fecha, topografía, histología y texto cuando existen. Mostrar los diagnósticos recientes primero. Adaptar las tarjetas al ancho del panel y preservar su presentación al imprimir.

### Compatibilidad de datos

La nueva proyección `clinicalDiagnosisEntries(state)` reúne:

1. `oncology.diagnosisRecords`, lista canónica usada al guardar.
2. El alias legado `oncology.diagnoses`.
3. Diagnósticos independientes de `state.diagnoses`.
4. Como respaldo cuando no existe la colección principal, los campos antiguos del objeto `oncology`.

Respetar registros eliminados/archivados, evitar duplicados por identificador y evitar repetir el reflejo legado del mismo diagnóstico. No descartar diagnósticos independientes importados.

- La proyección es de presentación: leer una ficha no la reescribe ni recalcula sus datos clínicos.
- No inventar un prefijo `c` o `p`, códigos, estadio, fecha ni día cuando la precisión guardada es sólo mes o año.
- Si hay prefijo explícito `c`, `p`, `yc`, `yp` o `r`, respetarlo al representar la categoría T.
- Al guardar el primer diagnóstico nuevo, materializar el diagnóstico antiguo si corresponde y conservar sus valores, auditoría, fecha y metadatos originales.
- No cambiar los algoritmos médicos de estadificación ni hacer una migración masiva de fichas por esta mejora visual.
- La impresión y la generación del texto para evolución deben usar la misma proyección.

### Acciones

**Agregar a evolución**, en cada diagnóstico:

1. Verificar paciente, permisos, ausencia de una operación pendiente y vigencia del registro seleccionado.
2. Abrir un borrador editable con diagnóstico, topografía, histología, clasificaciones, TNM, estadio y factores adicionales presentes.
3. Usar la fecha del borrador y el profesional de la sesión según el flujo existente.
4. Guardar sólo después de que el usuario confirme la evolución.

**Guardar y agregar a evolución**, en el formulario:

1. Guardar el diagnóstico por el servicio normal y esperar el resultado.
2. Liberar/cerrar ese borrador y abrir el borrador de evolución con lo efectivamente guardado.
3. Conservar **Guardar diagnóstico** como acción independiente.

No crear automáticamente una evolución ni duplicar el diagnóstico en la cronología al usar el guardado normal. Cancelar una evolución pendiente no deshace el diagnóstico que ya se guardó.

Invalidar el cálculo de estadio en curso al cambiar sitio, ejes o valor manual. Una respuesta antigua no puede sobrescribir una selección más reciente.

## 12. Mapa de archivos a trasladar

Las rutas son relativas al repositorio de referencia. Adaptarlas a la estructura del destino.

| Ruta o grupo | Responsabilidad |
| --- | --- |
| `frontend/src/app/core/clinical/study-image-presentation.ts` | Normalización y presentación de medios persistidos. |
| `frontend/src/app/core/clinical/clinical-diagnosis-projection.ts` | Diagnóstico canónico/legado, códigos, TNM y texto de evolución. |
| `frontend/src/app/core/clinical/clinical-print-projection.ts` | Integración del diagnóstico en la proyección de impresión. |
| `frontend/src/app/core/studies/external-study-search.*` | Cliente y normalización de búsqueda externa. |
| `frontend/src/app/features/studies/` | Lista, orden de archivos, galería, PDF, acciones y modelos. |
| `frontend/src/app/features/study-template-editor/` | Ciclo de carga del editor y envío de imágenes a evolución. |
| `frontend/src/app/features/clinical-entry/` | Modelos/normalizadores, adjuntos y modales de diagnóstico/evolución. |
| `frontend/src/app/features/clinical-workspace/` | Resumen de diagnóstico y coordinación de borradores. |
| `frontend/src/app/features/configuration/repositories/` | Pantalla, servicio y modelos de Repositorio. |
| `frontend/src/app/features/configuration/configuration-hub/` | Incorporación de la nueva solapa. |
| `src/main/java/ar/com/hexium/hcop/integration/studies/` | Conectores, HTTP/WebSocket, búsqueda, informes y ajustes. |
| `src/main/java/ar/com/hexium/hcop/patient/ExternalStudiesController.java` | Endpoints protegidos del paciente y entrega de recursos. |
| `src/main/java/ar/com/hexium/hcop/config/OpenApiConfiguration.java` | Documentación y clasificación de la API de estudios. |
| `src/test/java/ar/com/hexium/hcop/integration/studies/` | Proveedores ficticios y pruebas de protocolo/configuración. |
| `src/test/java/ar/com/hexium/hcop/patient/ExternalStudiesControllerTest.java` | Sesión, permisos, paciente y recursos. |
| `frontend/scripts/run-clinical-tests.mjs` | Registro de las nuevas suites. |
| `frontend/package.json`, `frontend/package-lock.json`, `frontend/angular.json` | PDF.js y recursos locales. |
| `docs/external-studies.md` | Contrato funcional y operativo del buscador. |
| `.gitignore` | Exclusión de frontend generado y artefactos locales. |

Trasladar también los `*.tests.ts` asociados a estos módulos. El parche es el inventario exacto; no copiar un directorio entero encima del destino sin revisar sus diferencias.

La referencia reutiliza estilos clínicos existentes y servicios que no cambian en este commit. Por eso aplicar sólo los archivos nuevos a un repositorio más antiguo puede no ser suficiente. Comprobar dependencias e imports y preservar las reglas de persistencia del receptor.

## 13. Verificación requerida en el destino

### Automatizada

En un entorno de pruebas, con dependencias del destino:

```sh
# Desde frontend
npm ci
npm run test:clinical
npm run build

# Desde la raíz, con Java 21 y Maven compatibles
mvn -B test
```

Si el proyecto utiliza el Dockerfile de referencia, éste ya ejecuta las pruebas frontend, el build Angular y las pruebas/empaquetado Java. Adaptar la forma de ejecución al entorno; no cambiar a ciegas la URL de una base real para hacer pasar tests.

### Recorrido de aceptación con datos ficticios

- [ ] Abrir una plantilla: imagen visible; dibujar, guardar, reabrir y comprobar las marcas.
- [ ] Crear dos ediciones de la misma imagen; conservar el original, las dos versiones y un único estudio.
- [ ] Adjuntar una versión a evolución; crear otra edición y confirmar que la evolución anterior no cambia.
- [ ] Cambiar rápidamente de plantilla/paciente durante una carga: ninguna respuesta anterior pisa la actual.
- [ ] Simular fallo del guardado clínico después de subir una imagen: el reintento no vuelve a subirla ni duplica versiones.
- [ ] Subir imagen, PDF A y PDF B: aparecen en ese orden debajo de la lista y lo conservan al recargar.
- [ ] La tabla crece con sus filas y comparte desplazamiento con los archivos; mantiene el margen izquierdo.
- [ ] PDF de varias páginas y tamaños mixtos: alto completo, proporciones correctas, primera/última página visibles al recorrerlo.
- [ ] Ampliar, cambiar zoom, editar dos veces una página y seleccionar original/copia.
- [ ] Agregar una página original y una anotada a evolución; conservar la versión elegida tras recargar.
- [ ] Simular siete fuentes: una con error, otra vacía y otras con resultados. Mostrar los resultados restantes, sus fuentes y el estado parcial.
- [ ] Simular un estudio sin informe o con fallo de metadatos: mantener su visor.
- [ ] Cambiar el paciente durante la búsqueda: no mostrar ni abrir datos de la ficha anterior.
- [ ] Probar las tres sesiones de Informe Médico por separado, paginación y filtrado de DNI por fila.
- [ ] Simular PDF que responde 403 sin referencia: el manejador local lo recupera con el origen del visor.
- [ ] Rechazar acceso sin sesión, sin permiso, con otro paciente activo o con estudio no perteneciente a ese paciente.
- [ ] Rechazar recurso de destino no permitido, respuesta excesiva y un HTML presentado como PDF.
- [ ] Repositorio permite guardar y recargar endpoints/usuario; nunca muestra la contraseña existente.
- [ ] Cambiar, mantener y quitar contraseña; comprobar que cada acción modifica la consulta siguiente como corresponde.
- [ ] Cambiar autoridad del endpoint con contraseña conservada: exigir reingreso de credencial para el nuevo destino.
- [ ] Abrir ficha antigua con diagnóstico sólo en `oncology`: tarjetas con valores originales, sin inventar datos faltantes.
- [ ] Guardar otro diagnóstico: conservar el anterior y su auditoría; no duplicarlo al volver a guardar/recargar.
- [ ] Comprobar registros canónicos, alias/importados y marcas de eliminado/archivado.
- [ ] Abrir evolución desde un diagnóstico, editarla y cancelar: proteger el borrador y no guardar una evolución por cancelar.
- [ ] Usar Guardar y agregar a evolución, editar y confirmar: persistir exactamente una alta de diagnóstico y una evolución.
- [ ] Una respuesta de cálculo atrasada no reemplaza un estadio cambiado manualmente.
- [ ] Pantalla e impresión muestran diagnóstico coherente, en ancho completo y compartido con Estudios.
- [ ] Comparar las secciones y registros previos antes/después: sólo cambian los elementos que se guardaron deliberadamente.
- [ ] Navegador sin errores nuevos de consola ni fallos de carga del worker PDF o sus fuentes.

La versión de referencia aprobó 436 pruebas Java en la última modificación backend y las suites clínicas/build Angular después del cambio final de diagnóstico. Entre las suites finales hubo 53 comprobaciones del proyector de diagnóstico, 34 de impresión y 56 de normalizadores. Estos son resultados históricos del origen: GPT debe ejecutar y reportar las pruebas del destino, sin presentarlos como evidencia de su propio trabajo.

El build Angular del origen tenía una advertencia conocida de tamaño del bundle inicial, por debajo de su límite de error. Revisar el presupuesto del destino; no ocultar advertencias ni modificar el límite sólo para declarar un build exitoso.

**Detalle de documentación a revisar al portar:** el cambio OpenAPI del origen incluye la búsqueda en el grupo clínico, pero no completa el patrón y el mapa descriptivo de permisos para todos los subrecursos y Repositorio. La autorización real sí está implementada en los controladores. Si el destino publica documentación OpenAPI, completar y verificar esa cobertura; no confundir documentación incompleta con ausencia de autorización funcional.

## 14. Datos, compilación y despliegue

Este diff no agrega migraciones SQL. Usa el documento clínico JSON y la tabla de ajustes existentes. Si el destino carece de esas estructuras, resolver esa diferencia antes del despliegue; no asumir que los esquemas son idénticos.

1. Comprobar puertos, nombre del proyecto Compose, volumen de PostgreSQL, almacenamiento de archivos y claves de cifrado del destino.
2. Antes de desplegar sobre una instalación con datos, generar y verificar su respaldo consistente de base y archivos. Mantener una imagen/versionado que permita volver a la versión previa.
3. Compilar desde fuentes. `src/main/resources/static/app/` es generado; el Dockerfile lo obtiene del build Angular. No copiar bundles antiguos ni el JAR de la máquina de referencia.
4. Inyectar las credenciales propias del destino, si se van a validar servicios reales. La configuración privada de esta instalación no está en el commit.
5. Actualizar la aplicación conservando volúmenes y secretos. No usar `docker compose down -v`, borrar volúmenes ni recrear la base como parte de esta actualización.
6. Verificar salud, acceso HTTP, bundle nuevo, inicio de sesión y lectura de una ficha autorizada. Las pruebas de escritura deben haberse hecho con datos ficticios en un entorno separado.
7. Informar el resultado del despliegue por separado del resultado del build. Si no hay autorización o acceso para desplegar, entregar la versión probada y las instrucciones concretas pendientes.

`external-studies.env` se conectó al Compose de la instalación original mediante un cambio privado, **no mediante este commit**. Por lo tanto, copiar el código o hacer cherry-pick no configura automáticamente las credenciales del otro servidor: ajustar su Compose/sistema de secretos explícitamente, sin sobrescribir el archivo de entorno ya existente.

No se trasladan como parte del software: la reparación del arranque de Docker de una PC, su limpieza de contenedores/imágenes, los nombres locales de sus volúmenes, la copia puntual de una ficha, sus respaldos ni sus credenciales. Fueron operaciones de esa instalación y no deben reproducirse para actualizar otro repositorio.

## 15. Entrega que debe producir GPT

Al terminar, informar:

1. Base detectada y método usado: commit/parche o adaptación por módulos.
2. Cambios funcionales y principales archivos modificados.
3. Pruebas efectivamente ejecutadas, resultado y verificación visual realizada.
4. Compatibilidad de fichas antiguas, originales, versiones y adjuntos.
5. Variables/endpoints que requieren configuración del operador, sin exponer valores secretos.
6. Estado de build y despliegue, respaldo y procedimiento de vuelta a la versión anterior cuando aplique.
7. Diferencias deliberadas respecto de esta guía, funcionalidades pendientes y fallos externos observados.

No declarar la actualización completa si sólo se reprodujo la apariencia, si falta persistencia o si los tests apuntan accidentalmente a servicios clínicos reales.
