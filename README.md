# HCOP JP

> **Rama de migración:** `codex/angular-hexagonal-migration` es un canal de
> prueba aislado. Conserva la interfaz clínica vigente mientras traslada el
> backend por capacidades a una arquitectura hexagonal y estabiliza los
> contratos que consumirá Angular. La versión estable continúa en `main`, con
> la imagen `latest`, el puerto 5180 y sus volúmenes habituales.

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

## Probar esta rama migratoria desde GitHub

Con Docker Desktop iniciado, copie esta línea completa en Windows PowerShell:

```powershell
$hcopScript = Join-Path $env:TEMP "EJECUTAR-DOCKER-DESDE-GITHUB.ps1"; Invoke-WebRequest "https://raw.githubusercontent.com/Marcolyto/HCOP_JP/codex/angular-hexagonal-migration/EJECUTAR-DOCKER-DESDE-GITHUB.ps1" -OutFile $hcopScript; powershell.exe -NoProfile -ExecutionPolicy Bypass -File $hcopScript -Channel Migration
```

La prueba abre <http://localhost:5181> y utiliza la imagen
`ghcr.io/marcolyto/hcop_jp:angular-hexagonal-migration`, una base
`hcop_ajp` y volúmenes `hcop_ajp_*`. Puede convivir con la versión estable:
no modifica su imagen `latest`, su puerto 5180 ni sus datos.

La guía [Probar la rama Angular/hexagonal](docs/00-inicio/PRUEBA-RAMA-ANGULAR-HEXAGONAL.md)
incluye inicio, actualización, estado, detención y límites actuales.

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
persistentes. El repositorio y la imagen Docker publicados son públicos, por lo
que esta modalidad no requiere autenticación en GitHub.

Modos disponibles:

```powershell
# Usa las imágenes locales y sólo descarga si todavía faltan
powershell.exe -File .\EJECUTAR-DOCKER-DESDE-GITHUB.ps1 -Mode Start

# Busca y aplica explícitamente la versión latest
powershell.exe -File .\EJECUTAR-DOCKER-DESDE-GITHUB.ps1 -Mode Update
powershell.exe -File .\EJECUTAR-DOCKER-DESDE-GITHUB.ps1 -Mode Status
powershell.exe -File .\EJECUTAR-DOCKER-DESDE-GITHUB.ps1 -Mode Stop
```

## Instalación administrada desde GitHub

Requisitos: Windows 10/11 de 64 bits, conexión a Internet y permisos para
instalar Docker Desktop.

1. Descargue [`INSTALAR-DESDE-GITHUB.bat`](INSTALAR-DESDE-GITHUB.bat).
2. Haga doble clic.
3. Acepte la instalación de Docker Desktop si Windows la solicita.
4. Elija usuario, contraseña y puerto o presione Enter para usar los valores
   sugeridos.
5. El instalador abre `http://localhost:5180`.

El repositorio y la imagen Docker publicados son públicos. La instalación no
requiere iniciar sesión en GitHub, GitHub CLI ni tokens personales.

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
elegida durante la instalación y debe tener al menos 10 caracteres.

## Arquitectura

HCOP JP evoluciona como monolito modular hexagonal, con migración incremental:

- cada capacidad migrada separa `domain`, `application` e `infrastructure`;
- los puertos de entrada expresan casos de uso y los de salida aíslan
  persistencia, archivos y catálogos;
- los módulos todavía no migrados conservan su estructura MVC mientras se
  verifica la paridad;
- `static` mantiene temporalmente la interfaz vigente en `/`;
- Angular ya entrega login y paciente activo en `/app/` y se ampliará por
  recorridos completos, sin una reescritura masiva;
- `db/migration` conserva la evolución reproducible de PostgreSQL mediante
  Flyway.

ArchUnit impide que dominio y aplicación dependan de Spring, JDBC, JSON o los
adaptadores. La migración de Configuración, Protocolos y Guías ya usa estos
límites; las demás capacidades siguen en convivencia controlada.

Cada cambio de estructura usa Flyway. Las reglas de concurrencia críticas,
incluida la superposición de turnos, también están protegidas por PostgreSQL.

## Documentación

Empiece por el [índice de documentación](docs/README.md).

- [Instalar desde GitHub](docs/00-inicio/INSTALACION-DESDE-GITHUB.md)
- [Probar la rama Angular/hexagonal](docs/00-inicio/PRUEBA-RAMA-ANGULAR-HEXAGONAL.md)
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
- [Dónde está cada archivo del frontend, backend y documentación](docs/04-desarrollo/ESTRUCTURA-DEL-REPOSITORIO.md)
- [Recrear todo con buenas prácticas](docs/08-recrear-desde-cero/README.md)
- [Checklist de producto final](docs/08-recrear-desde-cero/10-CHECKLIST-PRODUCTO-FINAL.md)
- [Docker](docs/05-operacion/DOCKER.md)
- [Copias de seguridad](docs/05-operacion/BACKUP-Y-RESTAURACION.md)
- [Seguridad](docs/05-operacion/SEGURIDAD.md)
- [Programa de migración Angular y hexagonal](docs/09-migracion-angular-hexagonal/README.md)

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
La verificación de publicación ejecuta la suite Java, reglas ArchUnit,
contratos reales de Configuración, Protocolos y Guías, OpenAPI, enlaces de
documentación y el E2E clínico multidroga. Carboplatino se interrumpe al 50 %,
se reanuda y la aplicación termina completada sin perder la reacción.

En este corte, `mvn verify` aprobó **142 pruebas** sin fallas ni omisiones. La
prueba Docker confirmó además los 111 endpoints documentados y el circuito
clínico completo con cuatro drogas.
