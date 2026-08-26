package ar.com.hexium.hcop.catalog.application.port.in;

import java.util.List;
import java.util.Set;

public interface LegacyProtocolCatalogUseCase {

  ProtocolSchemeCatalog list(String source);

  ProtocolSchemeDetail detail(String id, String source);

  CatalogStatus status(int tnmCount);

  /** Usado por {@code protocol.infrastructure.catalog.LegacyProtocolCatalogAdapter}. */
  List<Object> clinicalComponents(String schemeId);

  /** Usado por {@link DrugCatalogUseCase}. */
  List<Object> searchableDrugs();

  Set<String> protocolDrugNames();

  record ProtocolSchemeCatalog(String source, List<String> categories, List<Object> schemes) {
  }

  record ProtocolSchemeDetail(Object scheme, List<Object> drugs) {
  }

  record CatalogStatus(int protocols, int tnm, String tnmVersion) {
  }
}
