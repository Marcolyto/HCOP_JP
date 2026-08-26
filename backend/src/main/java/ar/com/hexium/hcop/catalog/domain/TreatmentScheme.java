package ar.com.hexium.hcop.catalog.domain;

/**
 * {@code definition} es el árbol opaco del esquema (en runtime, un {@code JsonNode} — así lo
 * siguen consumiendo {@code treatment}/{@code infusion}/{@code protocol}, módulos aún no
 * hexagonales; ver PROGRESO.md, desvío consciente de F3.2).
 */
public record TreatmentScheme(
    String id, String name, int cycleDays, Integer durationMinutes, Object definition, boolean custom) {
}
