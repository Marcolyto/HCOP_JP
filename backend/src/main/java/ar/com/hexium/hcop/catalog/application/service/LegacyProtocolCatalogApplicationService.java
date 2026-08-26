package ar.com.hexium.hcop.catalog.application.service;

import ar.com.hexium.hcop.catalog.application.port.in.LegacyProtocolCatalogUseCase;
import ar.com.hexium.hcop.catalog.application.port.out.LegacyProtocolCatalogStore;
import java.util.List;
import java.util.Set;

/** Sin lógica propia — la lectura/fusión del catálogo legacy la resuelve {@link LegacyProtocolCatalogStore}. */
public final class LegacyProtocolCatalogApplicationService implements LegacyProtocolCatalogUseCase {
  private final LegacyProtocolCatalogStore store;

  public LegacyProtocolCatalogApplicationService(LegacyProtocolCatalogStore store) {
    this.store = store;
  }

  @Override
  public ProtocolSchemeCatalog list(String source) {
    return store.list(source);
  }

  @Override
  public ProtocolSchemeDetail detail(String id, String source) {
    return store.detail(id, source);
  }

  @Override
  public CatalogStatus status(int tnmCount) {
    return store.status(tnmCount);
  }

  @Override
  public List<Object> clinicalComponents(String schemeId) {
    return store.clinicalComponents(schemeId);
  }

  @Override
  public List<Object> searchableDrugs() {
    return store.searchableDrugs();
  }

  @Override
  public Set<String> protocolDrugNames() {
    return store.protocolDrugNames();
  }
}
