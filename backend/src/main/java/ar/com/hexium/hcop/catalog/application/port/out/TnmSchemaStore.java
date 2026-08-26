package ar.com.hexium.hcop.catalog.application.port.out;

import ar.com.hexium.hcop.catalog.domain.TnmSchema;
import java.util.List;
import java.util.Optional;

public interface TnmSchemaStore {

  List<TnmSchema> schemas();

  /** Tabla auxiliar de estadificación ya proyectada (id/name/title/notes/definition/rows). */
  Optional<Object> stageTable(String tableId);
}
