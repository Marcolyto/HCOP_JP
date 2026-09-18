# Búsqueda de estudios externos

El buscador forma parte del backend Java de HCOP. No requiere PHP ni un servicio
adicional. Consulta por el DNI registrado del paciente activo y entrega datos
JSON para que Angular los dibuje con los componentes de HCOP.

`GET /api/patients/{patientId}/external-studies`

Requiere sesión y permiso `section.studies.view`. El servidor comprueba que el
paciente siga activo al finalizar. La respuesta no se almacena en cachés HTTP ni
modifica la historia clínica. No acepta un DNI arbitrario enviado por el cliente.

La respuesta incluye `patientId`, `searchedAt`, `studies`, `sources`, `partial` y
`total`. Cada estudio tiene identificador estable, fecha, tipo, descripción,
institución y enlaces `reportUrl` / `studyUrl`. Cada fuente indica `ok`, `empty` o
`error`, el número de resultados y, cuando corresponde, un mensaje de estado.

Se consultan FUESMEN, IDM, Centro del Diagnóstico, Hospital Español, Malargüe,
Laboratorio Schestakow y Patología. Las fuentes se ejecutan de forma independiente: un fallo
de autenticación, red, formato o tiempo de espera no descarta los demás resultados.
Los datos HTML de los proveedores nunca se insertan directamente en el frontend.

## Configuración

Las credenciales se inyectan exclusivamente en el servidor mediante variables
de entorno, o se guardan desde **Configuración → Repositorio**. No deben incluirse en imágenes Docker, código ni respuestas JSON:

- `EXTERNAL_STUDIES_FUESMEN_USERNAME` / `EXTERNAL_STUDIES_FUESMEN_PASSWORD`
- `EXTERNAL_STUDIES_IDM_USERNAME` / `EXTERNAL_STUDIES_IDM_PASSWORD`
- `EXTERNAL_STUDIES_LABO_USERNAME` / `EXTERNAL_STUDIES_LABO_PASSWORD`
- `EXTERNAL_STUDIES_ESPANOL_USERNAME` / `EXTERNAL_STUDIES_ESPANOL_PASSWORD`
- `EXTERNAL_STUDIES_MALARGUE_USERNAME` / `EXTERNAL_STUDIES_MALARGUE_PASSWORD`
- `EXTERNAL_STUDIES_CENTRO_USERNAME` / `EXTERNAL_STUDIES_CENTRO_PASSWORD`

En la instalación local se usa `external-studies.env`, fuera del repositorio,
referenciado desde el servicio `application` de Compose. No publicar ese archivo.

La solapa Repositorio lista los siete conectores y permite editar sus endpoints,
usuario y contraseña. Las contraseñas se cifran con el mecanismo de secretos del
servidor; el navegador sólo recibe si existe una contraseña. Al guardar se puede
conservar, reemplazar o quitar. Quitar una contraseña impide volver a usar la del
entorno automáticamente. Los cambios se aplican a las consultas siguientes, sin
reiniciar Docker. Se requiere `section.configuration.view` para consultar y
`section.configuration.manage` para guardar. Cambiar el servidor de destino
requiere introducir de nuevo la contraseña correspondiente.

`GET /api/admin/study-repositories` devuelve los conectores. Cada actualización
usa `PUT /api/admin/study-repositories/{id}`. La configuración se conserva en
`system_settings`, con el secreto en su columna cifrada y los endpoints separados.

Los informes de FUESMEN, IDM y Laboratorio se resuelven mediante
`GET /api/patients/{patientId}/external-studies/{sourceId}/{studyId}/{kind}`.
El manejador Java vuelve a comprobar que el estudio pertenezca al DNI del paciente,
inicia una sesión institucional y devuelve el PDF o el enlace directo del visor.
No utiliza el lanzador de la IP anterior ni `ver_lab_curl.php`. Un eventual informe
HTML del laboratorio se sirve aislado, con scripts y formularios deshabilitados.
Las respuestas son privadas, sin caché, y las descargas tienen un límite de tamaño.

