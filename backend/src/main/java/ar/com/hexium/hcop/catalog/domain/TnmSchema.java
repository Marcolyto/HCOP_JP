package ar.com.hexium.hcop.catalog.domain;

import java.util.List;

/** {@code notes}/{@code stagingInputs}/{@code outputs} son árboles opacos del esquema TNM. */
public record TnmSchema(
    String id,
    String name,
    String title,
    String version,
    Object notes,
    List<Object> stagingInputs,
    Object outputs,
    List<String> involvedTables) {
}
