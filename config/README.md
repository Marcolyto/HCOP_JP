# Repositorios de estudios

`study-repositories.env` contiene las seis cuentas de acceso de FUESMEN, IDM, Laboratorio Schestakow, Centro del Diagnóstico, Hospital Español y Malargüe. Sus credenciales están incluidas por solicitud del propietario y son visibles en este repositorio público. Patología utiliza el listado público y no requiere credenciales.

Ambos archivos Compose cargan esta configuración en el backend Java. Los endpoints iniciales se definen en `StudyRepositorySettings.java` y se pueden editar junto con las cuentas en **Configuración → Repositorio**. La configuración guardada allí tiene prioridad sobre las variables de este archivo.

Para utilizar otras cuentas sin modificar el archivo distribuido, configure `HCOP_STUDY_REPOSITORIES_ENV_FILE` en el `.env` de la instalación con la ruta absoluta a un archivo propio que contenga las variables `EXTERNAL_STUDIES_<FUENTE>_USERNAME` y `EXTERNAL_STUDIES_<FUENTE>_PASSWORD` para cada fuente. Reinicie la aplicación después de cambiar el archivo de entorno. Si ya guardó una cuenta en la pantalla de configuración, actualícela desde esa pantalla.

Este archivo no contiene claves de HCOP, PostgreSQL, cifrado ni QR. Tampoco contiene datos de pacientes. Se excluye del contexto de construcción de Docker: las credenciales se entregan al contenedor al iniciarlo y no se incorporan a la imagen.
