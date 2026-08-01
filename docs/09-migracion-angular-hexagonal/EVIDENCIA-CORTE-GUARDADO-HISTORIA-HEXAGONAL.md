# Corte hexagonal: guardado de historia clínica

Fecha de validación: 2026-07-31

## Alcance

El guardado de `PUT /api/hc` usa ahora `ClinicalHistorySaveUseCase` y el
adaptador PostgreSQL `JdbcClinicalHistorySaveAdapter`. El núcleo recibe texto
del documento y sus identificadores ya extraídos; no depende de JSON, Spring ni JDBC.

```text
ClinicalDocumentController -> PatientDocumentService (compatibilidad)
                          -> ClinicalHistorySaveUseCase
                          -> ClinicalHistorySavePort
                          -> JdbcClinicalHistorySaveAdapter
```

## Invariantes conservadas

- La identidad incluida en la hoja debe coincidir con el paciente activo.
- La revisión esperada debe coincidir; una edición obsoleta devuelve conflicto.
- La respuesta de `PUT /api/hc` conserva su formato y la revisión resultante.
- La historia creada en blanco y su lectura actual no cambian.

## Pruebas reproducibles

```powershell
docker run --rm --mount "type=bind,source=${PWD},target=/workspace" `
  --workdir /workspace maven:3.9.11-eclipse-temurin-21 `
  mvn -q '-Dtest=ClinicalHistorySaveApplicationServiceTest,PatientCreationApplicationServiceTest,AuthenticationApplicationServiceTest,ActivePatientContextApplicationServiceTest,HexagonalArchitectureTest' test
```

Resultado: correcto. Además, una prueba temporal de PostgreSQL y API confirmó:
alta de paciente, guardado de la hoja, recuperación y rechazo HTTP `409` al
reintentar una revisión obsoleta. Los contenedores y sus datos se eliminaron.

## Límite actual

La lectura, plantilla, creación inicial y agregado inmutable de evoluciones
siguen en convivencia dentro de `PatientDocumentService`. El siguiente corte
moverá específicamente las evoluciones y sus bloqueos transaccionales.
