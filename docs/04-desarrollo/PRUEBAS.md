# Pruebas

## Compilación

```powershell
mvn -f backend\pom.xml verify
mvn -f bff\pom.xml verify
```

## Prueba integral

Con el sistema iniciado:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\integration-test.ps1
```

Valida:

- salud;
- autenticación;
- paciente e historia;
- diagnóstico;
- protocolo y duración;
- tratamiento y ciclos;
- Farmacia y reserva por componente;
- turno sin superposición;
- triaje PASS;
- preparación y liberación;
- QR firmado;
- administración multidroga interrumpida y reanudada;
- conservación de dosis parcial, reacción e historial al cerrar;
- administración finalizada;
- hoja imprimible;
- evoluciones persistidas.

La prueba genera pacientes sintéticos solo en la base donde se ejecuta. No la
ejecute sobre producción.

## Paciente demostrativo de arranque

`DefaultDemoPatientBootstrapTest` cubre desactivación completa, creación única,
reparación exclusiva de una hoja faltante, recuperación ante dos arranques
concurrentes, colisiones de DNI/HC ajenas, recurso malformado y actor de
auditoría. Las colisiones y la falta de actor deben producir warning y omisión,
nunca una excepción que impida arrancar. La regresión versionada exige además
tres fronteras: misma versión sin escritura, recurso más nuevo que actualiza una
hoja aún intacta y recurso más nuevo que conserva una hoja con cualquier edición
humana. Un conflicto optimista debe releer y aceptar al ganador o concluir como
warning/no-op.

`BootstrapConfigurationTest` fija el orden de arranque: administrador, catálogos
y recién después paciente demostrativo. `DatabaseMigrationResourceTest`
comprueba que el artefacto incluya las 14 migraciones y que
`V012__patient_seed_identity.sql` contenga el índice único parcial sobre
`identity_json.seedKey`.

El entorno `compose.e2e.yaml` establece
`HCOP_SEED_EXAMPLE_PATIENT=false`. Así los recorridos crean únicamente sus
propios pacientes efímeros y la ficha predeterminada no altera conteos,
búsquedas ni aislamiento. Ninguna prueba ni recurso bootstrap contiene datos
reales. La versión 3 es un caso compuesto ficticio de colon y melanoma creado
desde cero, no una historia pseudonimizada. La validación del recurso empaquetado
es obligatoria para el release: un JSON inválido es un defecto del artefacto.

## Frontend Angular clínico

Desde `frontend`:

```powershell
npm test
npm run build
```

La suite pura cubre proyecciones, normalización del workspace, edición
estructurada de Motivo de consulta, Antecedentes de enfermedad actual,
Antecedentes personales, Examen físico y Conclusión / resumen, registro de
borradores sin contenido clínico, códigos de conflicto y comparación de
revisiones. El helper de Antecedentes personales contiene 14 casos y 104
aserciones sobre sus cuatro campos, instantáneas, compatibilidad y límites. El
helper de Examen físico agrega unidades, rangos, talla histórica en metros o
centímetros, filas normalizadas y métricas Du Bois: cerró con **14 casos y 83
aserciones aprobadas**. La proyección de impresión cerró con **6 casos y 28
aserciones aprobadas**. El recorrido Angular comprueba además que la plantilla
nunca sobrescriba texto. La compilación de producción generó un bundle de
**815.85 kB**: advierte por superar el presupuesto preventivo de **750 kB**, pero
permanece por debajo del límite que hace fallar la compilación.

Las pruebas Java de `ClinicalChiefComplaintAuthority`,
`ClinicalCurrentIllnessAuthority` y `ClinicalSummaryPlanAuthority` demuestran
que actor, fecha, motivo y versiones provienen del servidor, que un cliente no
puede reescribir la cadena confirmada y que el valor legacy no textual se
conserva. El corte 037 incorpora la misma cobertura para
`ClinicalPersonalHistoryAuthority`. La validación focalizada final ejecutó 66
pruebas Java/Swagger sin fallos ni omisiones. La prueba de contrato HTTP verifica que
`PUT /api/hc` devuelva el estado canónico con la nueva revisión y sin el
comando transitorio.

El recorrido concurrente y los editores migrados de la hoja se validan contra Java
y PostgreSQL reales en un entorno Docker efímero:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-clinical-conflict-e2e.ps1 -SkipInstall
```

El lanzador reserva `127.0.0.1:5182`, genera credenciales y secretos efímeros,
ejecuta dos sesiones Chrome independientes y elimina pacientes, contenedores,
redes y volúmenes tanto ante éxito como ante fallo. No reutiliza la base estable
ni la instancia QA ordinaria. Los recorridos de Conclusión / resumen y Motivo
de consulta verifican foco inicial, contención por teclado, ausencia de cierre
por fondo/Escape, retorno al disparador, bloqueo del contexto y auditoría
recuperada desde PostgreSQL. También interceptan un primer guardado con `503`
y comprueban que el diálogo y los valores editables permanezcan disponibles
para reintentar. Motivo de consulta agrega además un `VERSION_CONFLICT` real
con una segunda sesión.

