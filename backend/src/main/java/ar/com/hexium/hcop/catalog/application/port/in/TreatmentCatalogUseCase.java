package ar.com.hexium.hcop.catalog.application.port.in;

import ar.com.hexium.hcop.catalog.domain.TreatmentScheme;
import java.util.List;
import java.util.Optional;

public interface TreatmentCatalogUseCase {

  List<TreatmentScheme> schemes(String query);

  Optional<TreatmentScheme> scheme(String id);

  List<TreatmentScheme> allSchemes();

  void invalidate();
}
