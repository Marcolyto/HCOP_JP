package ar.com.hexium.hcop.catalog.application.service;

import ar.com.hexium.hcop.catalog.application.port.in.TnmCatalogUseCase;
import ar.com.hexium.hcop.catalog.application.port.out.TnmSchemaStore;
import ar.com.hexium.hcop.catalog.domain.TnmSchema;
import java.util.ArrayList;
import java.util.List;

public final class TnmCatalogApplicationService implements TnmCatalogUseCase {
  private final TnmSchemaStore store;

  public TnmCatalogApplicationService(TnmSchemaStore store) {
    this.store = store;
  }

  @Override
  public List<TnmSchemaSummary> list() {
    return store.schemas().stream()
        .map(schema -> new TnmSchemaSummary(
            schema.id(), schema.name(), schema.title(), schema.version(), schema.stagingInputs().size()))
        .toList();
  }

  @Override
  public TnmSchemaDetail detail(String id) {
    TnmSchema schema = store.schemas().stream().filter(item -> item.id().equals(id)).findFirst()
        .orElseThrow(() -> new CatalogFailure(CatalogFailure.Type.NOT_FOUND, "Sitio TNM no encontrado."));
    List<Object> stageTables = new ArrayList<>();
    for (String tableId : schema.involvedTables()) {
      if (!tableId.toLowerCase().contains("stage_group")) continue;
      store.stageTable(tableId).ifPresent(stageTables::add);
    }
    TnmSchemaView view = new TnmSchemaView(
        schema.id(), schema.name(), schema.title(), schema.version(), schema.notes(),
        schema.stagingInputs(), schema.outputs());
    return new TnmSchemaDetail(view, List.copyOf(stageTables));
  }
}
