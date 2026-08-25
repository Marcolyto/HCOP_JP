package ar.com.hexium.hcop.tools.application.port.out;

import ar.com.hexium.hcop.tools.domain.CalculatorSummary;
import java.util.List;
import java.util.Optional;

/**
 * Hoy lo implementa {@code infrastructure.configuration.ConfigurationCalculatorCatalogAdapter}
 * sobre {@code configuration.application.port.in.ConfigurationManagementUseCase} — este puerto
 * es lo que evita que el resto de {@code tools} conozca esa dependencia (patrón #7 del plan F3).
 */
public interface CalculatorCatalogPort {

  List<CalculatorSummary> activeCalculators();

  Optional<CalculatorSummary> activeToolSettings();
}