Los circuitos esenciales de interfaz tienen un recorrido Playwright más corto,
independiente de la prueba de conflictos:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-core-browser-e2e.ps1
```

Este lanzador usa `127.0.0.1:5183`, una base y un almacenamiento descartables,
Chrome instalado en Windows (Chromium administrado por Playwright en CI) y
secretos aleatorios. Habilita únicamente
la ficha demostrativa sintética para comprobar el inicio de sesión por pantalla,
la apertura y persistencia de la historia al navegar, las pestañas de
Configuración y la matriz compacta de Hospital de día. El contrato visible fija
el intervalo predeterminado de 10 minutos como **3 columnas × 2 filas por hora**,
seis sillones, 48 casilleros por sillón, horarios, controles de zoom, fecha y
atributos accesibles. Al terminar elimina contenedores, redes, volúmenes e imagen
de prueba. GitHub Actions ejecuta este recorrido antes de publicar una imagen.

El recorrido dedicado de **Antecedentes de enfermedad actual** aplica el mismo
arnés a primera carga, modificación con motivo, error transitorio, conflicto
concurrente, persistencia canónica y recuperación. El corte 036 cerró con los
cuatro recorridos Playwright aprobados contra Java y PostgreSQL efímeros; el
arnés eliminó luego paciente, contenedores, redes y volúmenes sintéticos.

El corte 037 agrega el formulario Angular nativo en dos columnas de
**Antecedentes personales** y una instantánea versionada de sus cuatro campos.
El arnés Docker aprobó los cinco recorridos Playwright, incluido el nuevo
conflicto concurrente, y eliminó luego pacientes, contenedores, redes y
volúmenes sintéticos.

El corte 038 agrega **Examen físico** con Peso, Talla en cm, texto clínico,
plantilla optativa, IMC y Superficie corporal. La validación focal de autoridad
Java, validator, contrato MVC, permisos y OpenAPI aprobó **81/81 pruebas**. El
arnés Docker/Playwright aprobó **6/6 escenarios** y verificó que
`exam.heightM` vuelva a PostgreSQL en metros, que la presentación tolere
historias antiguas en cm, que Angular y Java produzcan las mismas filas e
instantáneas y que la plantilla no sobrescriba texto. Al finalizar eliminó los
pacientes sintéticos, contenedores, redes y volúmenes; el corte quedó validado
localmente y no fue publicado.

El corte 039 agrega la coordinación de **Estudios complementarios** entre hoja
y panel. La proyección pura aprobó **9/9 casos**, y la proyección de impresión
aprobó **7 casos y 30 aserciones**. La validación focal de backend y permisos
aprobó **19/19 pruebas**; el arnés Docker/Playwright aprobó **7/7 escenarios**.
El recorrido integrado sube y elimina un archivo real, verifica el cierre
automático exitoso del único modal de alta, foco y cierre explícito, combinación
y deduplicación local/externa, tombstones, orden ascendente en la hoja y orden
descendente en el panel. También abre y enfoca una tarjeta sin ID mediante su
clave estable y comprueba `role="button"`, `aria-pressed`,
`section.studies.view` y
`section.studies.edit` en interfaz y servidor. Estudios continúa `Pendiente`
por las deudas registradas en el documento del corte; la evidencia no declara
paridad completa.

En la aceptación final del 30/07/2026, la suite Java terminó con **101/101
pruebas aprobadas**. El E2E utilizó una aplicación de cuatro drogas, interrumpió
Carboplatino al 50 %, reanudó la administración y finalizó en `completed`
conservando dosis parcial, interrupción y reacción.

## Instalador, backup y restauración

La prueba estática de Windows valida tanto el ejecutor Docker directo como la
instalación administrada:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-github-launcher.ps1
```

Además de puerto, secretos y canales aislados, genera en una carpeta temporal
los accesos **Respaldar HCOP JP** y **Restaurar HCOP JP**. Exige que deleguen en
la versión instalada, que no incorporen contraseñas ni referencias a `.env` y
que el instalador incluya los tres scripts necesarios para operar los datos.

La recuperación real se comprueba en un proyecto Docker descartable:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-backup-restore.ps1
```

Ese ensayo recupera un dato PostgreSQL y un archivo de storage, comprueba que
desaparezca el estado posterior al backup y elimina solamente sus recursos
efímeros.

## Documentación y OpenAPI

Con HCOP JP iniciado:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\generate-api-docs.ps1 -Check
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-documentation.ps1
```

La primera orden comprueba que el catálogo de endpoints coincide exactamente
con Swagger. La segunda valida enlaces Markdown, páginas públicas, documentación
HTML y que cada operación OpenAPI tenga resumen, descripción, controlador y
permiso. Ambas se ejecutan también en GitHub Actions.

## Matriz de 100 casos de Hospital de día

Con una instancia QA aislada en `http://127.0.0.1:5181`:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\qa\hospital-day-100-cases.ps1 `
  -BaseUrl http://127.0.0.1:5181
```

La última evidencia registró
[100 PASS, 0 FAIL, 0 NO_DATA y 0 MANUAL](../08-auditoria/resultados/hospital-dia-100-casos-20260730-100711.md).
Esta matriz es independiente de la prueba integral multidroga anterior.

## Docker en GitHub

El workflow `verify.yml` construye el producto, espera la salud y destruye sus
volúmenes temporales al finalizar.
