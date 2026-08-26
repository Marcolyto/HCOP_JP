package ar.com.hexium.hcop.tools.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.tools.application.port.out.CalculatorCatalogPort;
import ar.com.hexium.hcop.tools.domain.CalculatorSummary;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CalculatorCatalogApplicationServiceTest {

  @Test
  void armaLaVistaConLoQueDevuelveElPuerto() {
    CalculatorCatalogPort catalog = mock(CalculatorCatalogPort.class);
    CalculatorSummary calculator = new CalculatorSummary("1", "bsa", "SC", "", 1, Map.of());
    CalculatorSummary settings = new CalculatorSummary("", "default", "Herramientas", "", 0, Map.of());
    when(catalog.activeCalculators()).thenReturn(List.of(calculator));
    when(catalog.activeToolSettings()).thenReturn(Optional.of(settings));
    CalculatorCatalogApplicationService service = new CalculatorCatalogApplicationService(catalog);

    var view = service.list();

    assertThat(view.calculators()).containsExactly(calculator);
    assertThat(view.settings()).contains(settings);
  }
}
