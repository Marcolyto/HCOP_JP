package ar.com.hexium.hcop.catalog.application.port.out;

import ar.com.hexium.hcop.catalog.domain.AjccSite;
import java.util.List;

public interface AjccCatalogStore {

  List<AjccSite> sites();
}
