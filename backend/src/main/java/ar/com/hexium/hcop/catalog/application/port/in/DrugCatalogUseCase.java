package ar.com.hexium.hcop.catalog.application.port.in;

import java.util.List;

public interface DrugCatalogUseCase {

  int total();

  /** Cada elemento es un {@code Map<String,Object>} con los campos crudos del medicamento. */
  List<Object> search(String query);
}
