package ar.com.hexium.hcop.tools.application.port.in;

import ar.com.hexium.hcop.tools.domain.CalculatorSummary;
import java.util.List;
import java.util.Optional;

public interface CalculatorCatalogUseCase {

  CalculatorCatalogView list();

  record CalculatorCatalogView(List<CalculatorSummary> calculators, Optional<CalculatorSummary> settings) {
  }
}
