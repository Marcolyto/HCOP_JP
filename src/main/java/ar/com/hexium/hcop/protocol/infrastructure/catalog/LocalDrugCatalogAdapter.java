package ar.com.hexium.hcop.protocol.infrastructure.catalog;

import ar.com.hexium.hcop.catalog.DrugCatalogService;
import ar.com.hexium.hcop.protocol.application.port.out.DrugCatalogPort;
import ar.com.hexium.hcop.protocol.domain.ProtocolDocument;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LocalDrugCatalogAdapter implements DrugCatalogPort {
  private final DrugCatalogService drugs;

  public LocalDrugCatalogAdapter(DrugCatalogService drugs) {
    this.drugs = drugs;
  }

  @Override
  public List<ProtocolDocument> search(String query) {
    return drugs.search(query).stream().map(ProtocolDocument::of).toList();
  }
}
