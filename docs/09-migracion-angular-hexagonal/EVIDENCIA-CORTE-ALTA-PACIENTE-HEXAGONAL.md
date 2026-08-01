# Corte hexagonal: alta de pacientes

Fecha de validación: 2026-07-31

## Alcance

La regla de creación de pacientes se ejecuta ahora en
`PatientCreationApplicationService`. Conserva las validaciones de nombre,
apellido, identidad obligatoria, fecha de nacimiento y duplicados.

```text
PatientController -> PatientService (compatibilidad) -> PatientCreationUseCase
                                                        -> PatientCreationStorePort
                                                        -> JdbcPatientCreationStoreAdapter
```

## Compatibilidad

No cambia `POST /api/clinical/patients`, su respuesta, la creación de la hoja
clínica en blanco ni la activación del paciente creado. Esas dos últimas
operaciones siguen en el facade mientras se migra el documento clínico completo.

## Pruebas reproducibles

```powershell
docker run --rm --mount "type=bind,source=${PWD},target=/workspace" `
  --workdir /workspace maven:3.9.11-eclipse-temurin-21 `
  mvn -q '-Dtest=PatientCreationApplicationServiceTest,PatientServiceSearchTest,AuthenticationApplicationServiceTest,ActivePatientContextApplicationServiceTest,HexagonalArchitectureTest' test
```

Resultado: correcto. Las pruebas cubren alta válida, duplicado, identidad
incompleta, búsqueda existente y límites de arquitectura.

## Límite actual

La lectura, búsqueda, documento de historia y vistas de paciente permanecen en
convivencia. El próximo corte moverá el documento clínico y sus evoluciones sin
cambiar contratos ni el formato de la hoja actual.
