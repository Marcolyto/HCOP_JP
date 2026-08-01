# Validacion de la migracion - 31/07/2026

## Resultado

La verificacion se ejecutó sin alterar el arbol de trabajo: el codigo se monto
en modo solo lectura dentro de un contenedor temporal y los artefactos de Maven
se guardaron fuera del repositorio.

| Control | Resultado |
| --- | --- |
| Compilacion Angular del editor de protocolos y selector de drogas | Correcta |
| Empaquetado Docker completo Java + Angular | Correcto |
| Pruebas Java | 142 correctas, 0 fallas, 0 errores, 0 omitidas |
| Reglas de arquitectura hexagonal (ArchUnit) | Correctas |
| Sesion, catalogo, busqueda de drogas, alta y actualizacion de protocolo sobre PostgreSQL aislado | Correctas |

## Comandos reproducibles

```powershell
docker build --target frontend-build --file Dockerfile --tag hcop-jp:protocol-editor-drugs-check .
docker build --file Dockerfile --tag hcop-jp:angular-protocol-editor-package-check .
docker run --rm --mount type=bind,source="$PWD",target=/workspace,readonly `
  --mount type=volume,source=hcop-ajp-maven-cache,target=/root/.m2 `
  --workdir /workspace maven:3.9.11-eclipse-temurin-21 `
  mvn -B '-Dhcop.build.directory=/tmp/hcop-ajp-tests' test
```

La advertencia de Mockito sobre carga dinámica de agente proviene de la version
actual de su infraestructura de pruebas; no produjo fallas ni modifica el
comportamiento de la aplicacion. Se mantiene como mejora de mantenimiento, no
como bloqueo del corte.
