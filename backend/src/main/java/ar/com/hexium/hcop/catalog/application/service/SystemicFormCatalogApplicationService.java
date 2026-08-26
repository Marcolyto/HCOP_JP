package ar.com.hexium.hcop.catalog.application.service;

import ar.com.hexium.hcop.catalog.application.port.in.SystemicFormCatalogUseCase;
import ar.com.hexium.hcop.catalog.application.port.out.SystemicFormCatalogStore;
import java.util.List;

public final class SystemicFormCatalogApplicationService implements SystemicFormCatalogUseCase {
  private final SystemicFormCatalogStore store;

  public SystemicFormCatalogApplicationService(SystemicFormCatalogStore store) {
    this.store = store;
  }

  @Override
  public List<Object> forms() {
    return store.forms().orElseThrow(() -> new CatalogFailure(CatalogFailure.Type.INVALID, "Catálogo inválido."));
  }

  @Override
  public Object find(String id) {
    return store.find(id);
  }
}
