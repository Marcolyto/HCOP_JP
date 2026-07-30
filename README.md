# HCOP JP

HCOP JP reúne en un único sistema la historia clínica oncológica, diagnósticos,
prescripciones, protocolos, farmacia, Hospital de Día, turnero por sillón,
estudios, investigación, herramientas, usuarios y auditoría.

Hospital de Día opera cada aplicación real —ciclo y día— mediante un circuito
único: prescripción, validación y disponibilidad de medicación, turno, triaje,
preparación trazable con TTL, administración con doble control y cierre. Cada
rol trabaja en su propia cola sin poder adelantar estados.

La interfaz conserva el producto HCOP/Lira construido hasta ahora. El servidor
fue migrado a Java 21 con Spring MVC y la persistencia a PostgreSQL. No necesita
Lira, Node.js ni MySQL para funcionar.

## Ejecutar directamente con Docker Desktop, sin clonar

Si Docker Desktop ya está instalado y abierto, descargue únicamente
[`EJECUTAR-DOCKER-DESDE-GITHUB.ps1`](EJECUTAR-DOCKER-DESDE-GITHUB.ps1) y
ejecútelo. No necesita clonar el repositorio, Java ni Maven.

Esta línea descarga primero el archivo a la carpeta temporal y recién después
lo ejecuta; no canaliza código de Internet directamente al intérprete:

```powershell
$hcopScript = Join-Path $env:TEMP "EJECUTAR-DOCKER-DESDE-GITHUB.ps1"; Invoke-WebRequest "https://raw.githubusercontent.com/Marcolyto/HCOP_JP/main/EJECUTAR-DOCKER-DESDE-GITHUB.ps1" -OutFile $hcopScript; powershell.exe -NoProfile -ExecutionPolicy Bypass -File $hcopScript
```

En la primera ejecución solicita las credenciales iniciales sin mostrarlas,
genera los demás secretos una sola vez y guarda todo en
`%LOCALAPPDATA%\HCOP_JP-Docker`. Los datos clínicos quedan en volúmenes Docker
persistentes. Si el repositorio todavía es privado, descargue el script desde
GitHub después de iniciar sesión; el propio ejecutor resuelve por separado el
acceso a la imagen privada.

Modos disponibles:

```powershell
# Usa las imágenes locales y sólo descarga si todavía faltan
powershell.exe -File .\EJECUTAR-DOCKER-DESDE-GITHUB.ps1 -Mode Start

# Busca y aplica explícitamente la versión latest
powershell.exe -File .\EJECUTAR-DOCKER-DESDE-GITHUB.ps1 -Mode Update
powershell.exe -File .\EJECUTAR-DOCKER-DESDE-GITHUB.ps1 -Mode Status
powershell.exe -File .\EJECUTAR-DOCKER-DESDE-GITHUB.ps1 -Mode Stop
```

## Instalación más simple desde GitHub

Requisitos: Windows 10/11 de 64 bits, conexión a Internet y permisos para
instalar Docker Desktop.

1. Inicie sesión en GitHub con una cuenta autorizada y descargue
   [`INSTALAR-DESDE-GITHUB.bat`](INSTALAR-DESDE-GITHUB.bat).
2. Haga doble clic.
3. Acepte la instalación de Docker Desktop si Windows la solicita.
4. Elija usuario, contraseña y puerto o presione Enter para usar los valores
   sugeridos.
5. El instalador abre `http://localhost:5180`.

Como el repositorio y la imagen son privados, el asistente puede instalar
GitHub CLI y abrir una autorización por navegador en el primer equipo. No pide
que copie ni pegue tokens.

El acceso directo **HCOP JP** del Escritorio sirve como lanzador diario:
comprueba Docker, descarga la versión más reciente publicada en GitHub, mantiene
la base de datos y abre el sistema.

> Los pacientes y archivos clínicos nunca están en GitHub ni dentro de la
> imagen. Se conservan en volúmenes Docker locales.

## Ejecutar desde GitHub si ya tiene Docker

Use `EJECUTAR-DOCKER-DESDE-GITHUB.ps1`. El lanzador solicita la contraseña
inicial, genera los demás secretos, descarga las imágenes publicadas y conserva
los datos en volúmenes Docker locales. El proyecto no incluye una contraseña
predeterminada.