Las tres cuentas de Informe Médico usan el portal de profesionales. Cada consulta
inicia su propia sesión, completa el control CSRF y utiliza el canal de consulta
del portal para filtrar por DNI. El canal se inicia con la dirección final que
devuelve el acceso, incluida la redirección a `/sitios`. El servidor vuelve a comprobar el documento de
cada resultado; las cookies y los códigos de sesión no se envían a Angular.
Los resultados de las tres instituciones se ordenan por fecha junto con los de
las demás fuentes, conservando identificadores separados por institución.

Informe Médico tiene dos generaciones de visor. HCOP obtiene el enlace de la
fila seleccionada mediante la acción que publica el portal y conserva su clave
de acceso completa. Para el informe consulta la configuración del visor y los
metadatos del estudio; sólo presenta el enlace PDF cuando el proveedor declara
un documento de ese tipo. No descarga ni genera informes durante la búsqueda.
Un error al consultar esos metadatos conserva el enlace del estudio y se informa
como resultado parcial. La clave del enlace puede caducar según el proveedor;
el botón de actualización solicita enlaces nuevos.

Los informes de Centro, Español y Malargüe se abren mediante el manejador Java
autenticado de HCOP. Al hacer clic, vuelve a consultar el DNI y el identificador
del estudio, obtiene sus metadatos actuales y recupera el PDF con la referencia
del visor oficial. Centro devuelve `403 Forbidden` si falta esa referencia.
Sólo se envía el origen del visor, sin la clave del estudio ni cabeceras del
navegador de HCOP. El documento se mantiene en memoria y se entrega como PDF
en línea, con `Cache-Control: no-store`; no se crea una copia permanente. Se
validan el origen de descarga, el tamaño, el plazo y la firma PDF. Los enlaces
para abrir las imágenes en el visor institucional conservan su funcionamiento.

Las pruebas pueden sustituir `EXTERNAL_STUDIES_<FUENTE>_BASE_URL`,
`VIEWER_BASE_URL` y `DOCUMENT_BASE_URL` con servidores locales. La configuración
del visor se conserva sólo durante esa consulta, y las peticiones de documentos
se limitan a las autoridades conocidas de Informe Médico o a la autoridad
configurada expresamente. No se debe apuntar la instalación real a los fixtures.

## Pantalla de Estudios

Primero se presenta la lista de estudios externos y estudios clínicos sin archivo, con
fecha, descripción, tipo, institución y acciones para consultar informe o estudio.
El botón de actualización permite volver a consultar las fuentes. Los resultados
de una búsqueda anterior se descartan al cambiar de paciente.

Debajo se apilan todos los archivos subidos, incluidos PDF, imágenes, plantillas
y documentos, por fecha de carga original. Los nuevos se agregan al final;
editar un archivo conserva su posición. Las imágenes mantienen sus versiones,
edición y opción de agregar a evolución. Los PDF muestran todas sus páginas
con altura natural, ajustadas al ancho del panel, mediante PDF.js servido
localmente. No se utiliza un visor con desplazamiento vertical independiente.
Un fallo externo no oculta esos archivos ni impide trabajar con los datos locales.
Cada página del PDF permite ampliar con zoom, editar con las herramientas de
anotación y agregar la página seleccionada a una evolución. Las copias anotadas
se guardan como versiones de esa página, conservando el PDF original y el orden
del estudio. La evolución conserva la versión elegida en el momento de adjuntarla.
El listado ocupa la altura necesaria para todas sus filas y comparte el
desplazamiento vertical del panel con las imágenes situadas debajo.

## Validación de la integración

Las pruebas usan fuentes locales y datos ficticios para comprobar autenticación,
paginación, aislamiento de errores, límites de espera y protección del paciente
activo. La consulta real de verificación devolvió resultados de FUESMEN, IDM y
Laboratorio. La conexión anterior a Centro y Español se reemplazó por el portal
autenticado de Informe Médico y se incorporó la cuenta de Malargüe.

Por excepción expresamente acordada, Patología utiliza el listado remoto existente, consultando solamente
esa institución. Es una fuente externa consumida desde Java; no se instala ni
se crea un servidor PHP en HCOP. También admite una conexión JDBC configurada en
el servidor mediante `PATHOLOGY_DB_JDBC_URL`, `PATHOLOGY_DB_USERNAME` y
`PATHOLOGY_DB_PASSWORD`; el controlador incluido es PostgreSQL.
