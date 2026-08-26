package ar.com.hexium.hcop.catalog.application.port.in;

import ar.com.hexium.hcop.catalog.domain.CatalogSearchResult;
import ar.com.hexium.hcop.catalog.domain.DiagnosisEquivalence;
import java.util.List;

public interface DiagnosisCatalogUseCase {

  DiagnosisSearchResult search(String system, String query, int limit);

  /** Usado por {@code config.ClinicalCatalogBootstrap} para sembrar las equivalencias iniciales. */
  List<DiagnosisEquivalence> equivalences();

  record DiagnosisSearchResult(String system, String query, List<CatalogSearchResult> items) {
  }
}
