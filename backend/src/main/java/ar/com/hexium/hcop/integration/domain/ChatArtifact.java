package ar.com.hexium.hcop.integration.domain;

/** Tabla o gráfico validado, listo para renderizar — nunca datos crudos del LLM. */
public sealed interface ChatArtifact permits TableArtifact, ChartArtifact {
}
