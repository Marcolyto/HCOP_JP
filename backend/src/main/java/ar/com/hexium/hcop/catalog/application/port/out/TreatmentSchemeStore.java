package ar.com.hexium.hcop.catalog.application.port.out;

import ar.com.hexium.hcop.catalog.domain.TreatmentScheme;
import java.util.List;

public interface TreatmentSchemeStore {

  /** Catálogo de filesystem + configuración clínica en Postgres, ya fusionado y cacheado. */
  List<TreatmentScheme> load();

  void invalidate();
}
