package ar.com.hexium.hcop.catalog.application.port.out;

import java.util.List;

public interface DrugCatalogStore {

  /** Catálogo fusionado y deduplicado (protocolos COIR + vademecum local), cargado una vez. */
  List<Object> drugs();
}
