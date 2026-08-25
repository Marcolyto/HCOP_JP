package ar.com.hexium.hcop.tools.domain;

/**
 * Proyección de una calculadora o de los ajustes institucionales de Herramientas — {@code
 * definition} es el mismo árbol opaco (mapas/listas/escalares) que {@code
 * configuration.domain.ConfigurationDefinition}, sin atarse a ese tipo para que el dominio de
 * {@code tools} no dependa de otro módulo.
 */
public record CalculatorSummary(
    String id, String key, String name, String description, long revision, Object definition) {
}
