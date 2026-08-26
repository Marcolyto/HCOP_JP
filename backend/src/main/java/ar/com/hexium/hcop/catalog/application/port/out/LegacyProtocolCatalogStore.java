package ar.com.hexium.hcop.catalog.application.port.out;

import ar.com.hexium.hcop.catalog.application.port.in.LegacyProtocolCatalogUseCase.CatalogStatus;
import ar.com.hexium.hcop.catalog.application.port.in.LegacyProtocolCatalogUseCase.ProtocolSchemeCatalog;
import ar.com.hexium.hcop.catalog.application.port.in.LegacyProtocolCatalogUseCase.ProtocolSchemeDetail;
import java.util.List;
import java.util.Set;

public interface LegacyProtocolCatalogStore {

  ProtocolSchemeCatalog list(String source);

  ProtocolSchemeDetail detail(String id, String source);

  CatalogStatus status(int tnmCount);

  List<Object> clinicalComponents(String schemeId);

  List<Object> searchableDrugs();

  Set<String> protocolDrugNames();
}
