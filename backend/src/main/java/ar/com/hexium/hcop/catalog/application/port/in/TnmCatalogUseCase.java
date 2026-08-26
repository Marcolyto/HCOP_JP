package ar.com.hexium.hcop.catalog.application.port.in;

import java.util.List;

public interface TnmCatalogUseCase {

  List<TnmSchemaSummary> list();

  TnmSchemaDetail detail(String id);

  record TnmSchemaSummary(String id, String name, String title, String version, int inputCount) {
  }

  record TnmSchemaView(
      String id, String name, String title, String version, Object notes, List<Object> inputs, Object outputs) {
  }

  record TnmSchemaDetail(TnmSchemaView schema, List<Object> stageTables) {
  }
}
