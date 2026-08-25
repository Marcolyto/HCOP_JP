package ar.com.hexium.hcop.tools.infrastructure.configuration;

import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase;
import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase.ConfigurationView;
import ar.com.hexium.hcop.tools.application.port.out.CalculatorCatalogPort;
import ar.com.hexium.hcop.tools.domain.CalculatorSummary;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ConfigurationCalculatorCatalogAdapter implements CalculatorCatalogPort {
  private final ConfigurationManagementUseCase configurations;

  public ConfigurationCalculatorCatalogAdapter(ConfigurationManagementUseCase configurations) {
    this.configurations = configurations;
  }

  @Override
  public List<CalculatorSummary> activeCalculators() {
    return configurations.list("calculator", false).stream()
        .filter(ConfigurationView::active)
        .map(this::summary)
        .toList();
  }

  @Override
  public Optional<CalculatorSummary> activeToolSettings() {
    return configurations.list("tool-settings", false).stream()
        .filter(ConfigurationView::active)
        .findFirst()
        .map(this::summary);
  }

  private CalculatorSummary summary(ConfigurationView item) {
    return new CalculatorSummary(
        item.id(), item.key(), item.name(), item.description(), item.revision(),
        item.definition().value());
  }
}
