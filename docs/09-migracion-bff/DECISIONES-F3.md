# Decisiones F3 — Backend hexagonal

Desvíos conscientes de la redacción literal del plan (`~/.claude/plans/fuzzy-waddling-galaxy.md`),
tomados durante la implementación y verificados contra el sistema real. El detalle de cada tarea
está en `PROGRESO.md`; este documento reúne solo las decisiones, no la crónica.

## F3.3.0 — Orden canónico patient ← treatment ← infusion, no 3 ports simétricos

El plan mencionaba `PatientLookupPort`, `TreatmentSummaryPort`, `TreatmentCyclePort`,
`ClinicalFilePort` como ejemplos de puertos a definir, sin decir en qué dirección corta cada
edge del ciclo. Investigado el ciclo real (14 dependencias cruzadas mapeadas entre `patient`,
`treatment`, `infusion`), se eligió un **orden canónico único**: `patient` (base, entidad raíz) ←
`treatment` (pertenece a un paciente) ← `infusion` (pertenece a un tratamiento — el más
dependiente). Regla: solo se permite depender "hacia arriba" en ese orden (`infusion` puede
importar `treatment` y `patient` directo; `treatment` puede importar `patient` directo); toda
dependencia "hacia abajo" (upstream necesitando algo de downstream) se invierte con un puerto
propiedad del módulo upstream, implementado por un adapter que vive físicamente en el módulo
downstream (el único que puede importar sus clases concretas).

De las 14 dependencias mapeadas, solo 5 violaban el orden y necesitaron puerto:
- `patient.PatientWorkspaceController` → `treatment.TreatmentService.list` ⇒
  `patient.application.port.out.TreatmentSummaryPort`, adapter en `treatment.infrastructure.patient`.
- `patient.PatientWorkspaceController` → `infusion.InfusionService.list` ⇒
  `patient.application.port.out.InfusionSummaryPort`, adapter en `infusion.infrastructure.patient`.
- `treatment.TreatmentDocumentService` → `infusion.InfusionRepository.list` (tipo `Infusion`) ⇒
  `treatment.application.port.out.InfusionAppointmentPort` con DTO propio
  (`InfusionAppointment`, 5 campos) — evita que `treatment` importe el record de dominio de
  `infusion`; adapter en `infusion.infrastructure.treatment`.
- `treatment.TreatmentRepository` → `infusion.TreatmentApplicationLogisticsService.synchronizeTreatment`
  ⇒ `treatment.application.port.out.TreatmentApplicationSyncPort`, mismo adapter.
- `treatment.TreatmentService` → `infusion.InfusionService.list` ⇒ mismo
  `treatment.application.port.out.InfusionSummaryPort` (ya devolvía `Map<String,Object>`, sin
  tipo de dominio que leakear — se reusó la misma interfaz para los dos call-sites de `treatment`).

Las 9 dependencias restantes (`treatment`/`infusion` → `patient.PatientService.require`,
`treatment`/`infusion` → `patient.PatientDocumentService`, `infusion` →
`treatment.DayHospitalApplicationPolicy`, `infusion` → `treatment.TreatmentRepository.find`)
ya respetaban el orden canónico y se dejaron como llamada directa — no todo cruce de módulo
necesita un puerto, solo los que van en el sentido equivocado.

**No se movió nada** (cumpliendo el pedido explícito del plan para F3.3.0): los 5 puertos y sus
3 adapters son las únicas piezas nuevas; `patient`/`treatment`/`infusion` siguen en
`TRACKED_LEGACY_MODULES` sin capas `domain/application/infrastructure` — eso es F3.3.

## R4 sigue con `@ArchIgnore`, se agregaron R4a/R4b como criterio de aceptación real

El comentario original de R4 decía "sacar el `@ArchIgnore` y ver la regla pasar" asumiendo que
`patient`↔`treatment`↔`infusion` era el único ciclo del grafo. Al levantarlo apareció que
`SlicesRuleDefinition.slices().matching("ar.com.hexium.hcop.(*)..")` detecta **dos ciclos más**,
preexistentes y ajenos a este commit:
- `catalog` ↔ `config`: los `*Store` de `catalog.infrastructure.persistence` leen
  `HcopProperties.catalogRoot()` (config→catalog en sentido inverso... en realidad catalog→config,
  y config no depende de catalog directamente — el ciclo real pasa por `ClinicalCatalogBootstrap`
  en `config`, que sí depende de `catalog.application.port.in.DiagnosisCatalogUseCase`).
