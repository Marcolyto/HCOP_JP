# 02 · Construir la arquitectura hexagonal

## Regla de dependencias

```text
                    ┌─────────────┐
   Angular ──HTTP──▶│ web (in)    │
                    │  ↓          │
                    │ application │──▶ port/out ──▶ infrastructure/persistence ──▶ PostgreSQL
                    │  (service)  │                          │
                    │  ↑          │                          └──▶ Storage / integración externa
                    │ port/in     │
                    └─────────────┘
                     domain (sin frameworks)
```

`domain` y `application` no importan Spring, JDBC ni Jackson (`tools.jackson`
incluido) — regla incondicional de ArchUnit, sin excepción por allow-list.
`infrastructure` es la única capa que conoce PostgreSQL, HTTP, Jackson o el
resto del framework. La dependencia nunca vuelve en sentido contrario: un
puerto de salida (`port/out`) lo define `application`, lo implementa
`infrastructure`.

## Las siete piezas de un módulo

Cada capacidad clínica (`patient`, `treatment`, `qr`, `admin`, …) repite el
mismo esqueleto:

```text
<módulo>/
  domain/
    <ModeloDeDominio>.java          — records/clases puras, sin anotaciones de framework
  application/
    port/in/
      <Modulo>UseCase.java          — el caso de uso, comandos anidados como records
    port/out/
      <Modulo>Store.java            — persistencia (sufijo Store)
      <Otro>Port.java                — dependencia hacia otro módulo (sufijo Port)
    service/
      <Modulo>Failure.java          — enum de fallos del dominio (INVALID/NOT_FOUND/CONFLICT/...)
      <Modulo>ApplicationService.java — final, sin @Service (lo aplica infrastructure/configuration)
  infrastructure/
    persistence/
      Postgres<Modulo>Store.java    — @Repository, JdbcTemplate, implementa <Modulo>Store
    <otro-módulo>/
      <Modulo><OtroModulo>Adapter.java — implementa el puerto cruzado hacia otro módulo
    configuration/
      <Modulo>ModuleConfiguration.java (variante B: @Bean read-only)
      Transactional<Modulo>Management.java (variante A: @Service + @Transactional)
    web/
      <Modulo>Controller.java
      <Modulo>JsonMapper.java
      <Modulo>FailureAdvice.java     — traduce <Modulo>Failure → HTTP en el borde
```

## `domain`

Modelos puros. Records o clases sin `@Entity`, sin Jackson, sin JDBC. Un
campo que en el original era `JsonNode` (un catálogo legacy heterogéneo, no
un modelo de dominio real) se tipa `Object` opaco en la firma — sigue
siendo el mismo objeto en runtime, pero `domain`/`application` no declaran
la dependencia a Jackson para tocarlo. Sólo `infrastructure` lo castea de
vuelta.

## `application` — puerto de entrada + servicio

`port/in/<Modulo>UseCase` declara el contrato del caso de uso: qué se puede
pedir y qué devuelve, con comandos/resultados como records anidados. El
`<Modulo>ApplicationService` lo implementa:

- valida invariantes del dominio;
- controla transiciones de estado;
- coordina uno o más `port/out`;
- implementa idempotencia;
- devuelve `<Modulo>Failure` (nunca una excepción HTTP — eso es del borde).

Es una clase Java normal (`final`, sin `@Service`): la anotación Spring vive
en `infrastructure/configuration`, para que `application` no dependa del
framework.

## `application` — puerto de salida

`port/out/<Modulo>Store` es la interfaz que `application` necesita para
persistir. La implementa `infrastructure/persistence/Postgres<Modulo>Store`
con SQL parametrizado:

```sql
UPDATE recurso
   SET dato = ?, revision = revision + 1, updated_at = clock_timestamp()
 WHERE id = ? AND revision = ?
RETURNING revision;
```

Si no vuelve una fila, `application` diferencia inexistencia de conflicto y
devuelve el `<Modulo>Failure` correspondiente — el borde web lo traduce a
`404` o `409`.

## Puertos cruzados (un módulo consumiendo otro)

Un módulo nunca importa el `ApplicationService` ni el repositorio interno de
otro módulo directo. Si `treatment` necesita datos de `patient`, define su
propio `port/out/PatientLookupPort` (con el shape mínimo que necesita, no el
modelo completo de `patient`) y `patient` provee un adapter en
`infrastructure/<consumidor>/` que lo implementa. Esto:

- evita ciclos de compilación entre módulos hermanos;
- deja que cada módulo declare exactamente lo que necesita del otro, no todo
  su dominio;
- permite que ArchUnit verifique que los módulos clínicos son libres de
  ciclos (`r4_slicesAreFreeOfCycles`).

Cuando dos módulos necesitan depender uno del otro en direcciones opuestas
(p. ej. `patient`↔`treatment`↔`infusion`), fije un **orden canónico** y sólo
permita que la dependencia "hacia abajo" cruce con un puerto — la "hacia
arriba" es llamada directa. Documente el orden elegido.

## `infrastructure/web` — el controller

Responsabilidades:

- declarar método y ruta;
- resolver `Authorization: Bearer` (vía `AuthContext`, poblado por el
  filtro JWT — no hay `HttpServletRequest` manual en el caso de uso);
- exigir permiso;
- delegar una sola operación al `port/in`;
- mapear el resultado a JSON con `<Modulo>JsonMapper`;
- elegir el código HTTP a través de `<Modulo>FailureAdvice`.

No debe: ejecutar SQL, conocer Jackson más allá de su propio mapper, ni
construir una excepción de otro módulo (`PatientFailure` dentro de
`ClinicalDocumentController`, por ejemplo, está prohibido — el controller
sólo conoce el `Failure` de su propio módulo).

## Manejo de errores

Cada módulo tiene su `<Modulo>FailureAdvice` (`@RestControllerAdvice`
acotado a los controllers de ese módulo) que traduce `<Modulo>Failure` a:

```json
{
  "ok": false,
  "error": "Mensaje seguro",
  "code": "conflict",
  "status": 409
}
```

Un adapter que consume otro módulo (p. ej. `TreatmentPatientAdapter`
llamando a `patient`) debe **atrapar el `Failure` ajeno y relanzar el
propio** — si lo deja propagar sin traducir, ningún advice lo captura y cae
como `500` genérico. Este es un bug real y fácil de cometer: verifíquelo con
una prueba de integración, no sólo con mocks.

## Hito de aceptación

Para el primer módulo vertical implemente las siete piezas:

1. migración;
2. `domain` + `port/in` + `port/out`;
3. `ApplicationService` con `Failure`;
4. `Postgres*Store`;
5. `Controller` + `JsonMapper` + `FailureAdvice`;
6. prueba de éxito, entrada inválida, permiso y conflicto;
7. `HexagonalArchitectureTest` en verde (sin agregar el módulo a ninguna
   lista de excepción).

Use ese corte vertical como plantilla, no cree primero todos los
Controllers y deje persistencia/seguridad para el final.
