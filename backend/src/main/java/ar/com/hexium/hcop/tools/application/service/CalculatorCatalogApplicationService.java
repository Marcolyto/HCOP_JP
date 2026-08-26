package ar.com.hexium.hcop.tools.application.service;

import ar.com.hexium.hcop.tools.application.port.in.CalculatorCatalogUseCase;
import ar.com.hexium.hcop.tools.application.port.out.CalculatorCatalogPort;

/**
 * Sin lógica propia — la proyección "solo activas, sin historial" ya la resuelve
 * {@link CalculatorCatalogPort}. El módulo existe igual como capa propia porque es el punto de
 * extensión si Herramientas alguna vez necesita reglas que no son de {@code configuration}
 * (ordenar, agrupar, filtrar por permiso adicional).
 */
public final class CalculatorCatalogApplicationService implements CalculatorCatalogUseCase {
  private final CalculatorCatalogPort catalog;

  public CalculatorCatalogApplicationService(CalculatorCatalogPort catalog) {
    this.catalog = catalog;
  }

  @Override
  public CalculatorCatalogView list() {
    return new CalculatorCatalogView(catalog.activeCalculators(), catalog.activeToolSettings());
  }
}
