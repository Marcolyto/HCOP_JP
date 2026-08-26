package ar.com.hexium.hcop.catalog.application.port.out;

import java.util.List;
import java.util.Optional;

public interface SystemicFormCatalogStore {

  /** Vacío si el catálogo cargado no es un arreglo JSON válido. */
  Optional<List<Object>> forms();

  Object find(String id);
}
