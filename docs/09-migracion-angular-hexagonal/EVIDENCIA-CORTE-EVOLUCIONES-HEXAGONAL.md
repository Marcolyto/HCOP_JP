# Corte hexagonal: evoluciones clínicas

Fecha de validación: 2026-07-31

## Alcance

El agregado inmutable de evoluciones utiliza ahora `ClinicalEvolutionUseCase`
y `JdbcClinicalEvolutionAdapter`. El adaptador bloquea la historia con
`FOR UPDATE`, reemplaza una evolución con el mismo identificador, agrega la
nueva al inicio y aumenta la revisión dentro de la misma transacción.

## Compatibilidad

`PatientDocumentService.appendImmutableEvolution` conserva su firma y retorno.
Por ello tratamientos, farmacia, triaje, administración y QR siguen agregando
la misma evolución a la hoja clínica sin modificar sus contratos.

## Pruebas reproducibles

```powershell
docker run --rm --mount "type=bind,source=${PWD},target=/workspace" `
  --workdir /workspace maven:3.9.11-eclipse-temurin-21 `
  mvn -q '-Dtest=ClinicalEvolutionApplicationServiceTest,ClinicalHistorySaveApplicationServiceTest,PatientCreationApplicationServiceTest,AuthenticationApplicationServiceTest,ActivePatientContextApplicationServiceTest,HexagonalArchitectureTest' test
```

Resultado: correcto. Se verifican datos vacíos, historia inexistente,
persistencia por puerto y límites de arquitectura. Una imagen completa con
PostgreSQL temporal también alcanzó estado saludable.

## Límite actual

La lectura de historia, la plantilla inicial y la representación de paciente
continúan en convivencia dentro de `PatientDocumentService`; no cambian rutas
ni aspecto clínico mientras se migran en cortes posteriores.
