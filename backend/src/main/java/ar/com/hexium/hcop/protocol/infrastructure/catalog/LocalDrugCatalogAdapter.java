package ar.com.hexium.hcop.protocol.infrastructure.catalog;

import ar.com.hexium.hcop.catalog.application.port.in.DrugCatalogUseCase;
import ar.com.hexium.hcop.protocol.application.port.out.DrugCatalogPort;
import ar.com.hexium.hcop.protocol.domain.ProtocolDocument;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LocalDrugCatalogAdapter implements DrugCatalogPort {
  private final DrugCatalogUseCase drugs;

  public LocalDrugCatalogAdapter(DrugCatalogUseCase drugs) {
    this.drugs = drugs;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<ProtocolDocument> search(String query) {
    return drugs.search(query).stream()
        .map(item -> ProtocolDocument.of((Map<String, Object>) item))
        .toList();
  }
}
