# Diagramas

Diagramas Mermaid de la aplicación: arquitectura, flujos de comunicación entre
servicios y modelo de datos. Carpeta transversal (no numerada) porque el
contenido aplica tanto a `02-arquitectura/` como a `03-base-de-datos/`.

```
diagrams/
  src/    fuente editable (.mmd) — versión de verdad, se edita a mano
  png/    export en alta resolución (committeado, listo para pegar en docs/slides)
```

## Índice

| Archivo | Qué muestra |
|---|---|
| `01-arquitectura-contenedores` | Los 5 servicios Docker, las 2 redes (`hcop_internal` / `hcop_egress`) y cómo se comunican |
| `02-secuencia-login` | Login vía patrón BFF Token Handler — por qué el navegador nunca ve el JWT |
| `03-secuencia-request-autenticado` | Request típico autenticado: cookie → BFF → Redis → Bearer JWT → backend → DB |
| `04-modelo-datos-identidad-paciente` | Tablas de auth/RBAC (`local_users`, roles, permisos, sesión JWT) + identidad de paciente |
| `05-modelo-datos-tratamiento` | Tablas de tratamiento, ciclos, turnos, workflow y auditoría clínica |
| `06-modelo-datos-circuito-farmacia` | Circuito de validación farmacéutica: reserva de stock, preparación, lotes |

El modelo de datos se dividió en 3 diagramas (identidad/paciente, tratamiento,
farmacia) en vez de uno solo con las 35 tablas — un único ER de ese tamaño
queda ilegible incluso en alta resolución. Cada tabla muestra su nombre real
de la base (`local_*` es prefijo real, no simplificación mía — ver
`backend/src/main/resources/db/migration/V001__core_schema.sql` y
`V013__jwt_auth.sql`) más columnas clave (PK, FK, campos de negocio). Se
omiten a propósito columnas de auditoría repetitivas
(`created_at`/`updated_at`/`created_by`/`revision`) y blobs `jsonb` grandes
para que el diagrama se lea de un vistazo — el diccionario completo columna
por columna está en `docs/03-base-de-datos/DICCIONARIO-DE-DATOS.md`.
`treatment_application_logistics` aparece repetida (resumida) en 05 y 06
como ancla de la relación "ejecuta" — su definición completa vive en 05.

## Regenerar los PNG

Los `.mmd` son la fuente de verdad. Si se edita un `.mmd`, hay que
regenerar su PNG:

```powershell
pwsh scripts/generate-diagrams.ps1
# o un solo diagrama:
pwsh scripts/generate-diagrams.ps1 -Only 05-modelo-datos-tratamiento
```

Usa `npx @mermaid-js/mermaid-cli` (no requiere instalación global). Escala
3x (`-Scale`) para que el PNG se vea nítido ampliado o impreso.

## Convención

- Un `.mmd` por diagrama, numerado por orden de lectura sugerido.
- Español en labels/nodos, igual que el resto de `docs/`.
- Si un diagrama de datos supera ~15 entidades, dividir por dominio en vez
  de forzarlo en uno solo (regla aplicada en 04/05/06).