- `config` ↔ `patient`: `config.BootstrapConfiguration` llama a
  `patient.DefaultDemoPatientBootstrap.seed()`, y `patient.PatientDocumentService` depende de
  `config.HcopProperties`.

`config` es `PERMANENTLY_EXEMPT_MODULES` — el plan (F3.4) lo renombra a `platform` pero
explícitamente **no lo hexagonaliza**. Romper esos dos ciclos es trabajo de F3.4, no de F3.3.0.
Se dejó `r4_slicesAreFreeOfCycles` con `@ArchIgnore` (documentando ambos ciclos restantes en su
javadoc) y se agregaron dos reglas nuevas, acotadas y ya en verde, como el criterio de
aceptación real de esta etapa:
- `r4a_patientDoesNotDependOnTreatmentOrInfusion`
- `r4b_treatmentDoesNotDependOnInfusion`

Verificado: `mvn -f backend/pom.xml verify` verde, 390 tests (388 + R4a/R4b nuevos), 1 skip
(R4 genérico). No se corrió Docker en esta etapa (F3.3.0 no toca ningún endpoint HTTP ni el
comportamiento observable — es reorganización interna pura, sin cambio de contrato; el
guardián de OpenAPI no aplica).

## F3.3 (treatment) — `applicationDoesNotDependOnInfrastructure` es incondicional ENTRE módulos

