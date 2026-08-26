package ar.com.hexium.hcop.protocol.application.port.out;

import ar.com.hexium.hcop.protocol.domain.ProtocolDocument;
import java.util.List;

public interface DrugCatalogPort {
  List<ProtocolDocument> search(String query);
}
