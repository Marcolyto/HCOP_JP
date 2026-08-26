package ar.com.hexium.hcop.catalog.application.port.in;

import ar.com.hexium.hcop.catalog.domain.CatalogSearchResult;
import java.util.List;
import java.util.Map;

public interface AjccStagingUseCase {

  List<AjccSiteSummary> list();

  AjccSiteView detail(String id);

  AjccStagingOutcome stage(String id, Map<String, String> values);

  List<CatalogSearchResult> search(String query, int limit);

  record AjccSiteSummary(String id, String name, String group) {
  }

  record AjccSiteView(
      String id, String name, String edition, String source, String guideVersion, Object axes) {
  }

  /** Cuando {@code matched} es false, {@code stage} queda vacío y {@code sourceRow} en null. */
  record AjccStagingOutcome(boolean matched, String stage, Integer sourceRow, List<String> missing) {
  }
}