Hallazgo real, no anticipado en el plan ni en las etapas anteriores de F3.3: la regla ArchUnit
`applicationDoesNotDependOnInfrastructure` ("ningún `..application..` depende de
`..infrastructure..`") no es solo una regla intra-módulo — aplica **entre módulos distintos**
también, y es incondicional (no la relaja `TRACKED_LEGACY_MODULES`). Al mover
`DayHospitalApplicationPolicy` de `treatment` (legacy, paquete raíz) a
`treatment.infrastructure.legacy.DayHospitalProtocolRules`, se rompió porque
`qr.application.service.QrApplicationService` (ya hexagonal desde F3.3 3/6) llama a
`DayHospitalApplicationPolicy.isValidApplicationDay(int)` — un método puro sin `JsonNode` — y esa
llamada ahora cruzaba `application` → `infrastructure` de otro módulo.

Fix: partir la clase original en dos fragmentos según si tocan `JsonNode` o no:
- `treatment.domain.DayHospitalApplicationPolicy` — `MAX_APPLICATION_DAY` + `isValidApplicationDay(int)`,
  Java puro, sin Jackson. Es lo que consume `qr.application.service.QrApplicationService`.
- `treatment.infrastructure.legacy.DayHospitalProtocolRules` — `requiresDayHospital(JsonNode)` +
  `applicationDays(JsonNode)`, con Jackson. Es lo que consume `qr.infrastructure.infusion.QrInfusionAdapter`
  (ya en infraestructura, sin restricción).

Patrón reutilizable: cuando una "regla de negocio pura" legacy mezcla métodos sin I/O con métodos
que reciben `JsonNode`, y **otro módulo ya hexagonal** consume el fragmento puro directo (no a
través de un puerto), hay que partir la clase en el momento de hexagonalizar el módulo dueño —
no alcanza con mover todo a `infrastructure` así el resto del módulo original conviva ahí.

## F3.4 — `common`+`config` → `platform`, `ApiException` acotado a web, 2 bugs de propagación

### `common`/`config` → `platform` (fusión, no dos paquetes separados)

El plan decía `common` → `sharedkernel`+`platform/web`, `config` → `platform`. Investigado el
contenido real de `common/` (`ApiException`, `ApiExceptionHandler`, `common.api.{ApiErrorResponse,
AuthenticationRequiredResponse}`): las 4 clases son contrato HTTP puro, nada de dominio — no hay
nada que mover a `sharedkernel` (que ya existe, con value objects de dominio compartidos —
`UserId`/`PatientId`/etc. — sin relación con `common`). Se fusionaron `common`→`platform.web` y
`config`→`platform` (raíz) en un único módulo `platform`, `PERMANENTLY_EXEMPT_MODULES` pasa de
`{auth, common, config}` a `{auth, platform}`.

### `ApiException`: el target real no era "cero consumidores fuera de los 3 exentos"

El texto del plan ("eliminar ApiException… debería quedar sin consumidores fuera de
auth/common/config") **contradice un precedente ya sentado en F3.3 (5/6, patient)**: `ClinicalDocumentController`/
`ClinicalDocumentAccessPolicy`/`ClinicalDocumentChangeValidator` ya usaban `ApiException` a
propósito para 2 precondiciones puramente de borde HTTP (paciente activo, revisión requerida) —
**R6** (`el controller solo conoce el puerto de entrada`) les prohíbe construir el `*Failure` del
propio módulo (vive en `application.service`, y solo `*FailureAdvice` tiene esa excepción). Intentar
convertir esas validaciones a `*Failure` directo en el controller viola R6 — lo confirmé
literalmente al intentarlo en `ClinicalFileController`/`StudyTemplateController` y revertirlo.

**Decisión** (reconcilia el texto del plan con el precedente ya existente, documentado como
desvío consciente igual que el resto de F3): `ApiException` (en `platform.web`) sigue siendo el
vehículo legítimo para validaciones de forma HTTP hechas directo en `infrastructure.web`
(estructura del body, límites de tamaño de request, discriminadores de payload) — el target real
es **cero consumidores en `application`, `infrastructure.persistence`, `infrastructure.http` y
cualquier otro adapter no-web** de los módulos hexagonales; `infrastructure.web` puede seguir
usándolo por la razón estructural de R6. Verificado con
`grep -rl ApiException backend/src/main/java` tras el commit: los únicos consumidores fuera de
`auth`/`platform` son 8 clases, todas `infrastructure.web` (media×3, patient×5, integration×1,
treatment×1) — ninguna en `application`/`infrastructure.persistence`/`infrastructure.http`.

### Módulos migrados a su propio `*Failure`

- **`infusion`** (el único módulo de F3.3 sin `*Failure`/`*FailureAdvice` — su `application` es
  passthrough deliberado desde F3.3 PR3, la lógica vive en los `Postgres*Store`): nuevo
  `InfusionFailure`(`INVALID`/`NOT_FOUND`/`CONFLICT`) + `InfusionFailureAdvice`. ~50 sitios
  `ApiException`→`InfusionFailure` en `PostgresApplicationWorkflowStore`/
  `PostgresInfusionOperationsStore`/`ApplicationComponentValidator` (delegado a un agente en
  background, mecánico, revisado antes de commitear).
- **`integration`**: `IntegrationFailure` gana `UNAVAILABLE`/`UPSTREAM_ERROR`/`TIMEOUT` (antes
  solo `INVALID`/`NOT_FOUND`, sin uso real) — `HttpLlmClient` (adapter HTTP externo,
  `infrastructure.http`, no web) migrado completo. `LlmController` (web) conserva su único
  `ApiException` (precondición de payload, mismo criterio que patient).
- **`media`**: `MediaFailure` gana `INTERNAL` (I/O de filesystem que antes era 500 con
  `ApiException`). `FilesystemClinicalFileBlobStore`/`FilesystemStudyTemplateManifestStore`
  migrados. `ClinicalFileController`/`StudyTemplateController` (web) conservan `ApiException`.
- **`treatment`**: el único `ApiException` real (`TreatmentController.duration`, `esquema no
  encontrado`) queda igual — es web, mismo criterio.
- **`patient`**: sin cambios — ya usaba el criterio correcto desde F3.3.

### Hallazgo real: 2 módulos (`diagnosis`/`qr`) tenían un 500 en vez de 404 desde que `patient` se
hexagonalizó (F3.3 5/6), sin que nadie lo notara

`PatientDiagnosisAdapter`/`QrPatientAdapter`/`PatientEvolutionAdapter` (de `qr` y de `workflow`) y
los adapters cruzados de `treatment`/`infusion`/`media` llaman a `patient.application.port.in.
{PatientUseCase,PatientDocumentUseCase}.require(...)`. Sus javadocs (escritos en F3.3.0/F3.3
1-3/6, **antes** de que `patient` se hexagonalizara en F3.3 5/6) decían "el 404 de paciente
inexistente sigue viajando como `ApiException` sin traducir desde `PatientService.require`" — cierto
en su momento (esos módulos migraron antes que `patient`), **falso desde F3.3 5/6**: `PatientUseCase.require`
pasó a lanzar `PatientFailure.NOT_FOUND` (no `ApiException`), y ningún `*FailureAdvice` de
`diagnosis`/`qr`/`workflow`/`treatment`/`infusion`/`media` sabe capturar el `PatientFailure` de
otro módulo — caía en el `@ExceptionHandler(Exception.class)` genérico de `ApiExceptionHandler`:
**500 en vez de 404**, sin ningún test que lo detectara (los tests mockean el puerto de entrada,
nunca ejercitan el `*FailureAdvice` real contra un `PatientFailure` real).

**Fix, mismo patrón en los 10 adapters cruzados a `patient`** (`diagnosis.PatientDiagnosisAdapter`,
`qr.{QrPatientAdapter,PatientEvolutionAdapter}`, `workflow.PatientEvolutionAdapter`,
`treatment.{TreatmentPatientAdapter,PatientDiagnosisOptionsAdapter}`,
`treatment.infrastructure.persistence.PostgresTreatmentStore`,
`infusion.infrastructure.persistence.{PostgresApplicationWorkflowStore,PostgresInfusionOperationsStore}`,
`media.PatientServiceLookupAdapter`): atrapar `PatientFailure` en el adapter (el borde correcto,
mismo criterio que "el store traduce la excepción ajena" ya usado en `admin`/`workflow`) y
relanzar el `*Failure` propio del módulo — `NOT_FOUND`→`NOT_FOUND` siempre, `CONFLICT`→`CONFLICT`
donde el módulo lo soporta (mutaciones vía `appendImmutableEvolution`). No se tocó ningún test
viejo — ninguno ejercitaba este camino, así que no había aserciones que romper; no se agregaron
tests nuevos por el volumen (10 adapters, patrón idéntico y mecánico) pero queda documentado como
riesgo real cubierto solo por esta traducción defensiva, no por un test explícito.

### Hallazgo real: ciclo `auth` ↔ `platform`, no anticipado por el plan

Al levantar el `@ArchIgnore` de `r4_slicesAreFreeOfCycles` tras romper los 2 ciclos documentados
(`catalog`↔`config`, `config`↔`patient` — ver más abajo), ArchUnit encontró un tercer ciclo:
`auth` construye `platform.web.ApiException` directo, y `platform.{SecurityConfiguration,
WebConfiguration,BootstrapConfiguration}` conectan los beans de `auth` (`AuthInterceptor`,
`AuthService`, `TokenIssuer`, `JwtAuthenticationFilter`) en el wiring de Spring. Es acoplamiento
mutuo esperado entre los dos únicos módulos "pegamento" permanentemente exentos — no el ciclo de
negocio que R4 busca cazar entre los ~14 módulos clínicos. Se excluyeron `auth`/`platform` del
`@AnalyzeClasses` de `HexagonalArchitectureTest` con un `ImportOption` propio
(`ExcludeAuthAndPlatform`) — verificado que es seguro para las otras 17 reglas: todas ya exentan
esos 2 paquetes vía `allowListedPackages()`, o solo miran subpaquetes `domain`/`application`/
`infrastructure` que ninguno de los dos tiene (son planos, sin capas).

### Los 2 ciclos documentados en F3.3.0 (`catalog`↔`config`, `config`↔`patient`) — `BootstrapTask`

El ciclo real: `platform.BootstrapConfiguration` importaba las clases concretas
`ClinicalCatalogBootstrap` (de `catalog`) y `DefaultDemoPatientBootstrap` (de `patient`) para
llamarlas en el arranque — la dirección "hacia abajo" prohibida (`platform` es infraestructura
transversal, nunca debería depender de un módulo clínico). Fix: nueva interfaz
`platform.BootstrapTask` (`void run()`); cada módulo implementa la suya
(`catalog.infrastructure.persistence.ClinicalCatalogBootstrapTask`,
`patient.infrastructure.bootstrap.DefaultDemoPatientBootstrap`, ambas `@Component` con `@Order`
1/2 preservando el orden original: administrador→catálogos→paciente demo) — `platform.
BootstrapConfiguration` pasa a inyectar `List<BootstrapTask>` (Spring ordena por `@Order`) sin
conocer ninguna clase concreta. `ClinicalCatalogBootstrapTask` se movió a
`infrastructure.persistence` (no `infrastructure.bootstrap`) porque toca `JdbcTemplate` directo y
R2 solo permite JDBC en `infrastructure.persistence`/`infrastructure.catalog`.

### Deuda para antes de mergear a `main`

El texto de la API en `OpenApiConfiguration` ("La aplicación sigue MVC…") se actualizó a
"arquitectura hexagonal" — cambia el campo `description` del spec, así que
`scripts/generate-openapi-snapshot.ps1 -Check` **va a mostrar diff** hasta que alguien lo
regenere contra el stack Docker real (el usuario corre Docker, no Claude). Se suma a la deuda ya
documentada en `PROGRESO.md` (verificación end-to-end pendiente de F3.3) — un solo paso de Docker
antes de mergear cubre ambas.

`docs/02-arquitectura/MVC.md` → `HEXAGONAL.md` (reescrito, no solo renombrado — describía la
arquitectura MVC pre-F0, sin BFF ni capas). Referencias actualizadas en `docs/README.md` y
`docs/04-desarrollo/ESTRUCTURA-DEL-REPOSITORIO.md`.