Luego abra:

- Aplicación: <http://localhost:5180>
- Swagger: <http://localhost:5180/swagger-ui.html>
- Salud: <http://localhost:5180/actuator/health>

El usuario sugerido es `marcolyto`; la contraseña inicial siempre debe ser
elegida durante la instalación.

## Arquitectura

HCOP JP es un monolito modular con separación MVC:

- `controller`: contrato HTTP y autorización;
- `service`: reglas clínicas, validaciones y transacciones;
- `repository`: consultas parametrizadas a PostgreSQL;
- `static`: interfaz web existente;
- `db/migration`: creación y evolución reproducible de la base.

Cada cambio de estructura usa Flyway. Las reglas de concurrencia críticas,
incluida la superposición de turnos, también están protegidas por PostgreSQL.

## Documentación

Empiece por el [índice de documentación](docs/README.md).

- [Instalar desde GitHub](docs/00-inicio/INSTALACION-DESDE-GITHUB.md)
- [Manual de uso](docs/01-uso/MANUAL-DE-USO.md)
- [Flujo clínico](docs/01-uso/FLUJO-TRATAMIENTO.md)
- [Circuito de Hospital de día en 7 pasos](docs/01-uso/CIRCUITO-HOSPITAL-DE-DIA-7-PASOS.md)
- [Guía operativa por roles](docs/01-uso/GUIA-POR-ROLES-HOSPITAL-DE-DIA.md)
- [Video detallado del circuito, paso a paso](src/main/resources/static/help/media/circuito-hospital-dia-paso-a-paso.mp4)
- [Guía de capítulos, alternativas y diagrama del video](docs/01-uso/VIDEO-CIRCUITO-HOSPITAL-DIA-PASO-A-PASO.md)
- [Video resumen de 70 segundos](docs/media/demo-flujo-7-pasos/flujo-oncologico-7-pasos.mp4)
- [Arquitectura MVC](docs/02-arquitectura/MVC.md)
- [Swagger y API](docs/02-arquitectura/SWAGGER-OPENAPI.md)
- [Todos los endpoints](docs/02-arquitectura/ENDPOINTS.md)
- [Modelo de datos](docs/03-base-de-datos/MODELO-DE-DATOS.md)
- [Diccionario de datos](docs/03-base-de-datos/DICCIONARIO-DE-DATOS.md)
- [Mapa pantalla → API → base](docs/07-referencia/MAPA-FUNCIONAL.md)
- [Variables de entorno](docs/05-operacion/VARIABLES-DE-ENTORNO.md)
- [Crear el proyecto desde cero](docs/04-desarrollo/CREAR-DESDE-CERO.md)
- [Recrear todo con buenas prácticas](docs/08-recrear-desde-cero/README.md)
- [Checklist de producto final](docs/08-recrear-desde-cero/10-CHECKLIST-PRODUCTO-FINAL.md)
- [Docker](docs/05-operacion/DOCKER.md)
- [Copias de seguridad](docs/05-operacion/BACKUP-Y-RESTAURACION.md)
- [Seguridad](docs/05-operacion/SEGURIDAD.md)

## Verificación

GitHub Actions compila Java, ejecuta pruebas y levanta el producto completo con
Docker y PostgreSQL. En una instalación local:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\integration-test.ps1
```

La prueba integral recorre login, paciente, diagnóstico, tratamiento
multidroga, Farmacia, reserva, turno, triaje, preparación, QR, interrupción,
reanudación, administración, hoja de tratamiento y evoluciones. Además existe
una [matriz reproducible de Hospital de día](docs/08-auditoria/HOSPITAL-DIA-100-CASOS.md);
su última ejecución obtuvo
[100 PASS de 100](docs/08-auditoria/resultados/hospital-dia-100-casos-20260730-100711.md).
La verificación final incluyó además **101 pruebas Java aprobadas** y el E2E
multidroga con cuatro componentes: Carboplatino se interrumpió al 50 %, se
reanudó y la aplicación terminó completada sin perder la reacción.
