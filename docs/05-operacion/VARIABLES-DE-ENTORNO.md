# Variables de entorno

HCOP JP tiene valores de desarrollo para poder iniciar una prueba, pero una
instalación real debe definir contraseñas y secretos propios. Docker Compose lee
el archivo `.env`; el JAR también acepta estas variables directamente.

## Aplicación

| Variable | Predeterminado | Uso | Reinicio |
|---|---|---|---|
| `HCOP_PORT` | `5180` | Puerto HTTP publicado por la aplicación. | Sí |
| `HCOP_BIND_ADDRESS` | `0.0.0.0` | Interfaces de red en las que escucha Java. | Sí |
| `HCOP_PUBLIC_BASE_URL` | `http://127.0.0.1:5180` | URL absoluta usada al construir enlaces/QR. | Sí |
| `HCOP_SESSION_MINUTES` | `43200` | Duración máxima de sesión en minutos. | Sí |
| `HCOP_RUNTIME_ROOT` | `./runtime` | Raíz de datos externos al JAR. | Sí |
| `HCOP_CATALOG_ROOT` | `${HCOP_RUNTIME_ROOT}/catalogs` | Catálogos de referencia incluidos con la versión. | No; se reconstruyen desde la imagen. |
| `HCOP_STORAGE_ROOT` | `${HCOP_RUNTIME_ROOT}/storage` | Archivos clínicos privados y guías PDF agregadas desde Configuración. | Sí |

`HCOP_BIND_ADDRESS=0.0.0.0` permite acceso por intranet, pero también debe
existir una regla de firewall y una ruta de red. Consulte
[Acceso por red](ACCESO-POR-RED.md).

## PostgreSQL

| Variable | Predeterminado | Uso | Reinicio |
|---|---|---|---|
| `HCOP_DB_URL` | `jdbc:postgresql://127.0.0.1:5433/hcop_jp` | JDBC completo al ejecutar el JAR. | Sí |
| `HCOP_DB_NAME` | `hcop_jp` | Base creada por Docker Compose. | Recrear sólo si cambia el volumen |
| `HCOP_DB_USER` | `hcop` | Usuario de PostgreSQL. | Sí |
| `HCOP_DB_PASSWORD` | obligatorio | Contraseña de PostgreSQL. | Sí |
| `HCOP_DB_POOL_MAX` | `16` | Máximo de conexiones de HikariCP. | Sí |

Cambiar `HCOP_DB_NAME` o credenciales no migra automáticamente un volumen
existente. Primero haga un backup, cree el destino y restaure.

## Usuario inicial

| Variable | Predeterminado de prueba | Uso |
|---|---|---|
| `HCOP_BOOTSTRAP_USERNAME` | `marcolyto` | Usuario administrador inicial. |
| `HCOP_BOOTSTRAP_PASSWORD` | obligatorio, mínimo 10 caracteres | Contraseña inicial. |
| `HCOP_BOOTSTRAP_SECOND_USERNAME` | `marcolyto2` | Segunda cuenta clínica para probar flujos. |
| `HCOP_SEED_EXAMPLE_PATIENT` | `true` | Crea una sola ficha sintética y actualiza de forma versionada únicamente su hoja intacta; `false` impide crearla, repararla o refrescarla. |

Estas variables crean cuentas faltantes; no reemplazan silenciosamente la
contraseña de una cuenta ya persistida. Cambie las claves desde la interfaz.

`HCOP_SEED_EXAMPLE_PATIENT` no activa la ficha en ninguna sesión y tampoco
elimina una ficha existente cuando cambia a `false`. La identidad y la historia
incluidas con la aplicación son inventadas; este recurso nunca debe contener
datos reales ni utilizarse como ficha asistencial. Una edición humana incrementa
la revisión y bloquea definitivamente la actualización automática del contenido
demostrativo. La versión distribuida es `meta.demoContentVersion=3` y representa
un caso compuesto de colon y melanoma creado completamente desde cero, no una
historia anonimizada o pseudonimizada. El seed es best-effort: colisiones de
DNI/HC, ausencia del actor de auditoría o una concurrencia no resoluble generan
warning y omisión, nunca una caída del servicio. Un recurso empaquetado inválido
es un defecto de release que debe detectarse antes de publicar la imagen.

## Secretos

| Variable | Predeterminado de prueba | Uso |
|---|---|---|
| `HCOP_QR_SECRET` | obligatorio | Firma HMAC de códigos QR. |
| `HCOP_ENCRYPTION_SECRET` | obligatorio | Cifrado de secretos como la API key del LLM. |
| `HCOP_JWT_SECRET` | obligatorio, ≥32 bytes | Firma HS256 de los JWT de sesión (F2, `TokenIssuer`). |

En producción use cadenas aleatorias diferentes para cada una, largas y
respaldadas en un gestor seguro. Si se pierde `HCOP_QR_SECRET`, los QR
emitidos dejan de ser verificables. Si se pierde `HCOP_ENCRYPTION_SECRET`,
los secretos cifrados no se pueden recuperar. Si se pierde o rota
`HCOP_JWT_SECRET`, todas las sesiones activas quedan invalidadas (todo JWT
firmado con el secreto anterior deja de verificar). No coloque ninguno en Git.

## Ejemplo mínimo seguro

Copie `.env.example` como `.env` y reemplace todos los textos de prueba:

```dotenv
HCOP_PORT=5180
HCOP_DB_NAME=hcop_jp
HCOP_DB_USER=hcop
HCOP_DB_PASSWORD=una-clave-larga-y-unica
HCOP_BOOTSTRAP_USERNAME=administrador
HCOP_BOOTSTRAP_PASSWORD=otra-clave-larga-y-unica
HCOP_BOOTSTRAP_SECOND_USERNAME=medico2
HCOP_SEED_EXAMPLE_PATIENT=false
HCOP_QR_SECRET=secreto-aleatorio-de-al-menos-32-caracteres
HCOP_ENCRYPTION_SECRET=otro-secreto-aleatorio-independiente
HCOP_JWT_SECRET=otro-secreto-aleatorio-de-al-menos-32-bytes
HCOP_PUBLIC_BASE_URL=https://hcop.institucion.example
```

El `.gitignore` excluye `.env`. Confirme antes de publicar con `git status`.

## Configuración LLM

El endpoint, modelo, proveedor (compatible OpenAI, Ollama o LM Studio) y API key
se administran desde la interfaz y se guardan en `system_settings`. La API key
se cifra y nunca vuelve en las respuestas. No corresponde colocarla en
capturas, documentación ni ejemplos.
