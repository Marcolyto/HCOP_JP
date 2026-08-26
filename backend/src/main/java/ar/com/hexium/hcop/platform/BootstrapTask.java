package ar.com.hexium.hcop.platform;

/**
 * Contrato de arranque para que un módulo hexagonal registre trabajo de seed/bootstrap sin que
 * {@code platform} (infraestructura transversal, nunca hexagonal) dependa de sus clases concretas
 * — evita el ciclo {@code platform}↔{@code catalog} / {@code platform}↔{@code patient} (F3.4, ver
 * DECISIONES-F3.md). Cada implementación es un bean normal de Spring; {@link BootstrapConfiguration}
 * las recolecta como {@code List<BootstrapTask>} — Spring las ordena por
 * {@link org.springframework.core.annotation.Order}.
 */
public interface BootstrapTask {
  void run();
}
