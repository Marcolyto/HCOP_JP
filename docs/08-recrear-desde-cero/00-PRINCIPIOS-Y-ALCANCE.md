# 00 · Principios, alcance y decisiones

## Objetivo del sistema

HCOP JP concentra historia clínica oncológica y Hospital de Día en una sola
aplicación. El producto debe poder funcionar sin Lira, Node.js ni MySQL. La
compatibilidad histórica se conserva sólo donde evita romper la interfaz o un
contrato útil.

## Principios obligatorios

### Una fuente canónica

PostgreSQL es la autoridad de identidad, seguridad y operación. La hoja clínica
JSONB es la autoridad narrativa. Los archivos binarios viven en almacenamiento
privado y PostgreSQL conserva sus metadatos y hash.

### Un único límite de seguridad

El navegador habla únicamente con la API Java del mismo origen. Java valida
sesión, permisos, entrada, estados y concurrencia antes de persistir.

### Inmutabilidad donde importa

Evoluciones, eventos de workflow, auditoría y versiones históricas se agregan;
no se reescriben para ocultar una decisión previa. Las correcciones se expresan
como un nuevo evento o una nueva versión.

### Integridad en profundidad

La UI previene errores comunes, el servicio aplica reglas clínicas y
PostgreSQL impone restricciones que deben resistir dos usuarios concurrentes.

### Privacidad por defecto

No se incluyen **datos de pacientes reales** en Git, imágenes Docker, fixtures
ni ejemplos. Los logs evitan contenido clínico, cookies, contraseñas y claves.
Una demostración puede incluir una ficha sólo cuando toda su identidad, historia,
cronología y documentación hayan sido creadas íntegramente desde cero, esté
marcada inequívocamente como sintética y no exista posibilidad razonable de
asociarla a una persona real. Nunca se anonimiza, pseudonimiza ni transforma una
historia fuente para convertirla en demostración.

### Seeds demostrativos reversibles

Un seed de demostración debe poder desactivarse por configuración, usar una
clave estable separada de los identificadores clínicos y ser idempotente ante
reinicios y concurrencia. Nunca selecciona un paciente para una sesión, nunca
transforma datos reales en datos de prueba y nunca sobrescribe una edición
humana. Puede actualizar contenido administrado sólo mediante una versión
explícita y mientras la revisión actual coincida con la última revisión escrita
por el propio seed. La misma versión no produce escrituras. Desactivarlo impide
ejecuciones futuras; no borra silenciosamente lo ya persistido. El seed es
**best-effort y nunca bloquea el arranque**: una colisión de DNI/HC, la ausencia
del actor de auditoría o una carrera optimista no resoluble se registran como
warning y omisión segura. Un recurso empaquetado inválido es, en cambio, un
defecto de release que las pruebas deben detectar antes de publicar el artefacto.

### Cambios reproducibles

Todo cambio de base es una migración Flyway. Todo contrato HTTP aparece en
OpenAPI. Todo despliegue se construye desde el repositorio y se valida en CI.

## Límites de dominio

| Dominio | Responsabilidad |
|---|---|
| Autenticación | usuarios, sesiones y paciente activo |
| Administración | roles, permisos y política de acceso |
| Paciente | identidad y espacio de trabajo |
| Historia clínica | documento versionado y evoluciones |
| Diagnóstico | SNOMED, CIE-10, AJCC, TNM y estadio |
| Tratamiento | prescripción, protocolo, drogas y ciclos |
| Hospital de Día | logística, turnos, sillones y administración |
| Workflow | suspensión, continuidad y solicitudes |
| Configuración | protocolos, guías, cálculos, formularios y parámetros |
| Archivos | estudios, imágenes, plantillas y autorización de descarga |
| Integraciones | LLM opcional y cifrado de secretos |
| Operación | salud, métricas, Docker, backup y actualización |

## Decisiones iniciales

1. **Tres servicios, un dominio hexagonal:** `backend` (Java, arquitectura
   hexagonal por módulo clínico), `bff` (Java, Token Handler de sesión) y
   `frontend` (Angular + nginx) — no un monolito que sirve HTML y API desde
   el mismo proceso. Ver [01](01-INICIALIZAR-PROYECTO.md).
2. **Spring MVC + JDBC dentro de cada módulo:** contratos claros y SQL
   explícito para reglas relacionales críticas; `domain`/`application` sin
   dependencia a frameworks (ArchUnit lo hace cumplir). Ver
   [02](02-ARQUITECTURA-HEXAGONAL.md).
3. **PostgreSQL:** JSONB, transacciones, índices parciales y controles
   concurrentes — sólo en `backend`.
4. **Frontend separado, mismo punto de entrada público:** Angular no
   comparte proceso con el backend; nginx expone un único puerto (5180) y
   enruta `/api/` hacia el BFF, no hacia el backend.
5. **Sesión vía Token Handler:** el navegador nunca ve un JWT. El BFF le da
   una cookie de sesión opaca (`BFF_SESSION`, HttpOnly), guarda el
   access/refresh token real en Redis y reenvía `Authorization: Bearer` al
   backend en cada request. El backend valida JWT firmado con
   `JwtAuthenticationFilter`, sin cookies propias.
6. **Flyway:** migraciones inmutables; limpieza destructiva deshabilitada.
7. **OpenAPI generado:** Swagger deriva del código del backend y se
   verifica en CI (`generate-api-docs.ps1 -Check`,
   `generate-openapi-snapshot.ps1 -Check` — el snapshot es el guardián real
   del contrato, `generate-api-docs.ps1` es una proyección *lossy*).
8. **Storage fuera del JAR:** los adjuntos sobreviven actualizaciones.

Si una decisión cambia, documente motivo, alternativas, consecuencias y plan de
migración con una ADR.

## Definición de terminado

Una función está terminada cuando:

- tiene caso normal y errores definidos;
- comprueba permiso en servidor;
- valida cuerpo y transiciones;
- persiste en una transacción coherente;
- maneja concurrencia si corresponde;
- registra auditoría/evolución cuando es acto clínico;
- aparece en Swagger;
- tiene pruebas;
- está explicada para usuario y mantenimiento;
- puede desplegarse y restaurarse sin pasos ocultos.
