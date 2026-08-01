# Corte vertical: Configuración

Configuración es la primera capacidad trasladada completamente a la estructura
hexagonal. El corte conserva las rutas y el JSON de HCOP JP para que la
interfaz vigente y el futuro frontend Angular puedan convivir sin una
migración simultánea.

## Alcance

El caso de uso genérico administra estas clases de configuración:

- guías;
- plantillas anatómicas;
- equivalencias y visibilidad de diagnósticos;
- calculadoras;
- ajustes de herramientas;
- ajustes de Hospital de Día;
- formularios de investigación;
- protocolos.

Protocolos y Guías ya consumen el puerto de entrada de Configuración desde sus
propios casos de uso hexagonales. Plantillas anatómicas todavía conserva su
adaptador especializado y, durante esa transición, consume el caso de uso
nuevo a través de un puente de compatibilidad sin acceso directo a PostgreSQL.

## Flujo de dependencias

```text
HTTP / JSON
    |
    v
adaptador web
    |
    v
puerto de entrada
    |
    v
servicio de aplicación ----> puerto de salida
                                  |
                                  v
                         adaptador PostgreSQL
```

- `domain` contiene tipos Java inmutables y la definición estructurada.
- `application` valida comandos, versiones y reglas de archivo.
- `infrastructure/web` conserva el contrato HTTP y traduce JSON.
- `infrastructure/persistence` conserva tablas, consultas e historial.
- `infrastructure/configuration` delimita las transacciones Spring.

Jackson, Spring, JDBC y PostgreSQL no atraviesan hacia dominio o aplicación.
ArchUnit comprueba esta restricción en cada `mvn verify`.

## Concurrencia y errores

Toda actualización o archivo exige la revisión esperada. Si otro usuario
modificó el elemento, la operación devuelve `409 VERSION_CONFLICT` y no pisa
información. Las claves duplicadas devuelven `409 CONFIGURATION_KEY_CONFLICT`;
un tipo desconocido devuelve `404 CONFIGURATION_KIND_NOT_FOUND`.

El historial conserva una versión por revisión. Archivar no borra el registro:
incrementa la revisión, registra una nueva versión y permite listarlo mediante
`includeInactive`.

## Compatibilidad comprobada

La prueba `scripts/configuration-contract-test.ps1` ejecuta el contrato contra
la aplicación empaquetada y PostgreSQL reales:

1. lee los ajustes predeterminados;
2. crea una calculadora con definición dinámica;
3. actualiza parcialmente sin perder campos omitidos;
4. rechaza una revisión desactualizada;
5. consulta el historial;
6. archiva y verifica listados activos e inactivos;
7. rechaza una clase desconocida.

Además, la regresión comprueba:

- 111 operaciones OpenAPI con identificadores únicos;
- todos los documentos Markdown y sus enlaces;
- catálogo de protocolos y plantillas;
- recorrido clínico completo desde paciente y tratamiento hasta administración;
- reglas automáticas de arquitectura.

## Condición de salida

Este corte se considera backend hexagonal validado para la configuración
genérica. La capacidad completa sigue `En convivencia` hasta migrar Plantillas
anatómicas, construir su interfaz Angular y completar la comparación visual y
de permisos.
