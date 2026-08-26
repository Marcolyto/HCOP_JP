# HCOP JP

HCOP JP reúne en un único sistema la historia clínica oncológica, diagnósticos,
prescripciones, protocolos, farmacia, Hospital de Día, turnero por sillón,
estudios, investigación, herramientas, usuarios y auditoría.

Hospital de Día opera cada aplicación real —ciclo y día— mediante un circuito
único: prescripción, validación y disponibilidad de medicación, turno, triaje,
preparación trazable con TTL, administración con doble control y cierre. Cada
rol trabaja en su propia cola sin poder adelantar estados.

La interfaz es Angular nativo (nada de `static/app.js` ni iframe), servida
por su propio contenedor nginx. Detrás hay tres servicios Docker
independientes:

```text
frontend  Angular + nginx    único punto público (puerto 5180)
   ↓ /api/, /actuator, /swagger-ui
bff       Java 21 + Redis    Token Handler de la sesión (JWT ↔ cookie)
   ↓ Authorization: Bearer
backend   Java 21 + Postgres dominio clínico completo, arquitectura hexagonal
```

El navegador nunca ve un JWT ni habla directo con `backend`: el `bff` guarda
el access/refresh token en Redis y le da al navegador una cookie de sesión
opaca. Ver [Arquitectura](#arquitectura) más abajo. No necesita Lira ni
MySQL para funcionar; Node.js sólo hace falta para compilar el frontend, no
en runtime (las imágenes publicadas ya vienen compiladas).

## Ejecutar directamente con Docker Desktop, sin clonar

Si Docker Desktop ya está instalado y abierto, descargue únicamente
[`EJECUTAR-DOCKER-DESDE-GITHUB.ps1`](EJECUTAR-DOCKER-DESDE-GITHUB.ps1) y
ejecútelo. No necesita clonar el repositorio, Java ni Maven.

Esta línea descarga primero el archivo a la carpeta temporal y recién después
lo ejecuta; no canaliza código de Internet directamente al intérprete:

```powershell
$hcopScript = Join-Path $env:TEMP "EJECUTAR-DOCKER-DESDE-GITHUB.ps1"; Invoke-WebRequest -UseBasicParsing "https://raw.githubusercontent.com/Marcolyto/HCOP_JP/main/EJECUTAR-DOCKER-DESDE-GITHUB.ps1" -OutFile $hcopScript; powershell.exe -NoProfile -ExecutionPolicy Bypass -File $hcopScript
```

En la primera ejecución solicita puerto, usuario administrador y contraseña;
la contraseña no se muestra. Genera los demás secretos una sola vez y guarda todo en
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
4. Elija puerto, usuario y contraseña o presione Enter para usar los valores
   sugeridos.
5. El instalador abre el frontend Angular en
   `http://localhost:<puerto-elegido>/`.

El repositorio y la imagen Docker publicados son públicos. La instalación no
requiere iniciar sesión en GitHub, GitHub CLI ni tokens personales.

El acceso directo **HCOP JP** del Escritorio sirve como lanzador diario:
comprueba Docker, descarga la versión más reciente publicada en GitHub, mantiene
la base de datos y abre el sistema.

> Los pacientes y archivos clínicos nunca están en GitHub ni dentro de la
> imagen. Se conservan en volúmenes Docker locales.

## Ejecutar desde GitHub si ya tiene Docker

Use `EJECUTAR-DOCKER-DESDE-GITHUB.ps1`. En el primer inicio el lanzador solicita
puerto, usuario administrador y contraseña, genera los demás secretos, descarga
las imágenes publicadas y conserva los datos en volúmenes Docker locales. El
proyecto no incluye una contraseña predeterminada.

Luego abra:

- Aplicación Angular: `http://localhost:<puerto-elegido>/`
- Swagger: `http://localhost:<puerto-elegido>/swagger-ui.html`
- Salud: `http://localhost:<puerto-elegido>/actuator/health`

El usuario sugerido es `marcolyto`; la contraseña inicial siempre debe ser
elegida durante la instalación y debe tener al menos 10 caracteres.

## Arquitectura

Tres servicios Docker independientes, no un monolito:

- **`backend/`** — Java 21, Spring MVC, PostgreSQL. Los ~14 módulos
  clínicos son hexagonales: cada uno separa `domain` (sin dependencia a
  frameworks), `application` (`port/in`/`port/out`, casos de uso) e
  `infrastructure` (`web`/`persistence`/config). Habla JWT — nunca cookies,
  nunca lo toca el navegador directo. `auth` y `platform` son
  infraestructura transversal permanentemente exenta de la regla
  hexagonal.
- **`bff/`** — Java 21, Redis. Token Handler de la sesión: guarda el
  access/refresh token del backend en Redis, le da al navegador una cookie
  de sesión opaca (`BFF_SESSION`) y reenvía `Authorization: Bearer` en cada
  proxy hacia el backend. Sin base de datos propia.
- **`frontend/`** — Angular, servido por nginx. Único punto público
  (puerto 5180); enruta `/api/`, `/actuator/health`, `/v3/api-docs`,
  `/swagger-ui*` hacia el `bff`.

ArchUnit impide que `domain`/`application` dependan de Spring, JDBC, Jackson
o los adaptadores — regla incondicional, sin excepciones por módulo — y que
los módulos clínicos tengan ciclos entre sí. `db/migration` (dentro de
`backend/`) conserva la evolución reproducible de PostgreSQL mediante
Flyway. Las reglas de concurrencia críticas, incluida la superposición de
turnos, están protegidas por PostgreSQL, no sólo por la UI.

## Documentación

Empiece por el [índice de documentación](docs/README.md).

- [Instalar desde GitHub](docs/00-inicio/INSTALACION-DESDE-GITHUB.md)
- [Probar la rama Angular/hexagonal](docs/00-inicio/PRUEBA-RAMA-ANGULAR-HEXAGONAL.md)
- [Manual de uso](docs/01-uso/MANUAL-DE-USO.md)
- [Flujo clínico](docs/01-uso/FLUJO-TRATAMIENTO.md)
- [Circuito de Hospital de día en 7 pasos](docs/01-uso/CIRCUITO-HOSPITAL-DE-DIA-7-PASOS.md)
- [Guía operativa por roles](docs/01-uso/GUIA-POR-ROLES-HOSPITAL-DE-DIA.md)
- [Video detallado del circuito, paso a paso](frontend/public/help/media/circuito-hospital-dia-paso-a-paso.mp4)
- [Guía de capítulos, alternativas y diagrama del video](docs/01-uso/VIDEO-CIRCUITO-HOSPITAL-DIA-PASO-A-PASO.md)
- [Video resumen de 70 segundos](docs/media/demo-flujo-7-pasos/flujo-oncologico-7-pasos.mp4)
- [Arquitectura hexagonal](docs/02-arquitectura/HEXAGONAL.md)
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

GitHub Actions compila `backend`, `bff` y `frontend`, corre pruebas de cada
uno (incluida la suite ArchUnit del backend) y levanta el producto completo
con Docker (5 servicios: database, redis, backend, bff, frontend) antes de
publicar imagen alguna. En una instalación local:

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

La última migración cerrada (backend a arquitectura hexagonal + BFF/JWT)
dejó `mvn verify` en 418 pruebas del backend sin fallas ni omisiones, más la
suite propia de `bff/`. El catálogo real tiene **114 operaciones**
documentadas (ver [ENDPOINTS.md](docs/02-arquitectura/ENDPOINTS.md)) y la
prueba Docker confirma el circuito clínico completo con cuatro drogas.
