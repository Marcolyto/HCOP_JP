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
